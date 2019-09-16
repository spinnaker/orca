/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.gce

import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.ApplySourceServerGroupCapacityStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.CaptureSourceServerGroupCapacityTask
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.AbstractDeployStrategyStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.DeployStagePreProcessor
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.kato.pipeline.strategy.Strategy
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategySupport.getSource

@Component
class GceDeployStagePreProcessor implements DeployStagePreProcessor  {

  @Autowired
  ApplySourceServerGroupCapacityStage applySourceServerGroupSnapshotStage

  @Autowired
  ResizeServerGroupStage resizeServerGroupStage

  @Autowired
  TargetServerGroupResolver targetServerGroupResolver

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
  List<StageDefinition> beforeStageDefinitions(Stage stage) {
    def stageData = stage.mapTo(StageData)
    if (shouldPinSourceServerGroup(stageData.strategy)) {
      def resizeContext = getResizeContext(stageData)
      resizeContext.pinMinimumCapacity = true

      return [
        new StageDefinition(
          name: "Pin ${resizeContext.serverGroupName}".toString(),
          stageDefinitionBuilder: resizeServerGroupStage,
          context: resizeContext
        )
      ]
    }

    return []
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

  @Override
  List<StageDefinition> onFailureStageDefinitions(Stage stage) {
    def stageData = stage.mapTo(StageData)
    def stageDefinitions = []

    def unpinServerGroupStage = buildUnpinServerGroupStage(stageData)
    if (unpinServerGroupStage) {
      stageDefinitions << unpinServerGroupStage
    }

    return stageDefinitions
  }

  @Override
  boolean supports(Stage stage) {
    def stageData = stage.mapTo(StageData)
    return stageData.cloudProvider == "gce"
  }

  private static boolean shouldPinSourceServerGroup(String strategy) {
    return (Strategy.fromStrategyKey(strategy) == Strategy.RED_BLACK)
    // || Strategy.fromStrategyKey(strategy) == Strategy.ROLLING_RED_BLACK TODO(jacobkiefer): Insert if/when RRB is implemented for GCE.
  }

  private Map<String, Object> getResizeContext(StageData stageData) {
    def cleanupConfig = AbstractDeployStrategyStage.CleanupConfig.fromStage(stageData)
    def baseContext = [
      (cleanupConfig.location.singularType()): cleanupConfig.location.value,
      cluster                                : cleanupConfig.cluster,
      moniker                                : cleanupConfig.moniker,
      credentials                            : cleanupConfig.account,
      cloudProvider                          : cleanupConfig.cloudProvider,
    ]

    def source = getSource(targetServerGroupResolver, stageData, baseContext)
    baseContext.putAll([
      serverGroupName   : source.serverGroupName,
      action            : ResizeStrategy.ResizeAction.scale_to_server_group,
      source            : source,
      useNameAsLabel    : true     // hint to deck that it should _not_ override the name
    ])
    return baseContext
  }

  private StageDefinition buildUnpinServerGroupStage(StageData stageData) {
    if (!shouldPinSourceServerGroup(stageData.strategy)) {
      return null
    }

    if (stageData.scaleDown) {
      // source server group has been scaled down, no need to unpin if deploy was successful
      return null
    }

    def resizeContext = getResizeContext(stageData)
    resizeContext.unpinMinimumCapacity = true

    return new StageDefinition(
      name: "Unpin ${resizeContext.serverGroupName}".toString(),
      stageDefinitionBuilder: resizeServerGroupStage,
      context: resizeContext
    )
  }
}
