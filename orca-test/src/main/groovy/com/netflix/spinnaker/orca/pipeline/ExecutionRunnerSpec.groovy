/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.TaskDefinition

abstract class ExecutionRunnerSpec<R extends ExecutionRunner> extends Specification {

  abstract R create(StageDefinitionBuilder... stageDefBuilders)

  def "throws an exception if there's no builder for a stage type"() {
    given:
    @Subject def runner = create()

    when:
    runner.start(execution)

    then:
    thrown ExecutionRunner.NoSuchStageDefinitionBuilder

    where:
    stageType = "foo"
    execution = Pipeline.builder().withStage(stageType).build()
  }

  def "builds tasks for each stage"() {
    given:
    def stageDefBuilder = Stub(StageDefinitionBuilder) {
      getType() >> stageType
      taskGraph(_) >> [new TaskDefinition("1", Task)]
    }
    @Subject def runner = create(stageDefBuilder)

    when:
    runner.start(execution)

    then:
    with(execution.stages) {
      tasks.id.flatten() == ["1"]
    }

    where:
    stageType = "foo"
    execution = Pipeline.builder().withStage(stageType).build()
  }

  @Unroll
  def "marks start and end of each stage when there are #numTasks tasks"() {
    given:
    def stageDefBuilder = Stub(StageDefinitionBuilder) {
      getType() >> stageType
      taskGraph(_) >> (1..numTasks).collect { i -> new TaskDefinition("$i", Task) }
    }
    @Subject def runner = create(stageDefBuilder)

    when:
    runner.start(execution)

    then:
    with(execution.stages.first()) {
      tasks.head().stageStart
      tasks.tail().every { !it.stageStart }
      tasks.reverse().head().stageEnd
      tasks.reverse().tail().every { !it.stageEnd }
    }

    where:
    stageType = "foo"
    execution = Pipeline.builder().withStage(stageType).build()
    numTasks << [1, 2, 3]
  }

  def "builds each pre-stage"() {
    given:
    def stageDefBuilders = stageTypes.collect { stageType ->
      def preStage1 = before(new PipelineStage(execution, "${stageType}_pre1"))
      def preStage2 = before(new PipelineStage(execution, "${stageType}_pre2"))
      [
        Stub(StageDefinitionBuilder) {
          getType() >> stageType
          taskGraph() >> [new TaskDefinition("${stageType}_1", Task)]
          aroundStages(_) >> [preStage1, preStage2]
        },
        Stub(StageDefinitionBuilder) {
          getType() >> "${stageType}_pre1"
        },
        Stub(StageDefinitionBuilder) {
          getType() >> "${stageType}_pre2"
        }
      ]
    }
    .flatten()
    @Subject def runner = create(*stageDefBuilders)

    when:
    runner.start(execution)

    then:
    execution.stages.type == stageTypes.collect { stageType ->
      ["${stageType}_pre2", "${stageType}_pre1", stageType]
    }.flatten()

    where:
    stageTypes = ["foo", "bar"]
    execution = Pipeline.builder().withStages(*stageTypes).build()
  }

  def "builds each post-stage"() {
    given:
    def stageDefBuilders = stageTypes.collect { stageType ->
      def postStage1 = after(new PipelineStage(execution, "${stageType}_post1"))
      def postStage2 = after(new PipelineStage(execution, "${stageType}_post2"))
      [
        Stub(StageDefinitionBuilder) {
          getType() >> stageType
          taskGraph() >> [new TaskDefinition("${stageType}_1", Task)]
          aroundStages(_) >> [postStage1, postStage2]
        },
        Stub(StageDefinitionBuilder) {
          getType() >> "${stageType}_post1"
        },
        Stub(StageDefinitionBuilder) {
          getType() >> "${stageType}_post2"
        }
      ]
    }
    .flatten()
    @Subject def runner = create(*stageDefBuilders)

    when:
    runner.start(execution)

    then:
    execution.stages.type == stageTypes.collect { stageType ->
      [stageType, "${stageType}_post1", "${stageType}_post2"]
    }.flatten()

    where:
    stageTypes = ["foo", "bar"]
    execution = Pipeline.builder().withStages(*stageTypes).build()
  }

  def "builds tasks for pre and post-stages"() {
    given:
    def preStage = before(new PipelineStage(execution, "${stageType}_pre"))
    def postStage = after(new PipelineStage(execution, "${stageType}_post"))
    def stageDefBuilder = Stub(StageDefinitionBuilder) {
      getType() >> stageType
      taskGraph(_) >> [new TaskDefinition("${stageType}_1", Task)]
      aroundStages(_) >> [preStage, postStage]
    }
    def preStageDefBuilder = Stub(StageDefinitionBuilder) {
      getType() >> "${stageType}_pre"
      taskGraph(_) >> [new TaskDefinition("${stageType}_pre_1", Task)]
    }
    def postStageDefBuilder = Stub(StageDefinitionBuilder) {
      getType() >> "${stageType}_post"
      taskGraph(_) >> [new TaskDefinition("${stageType}_post_1", Task)]
    }
    @Subject def runner = create(stageDefBuilder, preStageDefBuilder, postStageDefBuilder)

    when:
    runner.start(execution)

    then:
    with(execution.stages) {
      tasks.name.flatten() == ["${stageType}_pre_1", "${stageType}_1", "${stageType}_post_1"]
    }

    where:
    stageType = "foo"
    execution = Pipeline.builder().withStages(stageType).build()
  }

  static PipelineStage before(PipelineStage stage) {
    stage.syntheticStageOwner = SyntheticStageOwner.STAGE_BEFORE
    return stage
  }

  static PipelineStage after(PipelineStage stage) {
    stage.syntheticStageOwner = SyntheticStageOwner.STAGE_AFTER
    return stage
  }
}

class NoOpExecutionRunnerSpec extends ExecutionRunnerSpec<ExecutionRunnerSupport> {
  @Override
  ExecutionRunnerSupport create(StageDefinitionBuilder... stageDefBuilders) {
    return new ExecutionRunnerSupport(stageDefBuilders.toList()) {
      // do nothing
    }
  }
}
