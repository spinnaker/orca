/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.mahe.pipeline

import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.mahe.MaheService
import com.netflix.spinnaker.orca.mahe.tasks.CreatePropertiesTask
import com.netflix.spinnaker.orca.mahe.tasks.MonitorPropertiesTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.getType

@Slf4j
@Component
class MonitorCreatePropertyStage implements StageDefinitionBuilder, CancellableStage {
  public static final String PIPELINE_CONFIG_TYPE = getType(MonitorCreatePropertyStage)

  @Autowired
  MaheService maheService

  @Override
  CancellableStage.Result cancel(Stage stage) {
    return null
  }

  @Override
  <T extends Execution> List<StageDefinitionBuilder.TaskDefinition> taskGraph(Stage<T> parentStage) {
    return [
      new StageDefinitionBuilder.TaskDefinition("createProperties", CreatePropertiesTask),
      new StageDefinitionBuilder.TaskDefinition("monitorProperties", MonitorPropertiesTask)
    ]
  }
}
