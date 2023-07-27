/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda;

import static java.net.HttpURLConnection.HTTP_ACCEPTED;
import static java.net.HttpURLConnection.HTTP_OK;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheStatusService;
import com.netflix.spinnaker.orca.clouddriver.config.LambdaConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaCacheRefreshInput;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

@Component
@Slf4j
public class LambdaCacheRefreshTask
    implements LambdaStageBaseTask, OverridableTimeoutRetryableTask {

  private static final String PENDING_ID_KEY = "pendingId";

  private final ObjectMapper objectMapper;
  private final LambdaConfigurationProperties lambdaConfigurationProperties;
  private final CloudDriverCacheService cacheService;
  private final CloudDriverCacheStatusService cacheStatusService;

  public LambdaCacheRefreshTask(
      ObjectMapper objectMapper,
      LambdaConfigurationProperties lambdaConfigurationProperties,
      CloudDriverCacheService cacheService,
      CloudDriverCacheStatusService cacheStatusService) {
    this.objectMapper = objectMapper;
    this.lambdaConfigurationProperties = lambdaConfigurationProperties;
    this.cacheService = cacheService;
    this.cacheStatusService = cacheStatusService;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    prepareTask(stage);

    if (getTaskContext(stage).get(PENDING_ID_KEY) == null) {
      // trigger cache refresh
      return forceCacheRefresh(stage);
    } else {
      // check status of pending cache refresh
      return checkCacheRefreshStatus(stage);
    }
  }

  @Data
  public static class OnDemandResponse {
    Map<String, List<String>> cachedIdentifiersByType;
  }

  private TaskResult forceCacheRefresh(StageExecution stage) {
    LambdaCacheRefreshInput input = stage.mapTo(LambdaCacheRefreshInput.class);
    input.setAppName(stage.getExecution().getApplication());
    input.setCredentials(input.getAccount());

    Map<String, Object> request = toMap(input);
    Response response = cacheService.forceCacheUpdate("aws", "function", request);

    if (response.getStatus() == HTTP_OK) {
      // 200 == usually a Delete operation... aka an eviction.
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(stage.getContext()).build();
    } else if (response.getStatus() == HTTP_ACCEPTED) {
      // Async processing... meaning we have to poll to see when the refresh finishes
      OnDemandResponse onDemandRefreshResponse =
          objectMapper.convertValue(response.getBody(), OnDemandResponse.class);
      if (onDemandRefreshResponse.getCachedIdentifiersByType().get("onDemand").isEmpty()) {
        throw new NotFoundException(
            "Force cache refresh did not return the ID of the cache to poll. We failed or cache refresh isn't working as expected");
      }

      String id = onDemandRefreshResponse.getCachedIdentifiersByType().get("onDemand").get(0);
      addToTaskContext(stage, PENDING_ID_KEY, id);
    } else {
      log.warn(
          "Failed to generate a force cache refresh with response code of {}",
          response.getStatus());
    }

    return TaskResult.builder(ExecutionStatus.RUNNING).context(stage.getContext()).build();
  }

  /*
     IF processedCount > 0 && processedTime > stageStart
         SUCCESS
     ELSE
         TRY AGAIN
  */
  private TaskResult checkCacheRefreshStatus(StageExecution stage) {
    Long startTime = stage.getStartTime();
    if (startTime == null) {
      throw new IllegalStateException("Stage has no start time, cannot be executed.");
    }

    String id = (String) getTaskContext(stage).get(PENDING_ID_KEY);

    Collection<Map<String, Object>> onDemands =
        objectMapper.convertValue(
            cacheStatusService.pendingForceCacheUpdatesById("aws", "function", id),
            new TypeReference<>() {});

    for (Map<String, Object> results : onDemands) {
      if ((int) results.getOrDefault("processedCount", 0) > 0
          && (long) results.getOrDefault("cacheTime", 0) > startTime) {
        log.info("Caching should be completed for " + id);
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(stage.getContext()).build();
      } else {
        return TaskResult.builder(ExecutionStatus.RUNNING).context(stage.getContext()).build();
      }
    }

    String error = String.format("No on demand cache refresh found for ID %s", id);
    addErrorMessage(stage, error);
    return TaskResult.builder(ExecutionStatus.TERMINAL)
        .context(stage.getContext())
        .outputs(stage.getOutputs())
        .build();
  }

  private Map<String, Object> toMap(LambdaCacheRefreshInput input) {
    return objectMapper.convertValue(input, new TypeReference<>() {});
  }

  @Override
  public long getBackoffPeriod() {
    return lambdaConfigurationProperties.getCacheOnDemandRetryWaitTime();
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(15);
  }
}
