/*
 * Copyright 2019 Google, Inc.
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
 */
package com.netflix.spinnaker.orca.igor.pipeline;

import com.netflix.spinnaker.orca.CancellableStage;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.igor.tasks.*;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public abstract class CIStage implements StageDefinitionBuilder, CancellableStage {
  private final StopJenkinsJobTask stopJenkinsJobTask;

  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    String jobType = StringUtils.capitalize(getType());
    builder
      .withTask(String.format("start%sJob", jobType), StartJenkinsJobTask.class)
      .withTask(String.format("waitFor%sJobStart", jobType), waitForJobStartTaskClass());

    if (waitForCompletion(stage)) {
      builder.withTask(String.format("monitor%sJob", jobType), MonitorJenkinsJobTask.class);
      builder.withTask("getBuildProperties", GetBuildPropertiesTask.class);
      builder.withTask("getBuildArtifacts", GetBuildArtifactsTask.class);
    }
    if (stage.getContext().containsKey("expectedArtifacts")) {
      builder.withTask(BindProducedArtifactsTask.TASK_NAME, BindProducedArtifactsTask.class);
    }
  }

  private boolean waitForCompletion(Stage stage) {
    Object contextValue = stage.getContext().get("waitForCompletion");
    Boolean waitForCompletion = true;
    if (contextValue instanceof String) {
      String contextValueString = (String) contextValue;
      waitForCompletion = !contextValueString.equalsIgnoreCase("false");
    } else if (contextValue instanceof Boolean) {
      waitForCompletion = (Boolean) contextValue;
    }
    return waitForCompletion;
  }

  protected Class<? extends Task> waitForJobStartTaskClass() {
    return MonitorQueuedJenkinsJobTask.class;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void prepareStageForRestart(@Nonnull Stage stage) {
    Object buildInfo = stage.getContext().get("buildInfo");
    if (buildInfo != null) {
      Map<String, Object> restartDetails = (Map<String, Object>) stage.getContext()
        .computeIfAbsent("restartDetails", k -> new HashMap<String, Object>());
      restartDetails.put("previousBuildInfo", buildInfo);
    }
    stage.getContext().remove("buildInfo");
    stage.getContext().remove("buildNumber");
  }

  @Override
  public Result cancel(final Stage stage) {
    log.info(String.format(
      "Cancelling stage (stageId: %s, executionId: %s context: %s)",
      stage.getId(),
      stage.getExecution().getId(),
      stage.getContext()
    ));

    try {
      stopJenkinsJobTask.execute(stage);
    } catch (Exception e) {
      log.error(
        String.format("Failed to cancel stage (stageId: %s, executionId: %s), e: %s", stage.getId(), stage.getExecution().getId(), e.getMessage()),
        e
      );
    }
    return new Result(stage, new HashMap());
  }
}
