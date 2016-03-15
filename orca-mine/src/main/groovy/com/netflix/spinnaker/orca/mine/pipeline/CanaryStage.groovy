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

package com.netflix.spinnaker.orca.mine.pipeline

import java.util.concurrent.TimeUnit
import groovy.util.logging.Slf4j
import com.netflix.frigga.autoscaling.AutoScalingGroupNameBuilder
import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.ShrinkClusterTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.newStage

@Slf4j
@Component
class CanaryStage implements StageDefinitionBuilder, CancellableStage {
  @Autowired DeployCanaryStage deployCanaryStage
  @Autowired MonitorCanaryStage monitorCanaryStage
  @Autowired ShrinkClusterTask shrinkClusterTask

  @Override
  def <T extends Execution> List<Stage<T>> aroundStages(Stage<T> parentStage) {
    Map canaryStageId = [canaryStageId: parentStage.id]

    Map<String, Object> deployContext = canaryStageId + parentStage.context
    Map<String, Object> monitorContext = canaryStageId + [scaleUp: parentStage.context.scaleUp ?: [:]]

    return [
      newStage(parentStage.execution, deployCanaryStage.type, "Deploy Canary", deployContext, parentStage, SyntheticStageOwner.STAGE_AFTER),
      newStage(parentStage.execution, monitorCanaryStage.type, "Monitor Canary", monitorContext, parentStage, SyntheticStageOwner.STAGE_AFTER)
    ]
  }

  @Override
  CancellableStage.Result cancel(Stage stage) {
    log.info("Cancelling stage (stageId: ${stage.id}, executionId: ${stage.execution.id}, context: ${stage.context as Map})")

    // it's possible the server groups haven't been created yet, allow a grace period before cleanup
    Thread.sleep(TimeUnit.MINUTES.toMillis(2))

    Collection<Map<String, Object>> shrinkContexts = []
    stage.context.clusterPairs.each { Map<String, Map> clusterPair ->
      [clusterPair.baseline, clusterPair.canary].each { Map<String, String> cluster ->

        def builder = new AutoScalingGroupNameBuilder()
        builder.appName = cluster.application
        builder.stack = cluster.stack
        builder.detail = cluster.freeFormDetails

        shrinkContexts << [
          cluster          : builder.buildGroupName(),
          regions          : (cluster.availabilityZones as Map).keySet(),
          shrinkToSize     : 0,
          allowDeleteActive: true,
          credentials      : cluster.account
        ]
      }
    }

    def shrinkResults = shrinkContexts.collect {
      def shrinkStage = new PipelineStage()
      shrinkStage.context.putAll(it)
      shrinkClusterTask.execute(shrinkStage)
    }

    return new CancellableStage.Result(stage, [
      shrinkContexts: shrinkContexts,
      shrinkResults : shrinkResults
    ])
  }
}
