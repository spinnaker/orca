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


package com.netflix.spinnaker.orca.pipeline.util

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import javax.annotation.Nonnull

class StageNavigatorSpec extends Specification {

  @Shared
  def stageBuilders = [
    new ExampleStageBuilder("One"),
    new ExampleStageBuilder("Two"),
    new ExampleStageBuilder("Three"),
    new ExampleStageBuilder("Four")
  ]

  @Subject
  def stageNavigator = new StageNavigator(stageBuilders)

  def execution = PipelineExecutionImpl.newPipeline("orca")

  def "traverses up the synthetic stage hierarchy"() {
    given:
    def stage1 = buildStage("One")
    def stage2 = buildStage("Two")
    def stage3 = buildStage("Three")

    stage1.parentStageId = stage2.id
    stage2.parentStageId = stage3.id

    expect:
    stage1.ancestors().type == ["One", "Two", "Three"]
  }

  def "traverses up the synthetic stage hierarchy with StageNavigator"() {
    given:
    def stage1 = buildStage("One")
    def stage2 = buildStage("Two")
    def stage3 = buildStage("Three")

    stage1.parentStageId = stage2.id
    stage2.parentStageId = stage3.id

    expect:
    with(stageNavigator.ancestors(stage1)) {
      stage.type == ["One", "Two", "Three"]
      stageBuilder.type == ["One", "Two", "Three"]
    }
  }

  def "traverses up the refId stage hierarchy"() {
    given:
    def stage1 = buildStage("One")
    def stage2 = buildStage("Two")
    def stage4 = buildStage("Four")
    def stage3 = buildStage("Three")

    stage1.refId = "1"
    stage2.refId = "2"
    stage3.refId = "3"
    stage4.refId = "4"

    stage1.requisiteStageRefIds = ["2"]
    stage2.requisiteStageRefIds = ["3", "4"]

    expect:
    // order is dependent on the order of a stage within `execution.stages`
    stage1.ancestors().type == ["One", "Two", "Four", "Three"]
  }

  def "traverses up the refId stage hierarchy with StageNavigator"() {
    given:
    def stage1 = buildStage("One")
    def stage2 = buildStage("Two")
    def stage4 = buildStage("Four")
    def stage3 = buildStage("Three")

    stage1.refId = "1"
    stage2.refId = "2"
    stage3.refId = "3"
    stage4.refId = "4"

    stage1.requisiteStageRefIds = ["2"]
    stage2.requisiteStageRefIds = ["3", "4"]

    expect:
    // order is dependent on the order of a stage within `execution.stages`
    with(stageNavigator.ancestors(stage1)) {
      stage.type == ["One", "Two", "Four", "Three"]
      stageBuilder.type == ["One", "Two", "Four", "Three"]
    }
  }

  def "traverses up both synthetic and refId stage hierarchies"() {
    given:
    def stage1 = buildStage("One")
    def stage2 = buildStage("Two")
    def stage4 = buildStage("Four")
    def stage3 = buildStage("Three")

    stage1.refId = "1"
    stage2.refId = "2"
    stage3.refId = "3"
    stage4.refId = "4"

    stage1.requisiteStageRefIds = ["2"]
    stage2.parentStageId = stage3.id
    stage3.requisiteStageRefIds = ["4"]

    expect:
    // order is dependent on the order of a stage within `execution.stages`
    stage1.ancestors().type == ["One", "Two", "Three", "Four"]
  }

  def "traverses up both synthetic and refId stage hierarchies with StageNavigator"() {
    given:
    def stage1 = buildStage("One")
    def stage2 = buildStage("Two")
    def stage4 = buildStage("Four")
    def stage3 = buildStage("Three")

    stage1.refId = "1"
    stage2.refId = "2"
    stage3.refId = "3"
    stage4.refId = "4"

    stage1.requisiteStageRefIds = ["2"]
    stage2.parentStageId = stage3.id
    stage3.requisiteStageRefIds = ["4"]

    expect:
    // order is dependent on the order of a stage within `execution.stages`
    with(stageNavigator.ancestors(stage1)) {
      stage.type == ["One", "Two", "Three", "Four"]
      stageBuilder.type == ["One", "Two", "Three", "Four"]
    }
  }

  private StageExecutionImpl buildStage(String type) {
    def pipelineStage = new StageExecutionImpl(execution, type)

    execution.stages << pipelineStage

    return pipelineStage
  }

  static class ExampleStageBuilder implements StageDefinitionBuilder {
    private final String type

    ExampleStageBuilder(String type) {
      this.type = type
    }

    @Nonnull
    @Override
    String getType() {
      return type
    }
  }

}
