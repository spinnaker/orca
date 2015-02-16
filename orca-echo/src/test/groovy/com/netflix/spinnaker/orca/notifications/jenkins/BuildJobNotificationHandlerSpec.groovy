/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.notifications.jenkins

import groovy.json.JsonSlurper
import com.google.common.collect.ImmutableMap
import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.DiscoveryClient
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.notifications.PipelineIndexer
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import spock.lang.Specification
import spock.lang.Subject

class BuildJobNotificationHandlerSpec extends Specification {

  def pipeline1 = [
      name    : "pipeline1",
      triggers: [[type  : "jenkins",
                  job   : "SPINNAKER-package-pond",
                  master: "master1"]],
      stages  : [[type: "bake"],
                 [type: "deploy", cluster: [name: "bar"]]]
  ]

  def pipeline2 = [
      name    : "pipeline2",
      triggers: [[type  : "jenkins",
                  job   : "SPINNAKER-package-pond",
                  master: "master1"]],
      stages  : [[type: "bake"],
                 [type: "deploy", cluster: [name: "foo"]]]
  ]

  def pipeline3 = [
      name    : "pipeline3",
      triggers: [[type  : "jenkins",
                  job   : "SPINNAKER-package-pond",
                  master: "master2"]],
      stages  : []
  ]

  def discoveryClient = Stub(DiscoveryClient)
  def pipelineIndexer = Stub(PipelineIndexer)
  def pipelineStarter = Mock(PipelineStarter)

  private BuildJobNotificationHandler handlerFor(Map input) {
    def handler = new BuildJobNotificationHandler(input)
    handler.discoveryClient = discoveryClient
    handler.pipelineStarter = pipelineStarter
    handler.objectMapper = new OrcaObjectMapper()
    handler.pipelineIndexer = pipelineIndexer
    return handler
  }

  void setup() {
    discoveryClient.getInstanceRemoteStatus() >> InstanceInfo.InstanceStatus.UP
  }

  void "should pick up stages subsequent to build job completing"() {
    given:
    @Subject handler = handlerFor(input)

    and:
    pipelineIndexer.getPipelines() >> ImmutableMap.of(
        new Trigger(input.master, input.name), [pipeline1]
    )

    when:
    handler.run()

    then:
    1 * pipelineStarter.start(_) >> { json ->
      def config = new JsonSlurper().parseText(json) as Map
      assert config.stages.size() == 2
      assert config.stages[0].type == "bake"
      assert config.stages[1].type == "deploy"
      assert config.trigger.type == "jenkins"
      assert config.trigger.buildInfo == input
      def pipeline = new Pipeline()
      pipeline.id = "1"
      return pipeline
    }

    where:
    input = [
        name     : "SPINNAKER-package-pond",
        master   : "master1",
        lastBuild: [result: "SUCCESS", building: false]
    ]
  }

  void "should only trigger targets from the same master "() {
    given:
    @Subject handler = handlerFor(input)

    and:
    pipelineIndexer.getPipelines() >> ImmutableMap.of(
        new Trigger(input.master, input.name), [pipeline1],
        new Trigger("master2", input.name), [pipeline3]
    )

    expect:
    pipeline1.triggers.master != pipeline3.triggers.master

    when:
    handler.run()

    then:
    1 * pipelineStarter.start({
      new JsonSlurper().parseText(it).trigger.master == master
    }) >> new Pipeline()

    where:
    master    | _
    "master1" | _

    input = [
        name     : "SPINNAKER-package-pond",
        master   : master,
        lastBuild: [result: "SUCCESS", building: false]
    ]
  }

  void "builds that have failed should not kick off the trigger"() {
    given:
    @Subject handler = handlerFor(input)

    and:
    pipelineIndexer.getPipelines() >> ImmutableMap.of(
        new Trigger(input.master, input.name), [pipeline3]
    )

    when:
    handler.run()

    then:
    0 * pipelineStarter.start(*_)

    where:
    input = [
        name     : "SPINNAKER-package-pond",
        master   : "master2",
        lastBuild: [result: "FAILURE", building: false]
    ]
  }

  void "in progress builds should not kick off the trigger"() {
    given:
    @Subject handler = handlerFor(input)

    and:
    pipelineIndexer.getPipelines() >> ImmutableMap.of(
        new Trigger(input.master, input.name), [pipeline3]
    )

    when:
    handler.run()

    then:
    0 * pipelineStarter.start(*_)

    where:
    input = [
        name     : "SPINNAKER-package-pond",
        master   : "master2",
        lastBuild: [result: "SUCCESS", building: true]
    ]
  }
}
