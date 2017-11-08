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

package com.netflix.spinnaker.orca.clouddriver.pipeline.manifest;

import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.DeleteManifestForceCacheRefreshTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.DeleteManifestTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.stereotype.Component;

@Component
public class DeleteManifestStage implements StageDefinitionBuilder {
  public static final String PIPELINE_CONFIG_TYPE = "deleteManifest";

  @Override
  public void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder.withTask(DeleteManifestTask.TASK_NAME, DeleteManifestTask.class)
        .withTask("monitorDelete", MonitorKatoTask.class)
        .withTask(DeleteManifestForceCacheRefreshTask.TASK_NAME, DeleteManifestForceCacheRefreshTask.class);
  }
}
