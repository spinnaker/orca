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

import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.DisableClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ScaleDownClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ShrinkClusterStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification

class RedBlackStrategySpec extends Specification {

  def ShrinkClusterStage shrinkClusterStage = new ShrinkClusterStage()
  def ScaleDownClusterStage scaleDownClusterStage = new ScaleDownClusterStage()
  def DisableClusterStage disableClusterStage = new DisableClusterStage()

  def "should compose flow"() {
    given:
      def ctx = [
          account          : "testAccount",
          application      : "unit",
          stack            : "tests",
          cloudProvider    : "aws",
          region           : "north",
          availabilityZones: [
              north: ["pole-1a"]
          ]
      ]
      def stage = new PipelineStage(new Pipeline(), "whatever", ctx)
      def strat = new RedBlackStrategy(shrinkClusterStage: shrinkClusterStage,
                                       scaleDownClusterStage: scaleDownClusterStage,
                                       disableClusterStage: disableClusterStage)

    when:
      strat.composeFlow(stage)

    then:
      stage.afterStages.size() == 1
      stage.afterStages.first().stageBuilder == disableClusterStage
      stage.afterStages.first().context == [
          credentials                   : "testAccount",
          cloudProvider                 : "aws",
          cluster                       : "unit-tests",
          region                        : "north",
          remainingEnabledServerGroups  : 1,
          preferLargerOverNewer         : false,
          interestingHealthProviderNames: null
      ]

    when:
      ctx.maxRemainingAsgs = 10
      stage = new PipelineStage(new Pipeline(), "whatever", ctx)
      strat.composeFlow(stage)

    then:
      stage.afterStages.size() == 2
      stage.afterStages.first().stageBuilder == shrinkClusterStage
      stage.afterStages.first().context.shrinkToSize == 10
      stage.afterStages.last().stageBuilder == disableClusterStage

    when:
      ctx.scaleDown = true
      stage = new PipelineStage(new Pipeline(), "whatever", ctx)
      strat.composeFlow(stage)

    then:
      stage.afterStages.size() == 3
      stage.afterStages[0].stageBuilder == shrinkClusterStage
      stage.afterStages[1].stageBuilder == scaleDownClusterStage
      stage.afterStages[1].context.allowScaleDownActive == true
      stage.afterStages[2].stageBuilder == disableClusterStage

  }
}
