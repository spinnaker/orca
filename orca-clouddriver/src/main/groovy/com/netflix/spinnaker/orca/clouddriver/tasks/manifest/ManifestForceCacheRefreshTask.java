/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheStatusService;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.Data;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING;
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED;
import static java.net.HttpURLConnection.HTTP_OK;

@Component
@Slf4j
public class ManifestForceCacheRefreshTask extends AbstractCloudProviderAwareTask implements Task, ManifestAware, RetryableTask {
  private final static String REFRESH_TYPE = "manifest";
  public final static String TASK_NAME = "forceCacheRefresh";

  @Getter
  private final long backoffPeriod = TimeUnit.SECONDS.toMillis(10);
  @Getter
  private final long timeout = TimeUnit.MINUTES.toMillis(15);

  private final long autoSucceedAfterMs = TimeUnit.MINUTES.toMillis(12);
  private final Clock clock;
  private final Registry registry;
  private final CloudDriverCacheService cacheService;
  private final CloudDriverCacheStatusService cacheStatusService;
  private final ObjectMapper objectMapper;
  private final Id durationTimerId;

  @Autowired
  public ManifestForceCacheRefreshTask(Registry registry,
                                       CloudDriverCacheService cacheService,
                                       CloudDriverCacheStatusService cacheStatusService,
                                       ObjectMapper objectMapper) {
    this(registry, cacheService, cacheStatusService, objectMapper, Clock.systemUTC());
  }

  ManifestForceCacheRefreshTask(Registry registry,
                                CloudDriverCacheService cacheService,
                                CloudDriverCacheStatusService cacheStatusService,
                                ObjectMapper objectMapper, Clock clock) {
    this.registry = registry;
    this.cacheService = cacheService;
    this.cacheStatusService = cacheStatusService;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.durationTimerId = registry.createId("manifestForceCacheRefreshTask.duration");
  }

  @Override
  @Nonnull
  public TaskResult execute(@Nonnull Stage stage) {
    Long startTime = stage.getStartTime();
    if (startTime == null) {
      throw new IllegalStateException("Stage has no start time, cannot be executing.");
    }
    long duration = clock.millis() - startTime;
    if (duration > autoSucceedAfterMs) {
      log.info("{}: Force cache refresh never finished processing... assuming the cache is in sync and continuing...", stage.getExecution().getId());
      registry.timer(durationTimerId.withTags("success", "true", "outcome", "autoSucceed"))
        .record(duration, TimeUnit.MILLISECONDS);
      return new TaskResult(SUCCEEDED);
    }

    String cloudProvider = getCloudProvider(stage);
    String account = getCredentials(stage);
    StageData stageData = fromStage(stage);
    stageData.deployedManifests = getDeployedManifests(stage);

    if (refreshManifests(cloudProvider, account, stageData)) {
      registry.timer(durationTimerId.withTags("success", "true", "outcome", "complete"))
        .record(duration, TimeUnit.MILLISECONDS);
      return new TaskResult(SUCCEEDED, toContext(stageData));
    } else {
      TaskResult taskResult = checkPendingRefreshes(cloudProvider, account, stageData, startTime);

      // ignoring any non-success, non-failure statuses
      if (taskResult.getStatus().isSuccessful()) {
        registry.timer(durationTimerId.withTags("success", "true", "outcome", "complete"))
          .record(duration, TimeUnit.MILLISECONDS);
      } else if (taskResult.getStatus().isFailure()) {
        registry.timer(durationTimerId.withTags("success", "false", "outcome", "failure"))
          .record(duration, TimeUnit.MILLISECONDS);
      }
      return taskResult;
    }
  }

  private TaskResult checkPendingRefreshes(String provider, String account, StageData stageData, long startTime) {
    Collection<PendingRefresh> pendingRefreshes = objectMapper.convertValue(
        cacheStatusService.pendingForceCacheUpdates(provider, REFRESH_TYPE),
        new TypeReference<Collection<PendingRefresh>>() { }
    );

    List<ScopedManifest> deployedManifests = stageData.getDeployedManifests();
    Set<String> refreshedManifests = stageData.getRefreshedManifests();
    Set<String> processedManifests = stageData.getProcessedManifests();
    boolean allProcessed = true;

    for (ScopedManifest manifest : deployedManifests) {
      String location = manifest.location;
      String name = manifest.name;

      String id = toManifestIdentifier(location, name);
      if (processedManifests.contains(id)) {
        continue;
      }

      Optional<PendingRefresh> pendingRefresh = pendingRefreshes.stream()
        .filter(pr -> pr.getDetails() != null)
        .filter(pr -> account.equals(pr.getDetails().getAccount()) &&
          (location.equals(pr.getDetails().getLocation()) || StringUtils.isNotEmpty(location) && StringUtils.isEmpty(pr.getDetails().getLocation())) &&
          name.equals(pr.getDetails().getName())
        )
        .findAny();

      if (pendingRefresh.isPresent()) {
        PendingRefresh refresh = pendingRefresh.get();
        // it's possible the resource isn't supposed to have a namespace -- clouddriver reports this by removing it
        // in the response. in this case, we make sure to set it to match between clouddriver and orca
        if (StringUtils.isEmpty(refresh.getDetails().getLocation())) {
          refresh.getDetails().setLocation(location);
        }
        if (pendingRefreshProcessed(refresh, refreshedManifests, startTime)) {
          log.debug("Pending manifest refresh of {} in {} completed", id, account);
          processedManifests.add(id);
        } else {
          log.debug("Pending manifest refresh of {} in {} still pending", id, account);
          allProcessed = false;
        }
      } else {
        log.warn("No pending refresh of {} in {}", id, account);
        allProcessed = false;
        refreshedManifests.remove(id);
      }
    }

    return new TaskResult(allProcessed ? SUCCEEDED : RUNNING, toContext(stageData));
  }

  private boolean pendingRefreshProcessed(PendingRefresh pendingRefresh, Set<String> refreshedManifests, long startTime) {
    PendingRefresh.Details details = pendingRefresh.getDetails();
    if (pendingRefresh.cacheTime == null || pendingRefresh.processedTime == null || details == null) {
      log.warn("Pending refresh of {} is missing cache metadata", pendingRefresh);
      refreshedManifests.remove(toManifestIdentifier(details.getLocation(), details.getName()));
      return false;
    } else if (pendingRefresh.cacheTime < startTime) {
      log.warn("Pending refresh of {} is stale", pendingRefresh);
      refreshedManifests.remove(toManifestIdentifier(details.getLocation(), details.getName()));
      return false;
    } else if (pendingRefresh.processedTime < startTime) {
      log.info("Pending refresh of {} was cached as a part of this request, but not processed", pendingRefresh);
      return false;
    } else {
      return true;
    }
  }

  private List<ScopedManifest> manifestsNeedingRefresh(StageData stageData) {
    List<ScopedManifest> deployedManifests = stageData.getDeployedManifests();
    Set<String> refreshedManifests = stageData.getRefreshedManifests();
    if (deployedManifests.isEmpty()) {
      log.warn("No manifests were deployed, nothing to refresh...");
    }

    return deployedManifests.stream()
      .filter(m -> !refreshedManifests.contains(toManifestIdentifier(m.location, m.name)))
      .collect(Collectors.toList());
  }

  private List<ScopedManifest> getDeployedManifests(Stage stage) {
    Map<String, List<String>> deployedManifests = manifestNamesByNamespace(stage);
    return deployedManifests.entrySet().stream()
      .flatMap(e -> e.getValue().stream().map(v -> new ScopedManifest(e.getKey(), v)))
      .collect(Collectors.toList());
  }

  private boolean refreshManifests(String provider, String account, StageData stageData) {
    List<ScopedManifest> manifests = manifestsNeedingRefresh(stageData);

    boolean allRefreshesSucceeded = true;
    for (ScopedManifest manifest : manifests) {
      String id = toManifestIdentifier(manifest.location, manifest.name);
      Map<String, String> request = new ImmutableMap.Builder<String, String>()
        .put("account", account)
        .put("name", manifest.name)
        .put("location", manifest.location)
        .build();

      try {
        Response response = cacheService.forceCacheUpdate(provider, REFRESH_TYPE, request);
        if (response.getStatus() == HTTP_OK) {
          log.info("Refresh of {} in {} succeeded immediately", id, account);
          stageData.getProcessedManifests().add(id);
        } else {
          allRefreshesSucceeded = false;
        }

        stageData.getRefreshedManifests().add(id);
      } catch (Exception e) {
        log.warn("Failed to refresh {}: ", id, e);
        allRefreshesSucceeded = false;
        stageData.errors.add(e.getMessage());
      }
    }

    boolean allRefreshesProcessed = stageData.getRefreshedManifests().equals(stageData.getProcessedManifests());

    // This can happen when the prior execution of this task returned RUNNING because one or more manifests
    // were not processed. In this case, all manifests may have been refreshed successfully without finishing processing.
    if (allRefreshesSucceeded && !allRefreshesProcessed) {
      log.warn("All refreshes succeeded, but not all have been processed yet...");
    }

    return allRefreshesSucceeded && allRefreshesProcessed;
  }

  private String toManifestIdentifier(String namespace, String name) {
    return namespace + ":" + name;
  }

  private StageData fromStage(Stage stage) {
    try {
      return objectMapper.readValue(objectMapper.writeValueAsString(stage.getContext()), StageData.class);
    } catch (IOException e) {
      throw new IllegalStateException("Malformed stage context in " + stage + ": " + e.getMessage(), e);
    }
  }

  private Map<String, Object> toContext(StageData stageData) {
    return objectMapper.convertValue(stageData, new TypeReference<Map<String, Object>>() { });
  }

  @Data
  static private class PendingRefresh {
    Details details;
    Long processedTime;
    Long cacheTime;
    Long processedCount;

    @Data
    static private class Details {
      String account;
      String location;
      String name;
    }
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static private class StageData {
    List<ScopedManifest> deployedManifests = Collections.emptyList();

    @JsonProperty("refreshed.manifests")
    Set<String> refreshedManifests = new HashSet<>();

    @JsonProperty("processed.manifests")
    Set<String> processedManifests = new HashSet<>();

    Set<String> errors = new HashSet<>();
  }

  @Value
  private static class ScopedManifest {
    final String location;
    final String name;

    ScopedManifest(
      @JsonProperty("location") String location,
      @JsonProperty("name") String name
    ) {
      this.location = location;
      this.name = name;
    }
  }
}
