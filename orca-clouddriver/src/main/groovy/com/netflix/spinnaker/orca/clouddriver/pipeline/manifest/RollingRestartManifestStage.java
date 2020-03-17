/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.manifest;

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestForceCacheRefreshTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.RollingRestartManifestTask;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Component
public class RollingRestartManifestStage implements StageDefinitionBuilder {
  public static final String PIPELINE_CONFIG_TYPE = "rollingRestartManifest";

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    builder
        .withTask(RollingRestartManifestTask.TASK_NAME, RollingRestartManifestTask.class)
        .withTask("monitorRollingRestart", MonitorKatoTask.class)
        .withTask(ManifestForceCacheRefreshTask.TASK_NAME, ManifestForceCacheRefreshTask.class);
  }
}
