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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies

import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.DisableClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ScaleDownClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ShrinkClusterStage
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import spock.lang.Specification

class RedBlackStrategySpec extends Specification {

  def trafficGuard = Stub(TrafficGuard)
  def disableClusterStage = new DisableClusterStage(trafficGuard)
  def shrinkClusterStage = new ShrinkClusterStage(trafficGuard, disableClusterStage)
  def scaleDownClusterStage = new ScaleDownClusterStage(trafficGuard)
  def waitStage = new WaitStage()

  def "should compose flow"() {
    given:
      Moniker moniker = new Moniker(app: "unit", stack: "tests");
      def ctx = [
        account               : "testAccount",
        application           : "unit",
        stack                 : "tests",
        moniker               : moniker,
        cloudProvider         : "aws",
        region                : "north",
        availabilityZones     : [
          north: ["pole-1a"]
        ]
      ]
      def stage = new Stage(Execution.newPipeline("orca"), "whatever", ctx)
      def strat = new RedBlackStrategy(
        shrinkClusterStage: shrinkClusterStage,
        scaleDownClusterStage: scaleDownClusterStage,
        disableClusterStage: disableClusterStage,
        waitStage: waitStage
      )

    when:
      def syntheticStages = strat.composeFlow(stage)
      def beforeStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
      def afterStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
      beforeStages.isEmpty()
      afterStages.size() == 1
      afterStages.first().type == disableClusterStage.type
      afterStages.first().context == [
          credentials                   : "testAccount",
          cloudProvider                 : "aws",
          cluster                       : "unit-tests",
          moniker                       : moniker,
          region                        : "north",
          remainingEnabledServerGroups  : 1,
          preferLargerOverNewer         : false,
      ]

    when:
      ctx.maxRemainingAsgs = 10
      stage = new Stage(Execution.newPipeline("orca"), "whatever", ctx)
      syntheticStages = strat.composeFlow(stage)
      beforeStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
      afterStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
      beforeStages.isEmpty()
      afterStages.size() == 2
      afterStages.first().type == shrinkClusterStage.type
      afterStages.first().context.shrinkToSize == 10
      afterStages.last().type == disableClusterStage.type

    when:
      ctx.scaleDown = true
      stage = new Stage(Execution.newPipeline("orca"), "whatever", ctx)
      syntheticStages = strat.composeFlow(stage)
      beforeStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
      afterStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
      beforeStages.isEmpty()
      afterStages.size() == 3
      afterStages[0].type == shrinkClusterStage.type
      afterStages[1].type == disableClusterStage.type
      afterStages[2].type == scaleDownClusterStage.type
      afterStages[2].context.allowScaleDownActive == false

    when:
      ctx.interestingHealthProviderNames = ["Google"]
      stage = new Stage(Execution.newPipeline("orca"), "whatever", ctx)
      syntheticStages = strat.composeFlow(stage)
      beforeStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
      afterStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
      beforeStages.isEmpty()
      afterStages.size() == 3
      afterStages.first().type == shrinkClusterStage.type
      afterStages.first().context.interestingHealthProviderNames == ["Google"]

    when:
      ctx.delayBeforeDisableSec = 5
      ctx.delayBeforeScaleDownSec = 10
      stage = new Stage(Execution.newPipeline("orca"), "resize", ctx)
      syntheticStages = strat.composeFlow(stage)
      beforeStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
      afterStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
      beforeStages.isEmpty()
      afterStages[0].type == shrinkClusterStage.type
      afterStages[1].type == waitStage.type
      afterStages[1].context.waitTime == 5
      afterStages[2].type == disableClusterStage.type
      afterStages[3].type == waitStage.type
      afterStages[3].context.waitTime == 10
      afterStages[4].type == scaleDownClusterStage.type
  }
}
