/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.annotations.VisibleForTesting;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.Manifest;
import com.netflix.spinnaker.orca.clouddriver.model.Task;
import com.netflix.spinnaker.orca.clouddriver.model.TaskOwner;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.SystemNotification;
import groovy.util.logging.Slf4j;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

@Slf4j
@Component
public class MonitorDeployManifestTask extends MonitorKatoTask {
  public static final String TASK_NAME = "monitorDeploy";
  private final OortService oortService;
  private static final Logger log = LoggerFactory.getLogger(MonitorDeployManifestTask.class);

  public MonitorDeployManifestTask(
      KatoService katoService,
      OortService oortService,
      Registry registry,
      DynamicConfigService dynamicConfigService,
      RetrySupport retrySupport) {
    super(katoService, registry, dynamicConfigService, retrySupport);
    this.oortService = oortService;
  }

  long maximumPeriodOfInactivity() {
    return getDynamicConfigService()
        .getConfig(
            Long.class,
            "tasks.monitor-kato-task.kubernetes.deploy-manifest.maximum-period-inactivity-ms",
            300000L);
  }

  long maximumForcedRetries() {
    return getDynamicConfigService()
        .getConfig(
            Integer.class,
            "tasks.monitor-kato-task.kubernetes.deploy-manifest.maximum-forced-retries",
            3);
  }

  public void handleClouddriverRetries(
      StageExecution stage, Task katoTask, Map<String, Object> outputs) {
    if (shouldRetryKubernetesTask(stage, katoTask)) {
      try {
        retryKubernetesTask(stage, katoTask, outputs);
      } catch (Exception e) {
        log.debug("exception occurred when attempting to retry task with id: " + katoTask.getId());
      }
    }
  }

  private boolean shouldRetryKubernetesTask(StageExecution stage, Task katoTask) {
    String cloudProvider = getCloudProvider(stage);
    if (cloudProvider == null
        || !cloudProvider.equals("kubernetes")
        || !getDynamicConfigService()
            .isEnabled("tasks.monitor-kato-task.kubernetes.deploy-manifest.retry-task", false)) {
      log.debug("task: {} did not meet the conditions required for a retry", katoTask.getId());
      return false;
    }

    Boolean hasPreviousRetryFailed =
        (Boolean) stage.getContext().getOrDefault("kato.task.forceRetryFatalError", false);
    if (hasPreviousRetryFailed) {
      log.debug(
          "previous forced retry attempts failed with a terminal error. Will not attempt to retry any further");
      return false;
    }

    Integer retryCount = (Integer) stage.getContext().getOrDefault("kato.task.forcedRetries", 0);
    if (retryCount > maximumForcedRetries()) {
      log.debug(
          "Number of forced retry attempts has exceeded maximum number allowed: {}. Will not "
              + "attempt to retry any further",
          maximumForcedRetries());
      return false;
    }

    Duration elapsedTime =
        getCurrentTaskExecutionTime(stage, katoTask, MonitorDeployManifestTask.TASK_NAME);
    if (elapsedTime == null) {
      log.debug(
          "error occurred in calculating how long the current task: {} for task id: {} has been running - "
              + "will not attempt to retry",
          MonitorDeployManifestTask.TASK_NAME,
          katoTask.getId());
      return false;
    }

    if (Duration.ofMillis(maximumPeriodOfInactivity()).compareTo(elapsedTime) < 0) {
      log.debug(
          "task: {} is eligible for retries as its status has not been updated for some time",
          katoTask.getId());
      return true;
    }
    log.debug(
        "the Running task: {} has not yet crossed the configured period of inactivity threshold - not attempting "
            + "to retry",
        katoTask.getId());
    return false;
  }

  private void retryKubernetesTask(
      StageExecution stage, Task katoTask, Map<String, Object> outputs) {
    Integer retryAttempts =
        (Integer) stage.getContext().getOrDefault("kato.task.forcedRetryAttempts", 0) + 1;
    Integer forcedRetries = (Integer) stage.getContext().getOrDefault("kato.task.forcedRetries", 0);
    log.debug(
        "retrying kubernetes cloudprovider task with ID: {}. Attempt: {}",
        katoTask.getId(),
        retryAttempts);
    outputs.put("kato.task.forcedRetryAttempts", retryAttempts);
    TaskOwner clouddriverPodName;
    try {
      clouddriverPodName = getKatoService().lookupTaskOwner("kubernetes", katoTask.getId());
    } catch (RetrofitError re) {
      log.debug(
          "failed to retrieve clouddriver owner information from task ID: {}. Retry failed. Error: ",
          katoTask.getId(),
          re);
      outputs.put("kato.task.forceRetryFatalError", true);
      stage.getContext().put("kato.task.forceRetryFatalError", true);
      return;
    }

    if (clouddriverPodName == null || clouddriverPodName.getName() == null) {
      log.debug(
          "failed to retrieve clouddriver owner information for task ID: {}. Retry failed",
          katoTask.getId());
      outputs.put("kato.task.forceRetryFatalError", true);
      stage.getContext().put("kato.task.forceRetryFatalError", true);
      return;
    }

    // manifest will be returned only if it actually exists - which means the owner clouddriver pod
    // is alive and
    // functional. We don't need to force retry in this case.
    String clouddriverAccount =
        getDynamicConfigService()
            .getConfig(
                String.class, "tasks.monitor-kato-task.kubernetes.deploy-manifest.account", "");
    if (clouddriverAccount.isBlank()) {
      log.debug(
          "tasks.monitor-kato-task.kubernetes.deploy-manifest.account is a required property. Retry failed for task: {}",
          katoTask.getId());
      outputs.put("kato.task.forceRetryFatalError", true);
      stage.getContext().put("kato.task.forceRetryFatalError", true);
      return;
    }

    String clouddriverNamespace =
        getDynamicConfigService()
            .getConfig(
                String.class,
                "tasks.monitor-kato-task.kubernetes.deploy-manifest.namespace",
                "spinnaker");

    try {
      Manifest _ignored =
          oortService.getManifest(
              clouddriverAccount,
              clouddriverNamespace,
              "pod " + clouddriverPodName.getName(),
              false);
      log.info(
          "task ID: {} owner: {} is up and running. No need to force a retry of the task",
          katoTask.getId(),
          clouddriverPodName);
    } catch (Exception e) {
      log.debug(
          "exception occurred while attempting to lookup task: {} owner clouddriver information",
          katoTask.getId(),
          e);
      if (e instanceof RetrofitError) {
        RetrofitError retrofitError = (RetrofitError) e;
        // only attempt a retry if clouddriver owner pod manifest results in a 404
        if (retrofitError.getResponse().getStatus() == HttpStatus.NOT_FOUND.value()) {
          log.info(
              "Since task ID {} owner: {} manifest not found, attempting to force retry task execution",
              katoTask.getId(),
              clouddriverPodName.getName());
          ((PipelineExecutionImpl) stage.getExecution())
              .getSystemNotifications()
              .add(
                  new SystemNotification(
                      getClock().millis(),
                      "katoRetryTask",
                      "Issue detected with the current clouddriver owner of the task. Retrying "
                          + "downstream cloud provider operation",
                      false));
          try {
            // we need to reschedule the task so that it can be picked up by a different clouddriver
            // pod
            getKatoService().updateTaskRetryability("kubernetes", katoTask.getId(), true);
            getKatoService()
                .restartTask(
                    "kubernetes", katoTask.getId(), ImmutableList.of(getOperations(stage)));
            log.info(
                "task: {} has been successfully rescheduled on another clouddriver pod",
                katoTask.getId());
            outputs.put("kato.task.forcedRetries", forcedRetries + 1);
            stage.getContext().put("kato.task.forcedRetries", forcedRetries + 1);
          } catch (Exception clouddriverException) {
            log.warn(
                "Attempt failed to retry task with id: {}", katoTask.getId(), clouddriverException);
            outputs.put("kato.task.forceRetryFatalError", true);
            stage.getContext().put("kato.task.forceRetryFatalError", true);
          }
        }
      }
    }
  }

  @VisibleForTesting
  public ImmutableMap<String, Map> getOperations(StageExecution stage) {
    String taskType = getTaskType(stage, DeployManifestTask.TASK_NAME);
    if (taskType == null || !taskType.equals(DeployManifestTask.TASK_NAME)) {
      throw new UnsupportedOperationException("Not supported for tasks of type: " + taskType);
    }

    DeployManifestContext context = stage.mapTo(DeployManifestContext.class);

    Map<String, Object> task = new HashMap<>(stage.getContext());

    task.put("source", "text");
    if (context.getTrafficManagement().isEnabled()) {
      task.put("services", context.getTrafficManagement().getOptions().getServices());
      task.put("enableTraffic", context.getTrafficManagement().getOptions().isEnableTraffic());
      task.put("strategy", context.getTrafficManagement().getOptions().getStrategy().name());
    } else {
      // For backwards compatibility, traffic is always enabled to new server groups when the new
      // traffic management
      // features are not enabled.
      task.put("enableTraffic", true);
    }

    return ImmutableMap.of(DeployManifestTask.TASK_NAME, task);
  }
}
