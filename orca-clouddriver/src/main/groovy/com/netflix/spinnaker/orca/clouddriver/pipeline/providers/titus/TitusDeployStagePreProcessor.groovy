/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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


package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.titus

import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.ApplySourceServerGroupCapacityStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.CaptureSourceServerGroupCapacityTask
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.DeployStagePreProcessor
import com.netflix.spinnaker.orca.kato.pipeline.strategy.Strategy
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class TitusDeployStagePreProcessor implements DeployStagePreProcessor {
  @Autowired
  ApplySourceServerGroupCapacityStage applySourceServerGroupSnapshotStage

  @Override
  List<StepDefinition> additionalSteps(Stage stage) {
    def stageData = stage.mapTo(StageData)
    if (Strategy.fromStrategyKey(stageData.strategy) == Strategy.ROLLING_RED_BLACK) {
      // rolling red/black has no need to snapshot capacities
      return []
    }

    return [
      new StepDefinition(
        name: "snapshotSourceServerGroup",
        taskClass: CaptureSourceServerGroupCapacityTask
      )
    ]
  }

  @Override
  boolean supports(Stage stage) {
    def stageData = stage.mapTo(StageData)
    return stageData.cloudProvider == "titus" // && stageData.useSourceCapacity
  }

  @Override
  List<StageDefinition> afterStageDefinitions(Stage stage) {
    def stageData = stage.mapTo(StageData)
    def stageDefinitions = []
    if (Strategy.fromStrategyKey(stageData.strategy) != Strategy.ROLLING_RED_BLACK) {
      // rolling red/black has no need to apply a snapshotted capacity (on the newly created server group)
      stageDefinitions << new StageDefinition(
        name: "restoreMinCapacityFromSnapshot",
        stageDefinitionBuilder: applySourceServerGroupSnapshotStage,
        context: [:]
      )
    }

    return stageDefinitions
  }

}
