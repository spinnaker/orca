/*
 * Copyright 2021 Salesforce.com, Inc.
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

package com.netflix.spinnaker.orca.config;

import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("executions")
public class ExecutionConfigurationProperties {
  /**
   * flag to enable/disable blocking of orchestration executions (ad-hoc operations). By default, it
   * is turned off, which means all ad-hoc executions are supported.
   */
  private boolean blockOrchestrationExecutions = false;

  /**
   * this is only applicable when blockOrchestrationExecutions: true. This allows the user to
   * configure only those orchestration executions(ad-hoc operations) that should be allowed. Every
   * orchestration execution not in this set will be blocked
   */
  private Set<String> allowedOrchestrationExecutions =
      Set.of(
          "deleteApplication",
          "saveApplication",
          "updateApplication",
          "deletePipeline",
          "savePipeline",
          "updatePipeline",
          "reorderPipelines",
          "createPipelineTemplate",
          "deletePipelineTemplate",
          "updatePipelineTemplate",
          "createV2PipelineTemplate",
          "deleteV2PipelineTemplate",
          "updateV2PipelineTemplate",
          "upsertEntityTags",
          "deleteEntityTags",
          "upsertPluginInfo",
          "deletePluginInfo");
}
