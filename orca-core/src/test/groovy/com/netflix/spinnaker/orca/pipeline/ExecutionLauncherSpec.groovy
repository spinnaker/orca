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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.appinfo.InstanceInfo
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable
import static com.netflix.spinnaker.orca.jackson.OrcaJackson.TYPE_IDENTIFIER
import static com.netflix.spinnaker.orca.pipeline.ExecutionLauncher.ExecutionRunner
import static com.netflix.spinnaker.orca.pipeline.ExecutionLauncher.NoSuchStageDefinitionBuilder
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.TaskDefinition

abstract class ExecutionLauncherSpec<T extends Execution, L extends ExecutionLauncher<T>> extends Specification {

  abstract L create(StageDefinitionBuilder... stageDefBuilders)

  @Shared def objectMapper = new ObjectMapper()
  @Shared def instanceInfo = InstanceInfo.Builder.newBuilder().setAppName("orca").setHostName("localhost").build()
  def runner = Mock(ExecutionRunner)

  def "throws an exception if there's no builder for a stage type"() {
    given:
    @Subject def launcher = create()

    and:
    def pipeline = new BlockingVariable<Pipeline>()
    runner.start(_) >> { Pipeline p -> pipeline.set(p) }

    when:
    launcher.start(json)

    then:
    thrown NoSuchStageDefinitionBuilder

    where:
    stageType = "foo"
    config = [
      id    : "whatever",
      stages: [
        [(TYPE_IDENTIFIER): "PipelineStage", type: stageType]
      ]
    ]
    json = objectMapper.writeValueAsString(config)
  }

  def "builds tasks for each stage"() {
    given:
    def stageDefBuilder = Stub(StageDefinitionBuilder) {
      getType() >> stageType
      taskGraph() >> [new TaskDefinition("1", "1", Task)]
    }
    @Subject def launcher = create(stageDefBuilder)

    and:
    def pipeline = new BlockingVariable<Pipeline>()
    runner.start(_) >> { Pipeline p -> pipeline.set(p) }

    when:
    launcher.start(json)

    then:
    with(pipeline.get().stages) {
      tasks.id.flatten() == ["1"]
    }

    where:
    stageType = "foo"
    config = [
      id    : "whatever",
      stages: [
        [(TYPE_IDENTIFIER): "PipelineStage", type: stageType]
      ]
    ]
    json = objectMapper.writeValueAsString(config)
  }

  def "builds each pre-stage"() {
    given:
    def stageDefBuilders = stageTypes.collect { stageType ->
      def preStage1 = new PipelineStage(null, "${stageType}_pre1")
      def preStage2 = new PipelineStage(null, "${stageType}_pre2")
      [
        Stub(StageDefinitionBuilder) {
          getType() >> stageType
          taskGraph() >> [new TaskDefinition("1", "1", Task)]
          preStages() >> [preStage1, preStage2]
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
    @Subject def launcher = create(*stageDefBuilders)

    and:
    def pipeline = new BlockingVariable<Pipeline>()
    runner.start(_) >> { Pipeline p -> pipeline.set(p) }

    when:
    launcher.start(json)

    then:
    pipeline.get().stages.type == stageTypes.collect { stageType ->
      ["${stageType}_pre1", "${stageType}_pre2", stageType]
    }.flatten()

    where:
    stageTypes = ["foo", "bar"]
    config = [
      id    : "whatever",
      stages: stageTypes.collect {
        [(TYPE_IDENTIFIER): "PipelineStage", type: it]
      }
    ]
    json = objectMapper.writeValueAsString(config)
  }

  def "builds each post-stage"() {
    given:
    def stageDefBuilders = stageTypes.collect { stageType ->
      def postStage1 = new PipelineStage(null, "${stageType}_post1")
      def postStage2 = new PipelineStage(null, "${stageType}_post2")
      [
        Stub(StageDefinitionBuilder) {
          getType() >> stageType
          taskGraph() >> [new TaskDefinition("1", "1", Task)]
          postStages() >> [postStage1, postStage2]
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
    @Subject def launcher = create(*stageDefBuilders)

    and:
    def pipeline = new BlockingVariable<Pipeline>()
    runner.start(_) >> { Pipeline p -> pipeline.set(p) }

    when:
    launcher.start(json)

    then:
    pipeline.get().stages.type == stageTypes.collect { stageType ->
      [stageType, "${stageType}_post1", "${stageType}_post2"]
    }.flatten()

    where:
    stageTypes = ["foo", "bar"]
    config = [
      id    : "whatever",
      stages: stageTypes.collect {
        [(TYPE_IDENTIFIER): "PipelineStage", type: it]
      }
    ]
    json = objectMapper.writeValueAsString(config)
  }

  def "builds tasks for pre and post-stages"() {
    given:
    def preStage = new PipelineStage(null, "${stageType}_pre")
    def postStage = new PipelineStage(null, "${stageType}_post")
    def stageDefBuilder = Stub(StageDefinitionBuilder) {
      getType() >> stageType
      taskGraph() >> [new TaskDefinition("1", "${stageType}_1", Task)]
      preStages() >> [preStage]
      postStages() >> [postStage]
    }
    def preStageDefBuilder = Stub(StageDefinitionBuilder) {
      getType() >> "${stageType}_pre"
      taskGraph() >> [new TaskDefinition("1", "${stageType}_pre_1", Task)]
    }
    def postStageDefBuilder = Stub(StageDefinitionBuilder) {
      getType() >> "${stageType}_post"
      taskGraph() >> [new TaskDefinition("1", "${stageType}_post_1", Task)]
    }
    @Subject def launcher = create(stageDefBuilder, preStageDefBuilder, postStageDefBuilder)

    and:
    def pipeline = new BlockingVariable<Pipeline>()
    runner.start(_) >> { Pipeline p -> pipeline.set(p) }

    when:
    launcher.start(json)

    then:
    with(pipeline.get().stages) {
      tasks.name.flatten() == ["${stageType}_pre_1", "${stageType}_1", "${stageType}_post_1"]
    }

    where:
    stageType = "foo"
    config = [
      id    : "whatever",
      stages: [
        [(TYPE_IDENTIFIER): "PipelineStage", type: stageType]
      ]
    ]
    json = objectMapper.writeValueAsString(config)
  }
}

class PipelineLauncherSpec extends ExecutionLauncherSpec<Pipeline, PipelineLauncher> {

  def startTracker = Stub(PipelineStartTracker)

  @Override
  PipelineLauncher create(StageDefinitionBuilder... stageDefBuilders) {
    return new PipelineLauncher(objectMapper, instanceInfo, runner, stageDefBuilders.toList(), startTracker)
  }

  def "can autowire pipeline launcher with optional dependencies"() {
    given:
    def context = new AnnotationConfigApplicationContext()
    context.with {
      beanFactory.with {
        registerSingleton("objectMapper", objectMapper)
        registerSingleton("executionRunner", runner)
        registerSingleton("instanceInfo", instanceInfo)
        registerSingleton("whateverStageDefBuilder", new StageDefinitionBuilder() {
          @Override
          String getType() {
            return "whatever"
          }
        })
        registerSingleton("pipelineStartTracker", startTracker)
      }
      register(PipelineLauncher)
      refresh()
    }

    expect:
    context.getBean(PipelineLauncher)
  }

  def "can autowire pipeline launcher without optional dependencies"() {
    given:
    def context = new AnnotationConfigApplicationContext()
    context.with {
      beanFactory.with {
        registerSingleton("objectMapper", objectMapper)
        registerSingleton("executionRunner", runner)
        registerSingleton("instanceInfo", instanceInfo)
        registerSingleton("whateverStageDefBuilder", new StageDefinitionBuilder() {
          @Override
          String getType() {
            return "whatever"
          }
        })
      }
      register(PipelineLauncher)
      refresh()
    }

    expect:
    context.getBean(PipelineLauncher)
  }

  def "does not start pipeline if it should be queued"() {
    given:
    startTracker.queueIfNotStarted(*_) >> true

    and:
    @Subject def launcher = create()

    when:
    launcher.start(json)

    then:
    0 * runner.start(_)

    where:
    config = [pipelineConfigId: "whatever", stages: []]
    json = objectMapper.writeValueAsString(config)
  }

  def "does not start pipeline if it does not have a pipeline config id"() {
    given:
    startTracker.queueIfNotStarted(*_) >> false

    and:
    @Subject def launcher = create()

    when:
    launcher.start(json)

    then:
    0 * runner.start(_)

    where:
    config = [stages: []]
    json = objectMapper.writeValueAsString(config)
  }

  def "starts pipeline if it should not be queued"() {
    given:
    startTracker.queueIfNotStarted(*_) >> false

    and:
    @Subject def launcher = create()

    when:
    launcher.start(json)

    then:
    1 * runner.start(_)

    where:
    config = [id: "whatever", stages: []]
    json = objectMapper.writeValueAsString(config)
  }
}
