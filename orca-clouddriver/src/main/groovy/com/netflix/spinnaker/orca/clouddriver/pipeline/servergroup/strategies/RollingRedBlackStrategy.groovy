/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies

import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.DisableClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ScaleDownClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CreateServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.DetermineTargetServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.front50.pipeline.PipelineStage
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.WaitStage
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
class RollingRedBlackStrategy implements Strategy, ApplicationContextAware {
  final String name = "rollingredblack"

  @Autowired
  DisableClusterStage disableClusterStage

  @Autowired
  ResizeServerGroupStage resizeServerGroupStage

  @Autowired
  WaitStage waitStage

  @Autowired(required = false)
  PipelineStage pipelineStage

  @Autowired
  DetermineTargetServerGroupStage determineTargetServerGroupStage

  @Autowired
  ScaleDownClusterStage scaleDownClusterStage

  @Override
  List<Stage> composeFlow(Stage stage) {
    if (!pipelineStage) {
      throw new IllegalStateException("Rolling red/black cannot be run without front50 enabled. Please set 'front50.enabled: true' in your orca config.")
    }

    def stages = []
    def stageData = stage.mapTo(RollingRedBlackStageData)
    def cleanupConfig = AbstractDeployStrategyStage.CleanupConfig.fromStage(stage)

    Map baseContext = [
      (cleanupConfig.location.singularType()): cleanupConfig.location.value,
      cluster                                : cleanupConfig.cluster,
      moniker                                : cleanupConfig.moniker,
      credentials                            : cleanupConfig.account,
      cloudProvider                          : cleanupConfig.cloudProvider,
    ]

    if (stageData.targetSize) {
      stage.context.targetSize = 0
    }

    if (stage.context.useSourceCapacity) {
      stage.context.useSourceCapacity = false
    }

    // we expect a capacity object if a fixed capacity has been requested or as a fallback value when we are copying
    // the capacity from the current server group
    def savedCapacity = stage.context.savedCapacity ?: stage.context.capacity?.clone()
    stage.context.savedCapacity = savedCapacity

    // FIXME: this clobbers the input capacity value (if any). Should find a better way to request a new asg of size 0
    stage.context.capacity = [
      min    : 0,
      max    : 0,
      desired: 0
    ]

    def targetPercentages = stageData.getTargetPercentages()
    if (targetPercentages.isEmpty() || targetPercentages[-1] != 100) {
      targetPercentages.add(100)
    }

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
      SyntheticStageOwner.STAGE_AFTER
    )

    // Get source ASG from prior determineSourceServerGroupTask
    def source = null

    try {
      StageData.Source sourceServerGroup

      Stage parentCreateServerGroupStage = stage.directAncestors().find() { it.type == CreateServerGroupStage.PIPELINE_CONFIG_TYPE }

      StageData parentStageData = parentCreateServerGroupStage.mapTo(StageData)
      sourceServerGroup = parentStageData.source

      if (sourceServerGroup != null && sourceServerGroup.serverGroupName != null) {
        source = new ResizeStrategy.Source(
          region: sourceServerGroup.region,
          serverGroupName: sourceServerGroup.serverGroupName,
          credentials: stageData.credentials ?: stageData.account,
          cloudProvider: stageData.cloudProvider
        )
      }
    } catch (Exception e) {
      // This probably means there was no parent CreateServerGroup stage - which should never happen
      throw new IllegalStateException("Failed to determine source server group from parent stage while planning RRB flow", e)
    }

    if (source == null) {
      log.warn("no source server group -- will perform RRB to exact fallback capacity $savedCapacity with no disableCluster or scaleDownCluster stages")
    }

    // java .forEach rather than groovy .each, since the nested .each closure sometimes omits parent context
    targetPercentages.forEach({ p ->
      def resizeContext = baseContext + [
        target              : TargetServerGroup.Params.Target.current_asg_dynamic,
        targetLocation      : cleanupConfig.location,
        scalePct            : p,
        pinCapacity         : p < 100,  // if p < 100, capacity should be pinned (min == max == desired)
        unpinMinimumCapacity: p == 100, // if p == 100, min capacity should be restored to the original unpinned value from source
        useNameAsLabel      : true,     // hint to deck that it should _not_ override the name
        targetHealthyDeployPercentage: stage.context.targetHealthyDeployPercentage
      ]

      if (source) {
        resizeContext = resizeContext + [
          action: ResizeStrategy.ResizeAction.scale_to_server_group,
          source: source
        ]
      } else {
        resizeContext = resizeContext + [
          action: ResizeStrategy.ResizeAction.scale_exact,
        ]
        resizeContext.capacity = savedCapacity // will scale to a percentage of that static capacity
      }

      log.info("Adding `Grow to $p% of Desired Size` stage with context $resizeContext [executionId=${stage.execution.id}]")

      def resizeStage = newStage(
        stage.execution,
        resizeServerGroupStage.type,
        "Grow to $p% of Desired Size",
        resizeContext,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
      stages << resizeStage

      // an expression to grab newly deployed server group at runtime (ie. the server group being resized up)
      def deployedServerGroupName = '${' + "#stage('${resizeStage.id}')['context']['asgName']" + '}'.toString()
      stages.addAll(getBeforeCleanupStages(stage, stageData, source, deployedServerGroupName, p))

      // only generate the "disable p% of traffic" stages if we have something to disable
      if (source) {
        def disableContext = baseContext + [
          desiredPercentage           : p,
          remainingEnabledServerGroups: 1,
          preferLargerOverNewer       : false
        ]

        log.info("Adding `Disable $p% of Desired Size` stage with context $disableContext [executionId=${stage.execution.id}]")

        stages << newStage(
          stage.execution,
          disableClusterStage.type,
          "Disable $p% of Traffic",
          disableContext,
          stage,
          SyntheticStageOwner.STAGE_AFTER
        )
      }
    })

    // only scale down if we have a source server group to scale down
    if (source && stageData.scaleDown) {
      if(stageData?.getDelayBeforeScaleDown()) {
        def waitContext = [waitTime: stageData?.getDelayBeforeScaleDown()]
        stages << newStage(
          stage.execution,
          waitStage.type,
          "Wait Before Scale Down",
          waitContext,
          stage,
          SyntheticStageOwner.STAGE_AFTER
        )
      }

      def scaleDown = baseContext + [
        allowScaleDownActive         : false,
        remainingFullSizeServerGroups: 1,
        preferLargerOverNewer        : false
      ]
      stages << newStage(
        stage.execution,
        scaleDownClusterStage.type,
        "scaleDown",
        scaleDown,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    return stages
  }

  List<Stage> getBeforeCleanupStages(Stage parentStage,
                                     RollingRedBlackStageData stageData,
                                     ResizeStrategy.Source source,
                                     String deployedServerGroupName,
                                     int percentageComplete) {
    def stages = []

    if (stageData.getDelayBeforeCleanup()) {
      def waitContext = [waitTime: stageData.getDelayBeforeCleanup()]
      stages << newStage(
        parentStage.execution,
        waitStage.type,
        "wait",
        waitContext,
        parentStage,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    if (stageData.pipelineBeforeCleanup?.application && stageData.pipelineBeforeCleanup?.pipelineId) {
      def serverGroupCoordinates = [
        region         : source.region,
        serverGroupName: source.serverGroupName,
        account        : source.credentials,
        cloudProvider  : source.cloudProvider
      ]

      def pipelineContext = [
        application        : stageData.pipelineBeforeCleanup.application,
        pipelineApplication: stageData.pipelineBeforeCleanup.application,
        pipelineId         : stageData.pipelineBeforeCleanup.pipelineId,
        pipelineParameters : stageData.pipelineBeforeCleanup.pipelineParameters + [
          "deployedServerGroup": serverGroupCoordinates + [
            serverGroupName: deployedServerGroupName
          ],
          "sourceServerGroup"  : serverGroupCoordinates + [
            serverGroupName: source.serverGroupName
          ],
          "percentageComplete" : percentageComplete
        ]
      ]

      stages << newStage(
        parentStage.execution,
        pipelineStage.type,
        "Run Validation Pipeline",
        pipelineContext,
        parentStage,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    return stages
  }

  ApplicationContext applicationContext
}
