/*
 * Copyright 2014 Netflix, Inc.
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


package com.netflix.spinnaker.orca.kato.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForUpInstanceHealthTask
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.kato.tasks.EnableInstancesInDiscoveryTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class EnableInstancesInDiscoveryStage implements StageDefinitionBuilder {
  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  @Override
  <T extends Execution> List<StageDefinitionBuilder.TaskDefinition> taskGraph(Stage<T> parentStage) {
    return Arrays.asList(
      new StageDefinitionBuilder.TaskDefinition("enableInstances", EnableInstancesInDiscoveryTask),
      new StageDefinitionBuilder.TaskDefinition("monitorInstances", MonitorKatoTask),
      new StageDefinitionBuilder.TaskDefinition("waitForDiscoveryState", WaitForUpInstanceHealthTask),
    );
  }
}
