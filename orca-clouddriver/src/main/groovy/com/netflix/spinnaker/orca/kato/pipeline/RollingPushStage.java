/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.kato.pipeline;

import static java.lang.String.format;

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.FeaturesService;
import com.netflix.spinnaker.orca.clouddriver.ForceCacheRefreshAware;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.TerminateInstancesTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForDownInstanceHealthTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForTerminatedInstancesTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.CaptureParentInterestingHealthProviderNamesTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask;
import com.netflix.spinnaker.orca.kato.tasks.DisableInstancesTask;
import com.netflix.spinnaker.orca.kato.tasks.rollingpush.*;
import com.netflix.spinnaker.orca.pipeline.tasks.WaitTask;
import java.util.Map;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RollingPushStage implements StageDefinitionBuilder, ForceCacheRefreshAware {

  public static final String PIPELINE_CONFIG_TYPE = "rollingPush";

  @Autowired private FeaturesService featuresService;

  @Autowired private DynamicConfigService dynamicConfigService;

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    boolean taggingEnabled = featuresService.areEntityTagsAvailable();
    builder
        .withTask(
            "captureParentInterestingHealthProviderNames",
            CaptureParentInterestingHealthProviderNamesTask.class)
        .withTask("determineTerminationCandidates", DetermineTerminationCandidatesTask.class)
        .withLoop(
            subGraph -> {
              subGraph.withTask(
                  "determineCurrentPhaseTerminations",
                  DetermineTerminationPhaseInstancesTask.class);

              if (shouldWaitForTermination(stage)) {
                subGraph.withTask("wait", WaitTask.class);
              }

              subGraph
                  .withTask("disableInstances", DisableInstancesTask.class)
                  .withTask("monitorDisable", MonitorKatoTask.class)
                  .withTask("waitForDisabledState", WaitForDownInstanceHealthTask.class)
                  .withTask("terminateInstances", TerminateInstancesTask.class)
                  .withTask("waitForTerminateOperation", MonitorKatoTask.class)
                  .withTask("waitForTerminatedInstances", WaitForTerminatedInstancesTask.class);

              if (isForceCacheRefreshEnabled(dynamicConfigService)) {
                subGraph.withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask.class);
              }

              subGraph
                  .withTask("waitForNewInstances", WaitForNewUpInstancesLaunchTask.class)
                  .withTask(
                      "checkForRemainingTerminations", CheckForRemainingTerminationsTask.class);
            });

    if (taggingEnabled) {
      builder
          .withTask("cleanUpTags", CleanUpTagsTask.class)
          .withTask("monitorTagCleanUp", MonitorKatoTask.class);
    }

    builder.withTask("pushComplete", PushCompleteTask.class);
  }

  private boolean shouldWaitForTermination(StageExecution stage) {
    Map termination = (Map) stage.getContext().get("termination");
    return termination != null && termination.containsKey("waitTime");
  }

  @Component
  public static class PushCompleteTask implements Task {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Nonnull
    @Override
    public TaskResult execute(@Nonnull StageExecution stage) {
      log.info(
          format(
              "Rolling Push completed for %s in %s / %s",
              stage.getContext().get("asgName"),
              stage.getContext().get("account"),
              stage.getContext().get("region")));
      return TaskResult.SUCCEEDED;
    }
  }
}
