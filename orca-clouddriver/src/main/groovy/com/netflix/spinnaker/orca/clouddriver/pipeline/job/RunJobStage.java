/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.job;

import com.netflix.spinnaker.orca.clouddriver.tasks.artifacts.ConsumeArtifactTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.job.MonitorJobTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.job.RunJobTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.job.WaitOnJobCompletion;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RunJobStage implements StageDefinitionBuilder {

  @Override
  public void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder.withTask("runJob", RunJobTask.class)
      .withTask("monitorDeploy", MonitorJobTask.class);

    if (!stage.getContext().getOrDefault("waitForCompletion", "true").toString().equalsIgnoreCase("false")) {
      builder.withTask("waitOnJobCompletion", WaitOnJobCompletion.class);
    }

    if (stage.getContext().containsKey("expectedArtifacts")) {
      builder.withTask(BindProducedArtifactsTask.TASK_NAME, BindProducedArtifactsTask.class);
    }

    if (stage.getContext().containsKey("consumeArtifactId")) {
      builder.withTask(ConsumeArtifactTask.TASK_NAME, ConsumeArtifactTask.class);
    }
  }

  @Override
  public void prepareStageForRestart(Stage stage) {
    Map<String, Object> context = stage.getContext();

    // preserve previous job details
    if (context.containsKey("jobStatus")) {
      Map<String, Object> restartDetails = context.containsKey("restartDetails") ?
        (Map<String, Object>) context.get("restartDetails") :
        new HashMap<>();
      restartDetails.put("jobStatus", context.get("jobStatus"));
      restartDetails.put("completionDetails", context.get("completionDetails"));
      restartDetails.put("propertyFileContents", context.get("propertyFileContents"));
      restartDetails.put("deploy.jobs", context.get("deploy.jobs"));
      context.put("restartDetails", restartDetails);
    }

    context.remove("jobStatus");
    context.remove("completionDetails");
    context.remove("propertyFileContents");
    context.remove("deploy.jobs");
  }
}
