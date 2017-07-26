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
import com.netflix.spinnaker.orca.pipeline.PipelineLauncher
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.slf4j.MDC
import org.springframework.context.support.StaticApplicationContext
import spock.lang.Specification
import spock.lang.Subject

class DependentPipelineStarterSpec extends Specification {

  @Subject
  DependentPipelineStarter dependentPipelineStarter

  ObjectMapper mapper = new ObjectMapper()

  def "should propagate credentials from explicit pipeline invocation ('run pipeline' stage)"() {
    setup:
    def triggeredPipelineConfig = [name: "triggered", id: "triggered"]
    def parentPipeline = new Pipeline(
      name: "parent",
      authentication: new Execution.AuthenticationDetails(
        user: "parentUser",
        allowedAccounts: ["acct1,acct2"]
      )
    )
    def gotMDC = [:]
    def executionLauncher = Stub(PipelineLauncher) {
      start(*_) >> {
        gotMDC.putAll(MDC.copyOfContextMap)
        def p = mapper.readValue(it[0], Map)
        return Pipeline.builder().withName(p.name).withId(p.name).withTrigger(p.trigger).build()
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
      contextParameterProcessor: new ContextParameterProcessor()
    )

    when:
    MDC.setContextMap(mdc)
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null /*user*/,
      parentPipeline, [:],
      "parent"
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
    def parentPipeline = new Pipeline(
      name: "parent",
      authentication: new Execution.AuthenticationDetails(
        user: "parentUser",
        allowedAccounts: ["acct1,acct2"]
      )
    )
    def gotMDC = [:]
    def executionLauncher = Stub(PipelineLauncher) {
      start(*_) >> {
        gotMDC.putAll(MDC.copyOfContextMap)
        def p = mapper.readValue(it[0], Map)
        return Pipeline.builder().withName(p.name).withId(p.name).withTrigger(p.trigger).build()
      }
    }
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
      objectMapper: mapper,
      applicationContext: applicationContext,
      contextParameterProcessor: new ContextParameterProcessor()
    )

    when:
    MDC.clear()
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null /*user*/,
      parentPipeline,
      [:],
      "parent"
    )
    MDC.clear()

    then:
    result?.name == "triggered"
    gotMDC["X-SPINNAKER-USER"] == "parentUser"
    gotMDC["X-SPINNAKER-ACCOUNTS"] == "acct1,acct2"
  }
}
