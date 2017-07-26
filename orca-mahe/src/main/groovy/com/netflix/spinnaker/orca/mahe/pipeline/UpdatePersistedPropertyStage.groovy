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

import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.mahe.tasks.RollbackPropertyTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class UpdatePersistedPropertyStage implements StageDefinitionBuilder, CancellableStage {
  @Autowired MonitorCreatePropertyStage monitorCreatePropertyStage
  @Autowired RollbackPropertyTask rollbackPropertyTask

  @Override
  def <T extends Execution> List<Stage<T>> aroundStages(Stage<T> parentStage) {
    return [
      newStage(
        parentStage.execution,
        monitorCreatePropertyStage.getType(),
        "Monitor Update Property",
        parentStage.context + [propertyStageId: parentStage.id],
        parentStage,
        SyntheticStageOwner.STAGE_AFTER
      )
    ]
  }

  @Override
  CancellableStage.Result cancel(Stage stage) {
    log.info("Cancelling stage (stageId: ${stage.id}, executionId: ${stage.execution.id}, context: ${stage.context as Map})")

    def deletedProperties = rollbackPropertyTask.execute(stage)

    return new CancellableStage.Result(stage, [
       deletedPropertyIdList: stage.context.propertyIdList,
       deletedPropertiesResults: deletedProperties
    ])
  }
}
