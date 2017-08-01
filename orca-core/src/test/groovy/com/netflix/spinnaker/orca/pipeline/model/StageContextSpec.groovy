/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.model

import spock.lang.Specification
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionEngine.v3
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import static java.lang.System.currentTimeMillis

class StageContextSpec extends Specification {

  def pipeline = pipeline {
  }
  .stage {
    refId = "1"
    context.foo = "root-foo"
    context.bar = "root-bar"
    context.baz = "root-baz"
    context.qux = "root-qux"
  }
  .stage {
    refId = "2"
    requisiteStageRefIds = ["1"]
    context.foo = "ancestor-foo"
    context.bar = "ancestor-bar"
    context.baz = "ancestor-baz"
  }
  .stage {
    refId = "3"
    requisiteStageRefIds = ["2"]
    context.foo = "parent-foo"
    context.bar = "parent-bar"
  }
  .stage {
    refId = "3>1"
    parentStageId = execution.stageByRef("3").id
    syntheticStageOwner = STAGE_AFTER
    context.foo = "child-foo"
  }
  .stage {
    refId = "4"
    context.covfefe = "unrelated-covfefe"
  }
  .stage {
    refId = "3>2"
    requisiteStageRefIds = ["3>1"]
    parentStageId = execution.stageByRef("3").id
    syntheticStageOwner = STAGE_AFTER
    context.covfefe = "downstream-covfefe"
  }
  .build()

  def "a stage's own context takes priority"() {
    expect:
    pipeline.stageByRef("3>1").context.foo == "child-foo"
  }

  def "parent takes priority over ancestor"() {
    expect:
    pipeline.stageByRef("3>1").context.bar == "parent-bar"
  }

  def "missing keys resolve in ancestor stage context"() {
    expect:
    pipeline.stageByRef("3>1").context.baz == "ancestor-baz"
  }

  def "can chain back up ancestors"() {
    expect:
    pipeline.stageByRef("3>1").context.qux == "root-qux"
  }

  def "can't resolve keys from unrelated or downstream stages"() {
    expect:
    pipeline.stageByRef("3>1").context.covfefe == null
  }

  private static PipelineInit pipeline(
    @DelegatesTo(PipelineInit) Closure init = {}) {
    def pipeline = new Pipeline()
    pipeline.id = UUID.randomUUID().toString()
    pipeline.executionEngine = v3
    pipeline.buildTime = currentTimeMillis()

    def builder = new PipelineInit(pipeline)
    init.delegate = builder
    init()

    return builder
  }

  private static class PipelineInit {
    final @Delegate Pipeline pipeline

    PipelineInit(Pipeline pipeline) {
      this.pipeline = pipeline
    }

    PipelineInit stage(@DelegatesTo(Stage) Closure init) {
      def stage = new Stage<Pipeline>()
      stage.execution = pipeline
      pipeline.stages.add(stage)

      init.delegate = stage
      init()

      return this
    }

    Pipeline build() {
      return pipeline
    }
  }
}
