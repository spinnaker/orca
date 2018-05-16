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

package com.netflix.spinnaker.orca.front50

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Trigger
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.slf4j.MDC
import org.springframework.context.support.StaticApplicationContext
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class DependentPipelineStarterSpec extends Specification {

  @Subject
  DependentPipelineStarter dependentPipelineStarter

  ObjectMapper mapper = OrcaObjectMapper.newInstance()
  ExecutionRepository executionRepository = Mock(ExecutionRepository)
  ArtifactResolver artifactResolver = Spy(ArtifactResolver, constructorArgs: [mapper, executionRepository])

  def "should propagate credentials from explicit pipeline invocation ('run pipeline' stage)"() {
    setup:
    def triggeredPipelineConfig = [name: "triggered", id: "triggered"]
    def parentPipeline = pipeline {
      name = "parent"
      authentication = new Execution.AuthenticationDetails("parentUser", "acct1", "acct2")
    }
    def gotMDC = [:]
    def executionLauncher = Stub(ExecutionLauncher) {
      start(*_) >> {
        gotMDC.putAll(MDC.copyOfContextMap)
        def p = mapper.readValue(it[1], Map)
        return pipeline {
          name = p.name
          id = p.name
          trigger = mapper.convertValue(p.trigger, Trigger)
        }
      }
    }
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    def mdc = [
      (AuthenticatedRequest.SPINNAKER_USER)    : "myMDCUser",
      (AuthenticatedRequest.SPINNAKER_ACCOUNTS): "acct3,acct4"
    ]
    dependentPipelineStarter = new DependentPipelineStarter(
      objectMapper: mapper,
      applicationContext: applicationContext,
      contextParameterProcessor: new ContextParameterProcessor(),
      artifactResolver: artifactResolver
    )

    when:
    MDC.setContextMap(mdc)
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null /*user*/,
      parentPipeline, [:],
      null
    )
    MDC.clear()

    then:
    result?.name == "triggered"
    gotMDC["X-SPINNAKER-USER"] == "myMDCUser"
    gotMDC["X-SPINNAKER-ACCOUNTS"] == "acct3,acct4"
  }

  def "should propagate credentials from implicit pipeline invocation (listener for pipeline completion)"() {
    setup:
    def triggeredPipelineConfig = [name: "triggered", id: "triggered"]
    def parentPipeline = pipeline {
      name = "parent"
      authentication = new Execution.AuthenticationDetails("parentUser", "acct1", "acct2")
    }
    def gotMDC = [:]
    def executionLauncher = Stub(ExecutionLauncher) {
      start(*_) >> {
        gotMDC.putAll(MDC.copyOfContextMap)
        def p = mapper.readValue(it[1], Map)
        return pipeline {
          name = p.name
          id = p.name
          trigger = mapper.convertValue(p.trigger, Trigger)
        }
      }
    }
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
      objectMapper: mapper,
      applicationContext: applicationContext,
      contextParameterProcessor: new ContextParameterProcessor(),
      artifactResolver: artifactResolver
    )

    when:
    MDC.clear()
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null /*user*/,
      parentPipeline,
      [:],
      null
    )
    MDC.clear()

    then:
    result?.name == "triggered"
    gotMDC["X-SPINNAKER-USER"] == "parentUser"
    gotMDC["X-SPINNAKER-ACCOUNTS"] == "acct1,acct2"
  }

  def "should propagate dry run flag"() {
    given:
    def triggeredPipelineConfig = [name: "triggered", id: "triggered"]
    def parentPipeline = pipeline {
      name = "parent"
      trigger = new DefaultTrigger("manual", null, "fzlem@netflix.com", [:], [], [], false, true)
      authentication = new Execution.AuthenticationDetails("parentUser", "acct1", "acct2")
    }
    def executionLauncher = Mock(ExecutionLauncher)
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
      objectMapper: mapper,
      applicationContext: applicationContext,
      contextParameterProcessor: new ContextParameterProcessor(),
      artifactResolver: artifactResolver
    )

    and:
    executionLauncher.start(*_) >> {
      def p = mapper.readValue(it[1], Map)
      return pipeline {
        name = p.name
        id = p.name
        trigger = mapper.convertValue(p.trigger, Trigger)
      }
    }

    when:
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null /*user*/,
      parentPipeline,
      [:],
      null
    )

    then:
    result.trigger.dryRun
  }

  def "should find artifacts from triggering pipeline"() {
    given:
    def triggeredPipelineConfig = [
      name             : "triggered",
      id               : "triggered",
      expectedArtifacts: [[
                            matchArtifact: [
                              kind: "gcs",
                              name: "gs://test/file.yaml",
                              type: "gcs/object"
                            ]
                          ]]
    ];
    Artifact testArtifact = new Artifact(
      type: "gcs/object",
      name: "gs://test/file.yaml"
    )
    def parentPipeline = pipeline {
      name = "parent"
      trigger = new DefaultTrigger("webhook", null, "test", [:], [testArtifact]);
      authentication = new Execution.AuthenticationDetails("parentUser", "acct1", "acct2")
    }
    def executionLauncher = Mock(ExecutionLauncher)
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
      objectMapper: mapper,
      applicationContext: applicationContext,
      contextParameterProcessor: new ContextParameterProcessor(),
      artifactResolver: artifactResolver
    )

    and:
    executionLauncher.start(*_) >> {
      def p = mapper.readValue(it[1], Map)
      return pipeline {
        name = p.name
        id = p.name
        trigger = mapper.convertValue(p.trigger, Trigger)
      }
    }
    artifactResolver.getArtifactsForPipelineId(*_) >> {
      return new ArrayList<Artifact>();
    }

    when:
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null,
      parentPipeline,
      [:],
      null
    )

    then:
    result.trigger.artifacts.size() == 1
    result.trigger.artifacts*.name == ["gs://test/file.yaml"]
  }

  def "should find artifacts from parent pipeline stage"() {
    given:
    def triggeredPipelineConfig = [
      name             : "triggered",
      id               : "triggered",
      expectedArtifacts: [[
                            matchArtifact: [
                              kind: "gcs",
                              name: "gs://test/file.yaml",
                              type: "gcs/object"
                            ]
                          ]]
    ];
    Artifact testArtifact = new Artifact(
      type: "gcs/object",
      name: "gs://test/file.yaml"
    )
    def parentPipeline = pipeline {
      name = "parent"
      trigger = new DefaultTrigger("webhook", null, "test")
      authentication = new Execution.AuthenticationDetails("parentUser", "acct1", "acct2")
      stage {
        id = "stage1"
        refId = "1"
        outputs.artifacts = [testArtifact]
      }
      stage {
        id = "stage2"
        refId = "2"
        requisiteStageRefIds = ["1"]
      }
    }
    def executionLauncher = Mock(ExecutionLauncher)
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
      objectMapper: mapper,
      applicationContext: applicationContext,
      contextParameterProcessor: new ContextParameterProcessor(),
      artifactResolver: artifactResolver
    )

    and:
    executionLauncher.start(*_) >> {
      def p = mapper.readValue(it[1], Map)
      return pipeline {
        name = p.name
        id = p.name
        trigger = mapper.convertValue(p.trigger, Trigger)
      }
    }
    artifactResolver.getArtifactsForPipelineId(*_) >> {
      return new ArrayList<Artifact>();
    }

    when:
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null,
      parentPipeline,
      [:],
      "stage1"
    )

    then:
    result.trigger.artifacts.size() == 1
    result.trigger.artifacts*.name == ["gs://test/file.yaml"]
  }

  def "should find artifacts from triggering pipeline without expected artifacts"() {
    given:
    def triggeredPipelineConfig = [
      name             : "triggered",
      id               : "triggered",
      expectedArtifacts: [[
                            matchArtifact: [
                              kind: "gcs",
                              name: "gs://test/file.yaml",
                              type: "gcs/object"
                            ]
                          ]]
    ]
    Artifact testArtifact1 = new Artifact(
      type: "gcs/object",
      name: "gs://test/file.yaml"
    )
    Artifact testArtifact2 = new Artifact(
      type: "docker/image",
      name: "gcr.io/project/image"
    )
    def parentPipeline = pipeline {
      name = "parent"
      trigger = new DefaultTrigger("webhook", null, "test", [:], [testArtifact1, testArtifact2])
      authentication = new Execution.AuthenticationDetails("parentUser", "acct1", "acct2")
    }
    def executionLauncher = Mock(ExecutionLauncher)
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
      objectMapper: mapper,
      applicationContext: applicationContext,
      contextParameterProcessor: new ContextParameterProcessor(),
      artifactResolver: artifactResolver
    )

    and:
    executionLauncher.start(*_) >> {
      def p = mapper.readValue(it[1], Map)
      return pipeline {
        name = p.name
        id = p.name
        trigger = mapper.convertValue(p.trigger, Trigger)
      }
    }
    artifactResolver.getArtifactsForPipelineId(*_) >> {
      return new ArrayList<Artifact>();
    }

    when:
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null,
      parentPipeline,
      [:],
      null
    )

    then:
    result.trigger.artifacts.size() == 2
    result.trigger.artifacts*.name.contains(testArtifact1.name)
    result.trigger.artifacts*.name.contains(testArtifact2.name)
    result.trigger.resolvedExpectedArtifacts.size() == 1
    result.trigger.resolvedExpectedArtifacts*.boundArtifact.name == [testArtifact1.name]
  }

  def "should resolve expressions in trigger"() {
    given:
    def triggeredPipelineConfig = [name: "triggered", id: "triggered", parameterConfig: [[name: 'a', default: '${2 == 2}']]]
    def parentPipeline = pipeline {
      name = "parent"
      trigger = new DefaultTrigger("manual", null, "fzlem@netflix.com", [:], [], [], false, true)
      authentication = new Execution.AuthenticationDetails("parentUser", "acct1", "acct2")
    }
    def executionLauncher = Mock(ExecutionLauncher)
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
      objectMapper: mapper,
      applicationContext: applicationContext,
      contextParameterProcessor: new ContextParameterProcessor(),
      artifactResolver: artifactResolver
    )

    and:
    executionLauncher.start(*_) >> {
      def p = mapper.readValue(it[1], Map)
      return pipeline {
        trigger = mapper.convertValue(p.trigger, Trigger)
      }
    }

    when:
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null /*user*/,
      parentPipeline,
      [:],
      null
    )

    then:
    result.trigger.parameters.a == true
  }
}
