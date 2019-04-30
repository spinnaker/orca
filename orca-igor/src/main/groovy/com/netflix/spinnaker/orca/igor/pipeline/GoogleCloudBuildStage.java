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

import com.netflix.spinnaker.orca.igor.model.GoogleCloudBuildStageDefinition;
import com.netflix.spinnaker.orca.igor.tasks.GetGoogleCloudBuildArtifactsTask;
import com.netflix.spinnaker.orca.igor.tasks.MonitorGoogleCloudBuildTask;
import com.netflix.spinnaker.orca.igor.tasks.StartGoogleCloudBuildTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleCloudBuildStage implements StageDefinitionBuilder {
  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    GoogleCloudBuildStageDefinition stageDefinition = stage.mapTo(GoogleCloudBuildStageDefinition.class);
    builder
      .withTask("startGoogleCloudBuildTask", StartGoogleCloudBuildTask.class)
      .withTask("monitorGoogleCloudBuildTask", MonitorGoogleCloudBuildTask.class)
      .withTask("getGoogleCloudBuildArtifactsTask", GetGoogleCloudBuildArtifactsTask.class)
    ;
  }
}
