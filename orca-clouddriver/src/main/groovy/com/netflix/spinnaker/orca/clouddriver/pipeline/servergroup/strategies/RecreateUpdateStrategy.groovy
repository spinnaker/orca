/*
 * Copyright 2017 Cisco, Inc.
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
package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.DetermineTargetServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.newStage

@Slf4j
@Component
class RecreateUpdateStrategy implements Strategy, ApplicationContextAware {
  final String name = "recreateupdate"

  @Autowired
  ResizeServerGroupStage resizeServerGroupStage

  @Autowired
  DetermineTargetServerGroupStage determineTargetServerGroupStage

  @Autowired
  TargetServerGroupResolver targetServerGroupResolver

  @Override
  List<Stage> composeFlow(Stage stage) {
    def stages = []
    def stageData = stage.mapTo(RecreateUpdateStrategyData)
    def cleanupConfig = AbstractDeployStrategyStage.CleanupConfig.fromStage(stage)

    Map baseContext = [
      (cleanupConfig.location.singularType()): cleanupConfig.location.value,
      cluster                                : cleanupConfig.cluster,
      credentials                            : cleanupConfig.account,
      cloudProvider                          : cleanupConfig.cloudProvider,
    ]

    stage.context.capacity = [
      min    : 0,
      max    : 0,
      desired: 0
    ]

    def p = 0
    def findContext = baseContext + [
      target        : TargetServerGroup.Params.Target.current_asg_dynamic,
      targetLocation: cleanupConfig.location,
    ]

    stages << newStage(
      stage.execution,
      determineTargetServerGroupStage.type,
      "Determine Deployed Server Group",
      findContext,
      stage,
      SyntheticStageOwner.STAGE_BEFORE
    )

    def source
    try {
      source = getSource(targetServerGroupResolver, stageData, baseContext)
    } catch (Exception e) {
      stages = []
      return stages
    }

    def resizeContext = baseContext + [
      target        : TargetServerGroup.Params.Target.current_asg_dynamic,
      action        : ResizeStrategy.ResizeAction.scale_exact,
      source        : source,
      targetLocation: cleanupConfig.location,
      kind          : stageData.kind,
      capacity      : stage.context.capacity,
      resizeType    : "exact",
      scaleNum      : 0
    ]

    def resizeStage = newStage(
      stage.execution,
      resizeServerGroupStage.type,
      "Scale down to $p% Desired Size(0)",
      resizeContext,
      stage,
      SyntheticStageOwner.STAGE_BEFORE
    )
    stages << resizeStage

    return stages
  }

  static ResizeStrategy.Source getSource(TargetServerGroupResolver targetServerGroupResolver,
                                         RecreateUpdateStrategyData stageData,
                                         Map baseContext) {
    if (stageData.source) {
      return new ResizeStrategy.Source(
        region: stageData.source.region,
        serverGroupName: stageData.source.serverGroupName ?: stageData.source.asgName,
        credentials: stageData.credentials ?: stageData.account,
        cloudProvider: stageData.cloudProvider
      )
    }

    // no source server group specified, lookup current server group
    TargetServerGroup target = targetServerGroupResolver.resolve(
      new Stage(null, null, null, baseContext + [target: TargetServerGroup.Params.Target.current_asg_dynamic])
    )?.get(0)

    return new ResizeStrategy.Source(
      region: target.getLocation().value,
      serverGroupName: target.getName(),
      credentials: stageData.credentials ?: stageData.account,
      cloudProvider: stageData.cloudProvider
    )
  }
  ApplicationContext applicationContext
}
