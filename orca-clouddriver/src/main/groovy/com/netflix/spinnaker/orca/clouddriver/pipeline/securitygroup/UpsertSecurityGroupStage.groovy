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

package com.netflix.spinnaker.orca.clouddriver.pipeline.securitygroup

import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.securitygroup.SecurityGroupForceCacheRefreshTask
import com.netflix.spinnaker.orca.clouddriver.tasks.securitygroup.UpsertSecurityGroupTask
import com.netflix.spinnaker.orca.clouddriver.tasks.securitygroup.WaitForUpsertedSecurityGroupTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component

@Component
class UpsertSecurityGroupStage implements StageDefinitionBuilder {
  @Override
  <T extends Execution> List<StageDefinitionBuilder.TaskDefinition> taskGraph(Stage<T> parentStage) {
    return [
      new StageDefinitionBuilder.TaskDefinition("upsertSecurityGroup", UpsertSecurityGroupTask),
      new StageDefinitionBuilder.TaskDefinition("monitorUpsert", MonitorKatoTask),
      new StageDefinitionBuilder.TaskDefinition("forceCacheRefresh", SecurityGroupForceCacheRefreshTask),
      new StageDefinitionBuilder.TaskDefinition("waitForUpsertedSecurityGroup", WaitForUpsertedSecurityGroupTask)
    ]
  }
}
