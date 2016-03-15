/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupLinearStageSupport
import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.DisableServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForAllInstancesDownTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component

@Component
class DisableServerGroupStage extends TargetServerGroupLinearStageSupport {
  static final String PIPELINE_CONFIG_TYPE = "disableServerGroup"

  @Override
  def <T extends Execution> List<StageDefinitionBuilder.TaskDefinition> taskGraph(Stage<T> parentStage) {
    [
      new StageDefinitionBuilder.TaskDefinition("determineHealthProviders", DetermineHealthProvidersTask),
      new StageDefinitionBuilder.TaskDefinition("disableServerGroup", DisableServerGroupTask),
      new StageDefinitionBuilder.TaskDefinition("monitorServerGroup", MonitorKatoTask),
      new StageDefinitionBuilder.TaskDefinition("waitForDownInstances", WaitForAllInstancesDownTask),
      new StageDefinitionBuilder.TaskDefinition("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
    ]
  }
}
