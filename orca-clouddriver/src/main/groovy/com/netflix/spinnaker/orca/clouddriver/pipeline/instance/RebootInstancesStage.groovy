/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.instance

import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.RebootInstancesTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForDownInstanceHealthTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForUpInstanceHealthTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.stereotype.Component

@Component
@CompileStatic
class RebootInstancesStage implements StageDefinitionBuilder {
  @Override
  <T extends Execution> List<StageDefinitionBuilder.TaskDefinition> taskGraph(Stage<T> parentStage) {
    return [
      new StageDefinitionBuilder.TaskDefinition("rebootInstances", RebootInstancesTask),
      new StageDefinitionBuilder.TaskDefinition("monitorReboot", MonitorKatoTask),
      new StageDefinitionBuilder.TaskDefinition("waitForDownInstances", WaitForDownInstanceHealthTask),
      new StageDefinitionBuilder.TaskDefinition("waitForUpInstances", WaitForUpInstanceHealthTask)
    ]
  }
}
