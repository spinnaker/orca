/*
 * Copyright 2015 Netflix, Inc.
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


package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.rollback.TestRollback
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilderImpl
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class RollbackServerGroupStageSpec extends Specification {
  @Shared
  def waitStage = new WaitStage()

  def "should build stages appropriate for strategy"() {
    given:
    def autowireCapableBeanFactory = Stub(AutowireCapableBeanFactory) {
      autowireBean(_) >> { TestRollback rollback ->
        rollback.waitStage = waitStage
      }
    }

    def rollbackServerGroupStage = new RollbackServerGroupStage()
    rollbackServerGroupStage.autowireCapableBeanFactory = autowireCapableBeanFactory

    def stage = stage {
      type = "rollbackServerGroup"
      context = [
        rollbackType                   : "TEST",
        rollbackContext                : [
          waitTime: 100,
        ]
      ]
    }

    def graphBefore = StageGraphBuilderImpl.beforeStages(stage)
    def graphAfter = StageGraphBuilderImpl.afterStages(stage)

    when:
    def tasks = rollbackServerGroupStage.buildTaskGraph(stage)

    rollbackServerGroupStage.beforeStages(stage, graphBefore)
    rollbackServerGroupStage.afterStages(stage, graphAfter)
    def beforeStages = graphBefore.build()
    def afterStages = graphAfter.build()

    then:
    tasks.iterator().size() == 0
    beforeStages.isEmpty()
    afterStages*.type == [
      "wait",
    ]
    afterStages[0].context == [waitTime: 100]
  }
}
