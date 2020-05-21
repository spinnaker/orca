/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.orca.clouddriver.pipeline.job;

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestForceCacheRefreshTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.PromoteManifestKatoOutputsTask;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
@NonnullByDefault
public class KubernetesRunJobStageDecorator implements RunJobStageDecorator {
  @Override
  public boolean supports(String cloudProvider) {
    return "kubernetes".equalsIgnoreCase(cloudProvider);
  }

  @Override
  public void afterRunJobTaskGraph(StageExecution stageExecution, TaskNode.Builder builder) {
    final Map<String, Object> context = stageExecution.getContext();
    boolean manifestBasedRunJob = context.containsKey("source") || context.containsKey("manifest");

    if (manifestBasedRunJob) {
      builder
          .withTask("promoteOutputs", PromoteManifestKatoOutputsTask.class)
          .withTask("forceCacheRefresh", ManifestForceCacheRefreshTask.class);
    }
  }

  @Override
  public void modifyDestroyJobContext(
      RunJobStageContext context, Map<String, Object> destroyContext) {
    // Do nothing.
  }
}
