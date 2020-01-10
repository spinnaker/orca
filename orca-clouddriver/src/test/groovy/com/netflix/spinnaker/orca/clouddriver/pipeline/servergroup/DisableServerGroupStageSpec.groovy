/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.test.model.ExecutionBuilder
import spock.lang.Specification
import spock.lang.Unroll

class DisableServerGroupStageSpec extends Specification {
  @Unroll
  def "should run tasks appropriate for #provider & cache refresh #forceCacheRefresh"() {
    given:
    def dynamicConfigService = Stub(DynamicConfigService) {
      isEnabled(_, _) >> forceCacheRefresh
    }
    def disableServerGroupStage = new DisableServerGroupStage(dynamicConfigService)
    def parentStage = ExecutionBuilder.stage {}
    def stage = ExecutionBuilder.stage {
      type = "disableServerGroup"
      context["regions"] = ["us-west-1"]
      context["target"] = "current_asg_dynamic"
      context["cloudProvider"] = provider
      parentStageId = parentStage.id
    }
    stage.execution.stages.add(parentStage)

    when:
    def graphBefore = StageGraphBuilder.beforeStages(stage)
    def graphAfter = StageGraphBuilder.afterStages(stage)
    disableServerGroupStage.beforeStages(stage, graphBefore)
    def tasks = disableServerGroupStage.buildTaskGraph(stage).toList()
    disableServerGroupStage.afterStages(stage, graphAfter)
    def beforeStages = graphBefore.build().toList()
    def afterStages = graphAfter.build().toList()

    then:
    beforeStages.isEmpty()
    def expectTasks = ["determineHealthProviders", "disableServerGroup", "monitorServerGroup"]
    if (expectWaitDown) {
      expectTasks.add("waitForDownInstances")
    }
    if (forceCacheRefresh) {
      expectTasks.add("forceCacheRefresh")
    }
    tasks*.name == expectTasks
    afterStages.isEmpty()

    where:
    forceCacheRefresh | provider || expectWaitDown
    true              | "aws"    || false
    false             | "aws"    || false
    true              | "gcs"    || true
    false             | "gcs"    || true
    true              | "azure"  || true
    false             | "azure"  || true
  }
}
