/*
 * Copyright 2016 Netflix, Inc.
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

import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import spock.lang.Specification
import spock.lang.Subject

class MonitorCanaryStageSpec extends Specification {
  def mineService = Mock(MineService)
  def stageNavigator = Stub(StageNavigator)

  @Subject
  def monitorCanaryStage = new MonitorCanaryStage(mineService: mineService, stageNavigator: stageNavigator)

  def "should short-circuit if canary registered but execution not explicitly canceled"() {
    given:
    def stage = new Stage<>(new Pipeline("orca"), "pipelineStage", [
      canary: [id: "canaryId"]
    ])

    when:
    stage.execution.canceled = false
    def result = monitorCanaryStage.cancel(stage)

    then:
    result == null
    0 * mineService.cancelCanary(_, _)
  }

  def "should propagate cancel upstream if canary registered and execution explicitly canceled"() {
    given:
    def canaryStage = new Stage<Pipeline>()
    def canaryStageBuilder = Mock(CanaryStage)
    def stage = new Stage<Pipeline>(new Pipeline("orca"), "pipelineStage", [
      canary: [id: "canaryId"]
    ])

    and:
    stageNavigator.ancestors(_) >> [new StageNavigator.Result(canaryStage, canaryStageBuilder)]

    when:
    stage.execution.canceled = true
    def result = monitorCanaryStage.cancel(stage)

    then:
    result.details.canary == [canceled: true]
    1 * canaryStageBuilder.cancel(canaryStage) >> {
      new CancellableStage.Result(stage, [:])
    }
    1 * mineService.cancelCanary("canaryId", _) >> { return [canceled: true] }
  }

  def "should raise exception if no upstream canary stage found"() {
    def stage = new Stage<>(new Pipeline("orca"), "pipelineStage", [
      canary: [id: "canaryId"]
    ])

    when:
    stage.execution.canceled = true
    monitorCanaryStage.cancel(stage)

    then:
    thrown(IllegalStateException)
  }
}
