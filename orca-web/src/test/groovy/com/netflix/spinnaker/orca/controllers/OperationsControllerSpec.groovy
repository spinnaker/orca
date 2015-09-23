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

package com.netflix.spinnaker.orca.controllers

import com.netflix.spinnaker.orca.mayo.MayoService
import org.springframework.mock.env.MockEnvironment

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

import com.netflix.spinnaker.orca.igor.IgorService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import groovy.json.JsonSlurper
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class OperationsControllerSpec extends Specification {

  def pipelineStarter = Mock(PipelineStarter)
  def igor = Stub(IgorService)
  def mayo = Stub(MayoService)
  def mapper = new OrcaObjectMapper()
  def env = new MockEnvironment()
  @Subject
    controller = new OperationsController(objectMapper: mapper, mayoService: mayo, pipelineStarter: pipelineStarter, igorService: igor, environment: env)

  @Unroll
  void '#endpoint accepts #contentType'() {
    given:
    def mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    when:
    def resp = mockMvc.perform(
      post(endpoint).contentType(contentType).content('{}')
    ).andReturn().response

    then:
    1 * pipelineStarter.start(_) >> pipeline

    and:
    resp.status == 200
    slurp(resp.contentAsString).ref == "/pipelines/$pipeline.id"

    where:
    contentType << [MediaType.APPLICATION_JSON, MediaType.valueOf('application/context+json')]
    endpoint = "/orchestrate"
    pipeline = new Pipeline(id: "1")
  }

  private Map slurp(String json) {
    new JsonSlurper().parseText(json)
  }

  def "invalid application name returns a bad request response"() {
    given:
    mayo.getPipelines('foo') >> { [] }

    when:
    controller.orchestrateById('foo', '123', [type: 'cron', user: 'Anonymous'])

    then:
    thrown(OperationsController.PipelineConfigNotFoundException)
  }

  def "invalid pipeline id returns a bad request response"() {
    given:
    mayo.getPipelines('foo') >> { [ [id: '456', name: 'bar'] ] }

    when:
    controller.orchestrateById('foo', '123', [type: 'cron', user: 'Anonymous'])

    then:
    thrown(OperationsController.PipelineConfigNotFoundException)
  }

  def "valid application and pipeline id should trigger the pipeline"() {
    given:
    mayo.getPipelines('foo') >> { [ [id: '123', name: 'bar'] ] }

    when:
    controller.orchestrateById('foo', '123', [type: 'cron', user: 'Anonymous'])

    then:
    noExceptionThrown()
    1 * pipelineStarter.start(_) >> { [id: '123', name: 'bar'] }
  }

  def "uses trigger details from pipeline if present"() {
    given:
    Pipeline startedPipeline = null
    pipelineStarter.start(_) >> { String json ->
      startedPipeline = mapper.readValue(json, Pipeline)
    }
    igor.getBuild(master, job, buildNumber) >> buildInfo

    when:
    controller.orchestrate(requestedPipeline, null)

    then:
    with(startedPipeline) {
      trigger.type == requestedPipeline.trigger.type
      trigger.master == master
      trigger.job == job
      trigger.buildNumber == buildNumber
      trigger.buildInfo == buildInfo
    }

    where:
    master = "master"
    job = "job"
    buildNumber = 1337
    requestedPipeline = [
      trigger: [
        type       : "jenkins",
        master     : master,
        job        : job,
        buildNumber: buildNumber
      ]
    ]
    buildInfo = [result: "SUCCESS"]
  }

  def "trigger user takes precidence over query parameter"() {
    given:
    Pipeline startedPipeline = null
    pipelineStarter.start(_) >> { String json ->
      startedPipeline = mapper.readValue(json, Pipeline)
    }
    igor.getBuild(master, job, buildNumber) >> buildInfo

    when:
    controller.orchestrate(requestedPipeline, queryUser)

    then:
    with(startedPipeline) {
      trigger.type == requestedPipeline.trigger.type
      trigger.master == master
      trigger.job == job
      trigger.buildNumber == buildNumber
      trigger.buildInfo == buildInfo
      trigger.user == expectedUser
    }

    where:
    triggerUser   | queryUser   | expectedUser
    null          | "fromQuery" | "fromQuery"
    null          | null        | "[anonymous]"
    "fromTrigger" | "fromQuery" | "fromTrigger"

    master = "master"
    job = "job"
    buildNumber = 1337
    requestedPipeline = [
      trigger: [
        type       : "jenkins",
        master     : master,
        job        : job,
        buildNumber: buildNumber,
        user       : triggerUser
      ]
    ]
    buildInfo = [result: "SUCCESS"]

  }

  def "gets properties file from igor if specified in pipeline"() {
    given:
    Pipeline startedPipeline = null
    pipelineStarter.start(_) >> { String json ->
      startedPipeline = mapper.readValue(json, Pipeline)
    }
    igor.getBuild(master, job, buildNumber) >> [result: "SUCCESS"]
    igor.getPropertyFile(master, job, buildNumber, propertyFile) >> propertyFileContent

    when:
    controller.orchestrate(requestedPipeline, null)

    then:
    with(startedPipeline) {
      trigger.propertyFile == propertyFile
      trigger.properties == propertyFileContent
    }

    where:
    master = "qs-master"
    job = "qs-job"
    buildNumber = 1337
    propertyFile = "foo.properties"
    requestedPipeline = [
      trigger: [
        type        : "jenkins",
        master      : master,
        job         : job,
        buildNumber : buildNumber,
        propertyFile: propertyFile
      ]
    ]
    propertyFileContent = [foo: "bar"]
  }

  def "context parameters are processed before pipeline is started"() {
    given:
    Pipeline startedPipeline = null
    pipelineStarter.start(_) >> { String json ->
      startedPipeline = mapper.readValue(json, Pipeline)
    }

    Map requestedPipeline = [
      trigger: [
        type      : "manual",
        properties: [
          key1        : 'val1',
          key2        : 'val2',
          replaceValue: ['val3']
        ],
        replaceMe : '${trigger.properties.replaceValue}'
      ],
      id     : '${trigger.properties.key1}',
      name   : '${trigger.properties.key2}'
    ]

    when:
    controller.orchestrate(requestedPipeline, null)

    then:
    startedPipeline.id == 'val1'
    startedPipeline.name == 'val2'
    startedPipeline.trigger.replaceMe instanceof ArrayList
    startedPipeline.trigger.replaceMe.first() == 'val3'
  }

  def "processes pipeline parameters"() {
    given:
    Pipeline startedPipeline = null
    pipelineStarter.start(_) >> { String json ->
      startedPipeline = mapper.readValue(json, Pipeline)
    }

    Map requestedPipeline = [
      trigger: [
        parameters: [
          key1: 'value1',
          key2: 'value2'
        ]
      ],
      id     : '${parameters.key1}',
      name   : '${parameters.key2}'
    ]

    when:
    controller.orchestrate(requestedPipeline, null)

    then:
    startedPipeline.id == 'value1'
    startedPipeline.name == 'value2'
  }

  def "fills out pipeline parameters with defaults"() {
    given:
    Pipeline startedPipeline = null
    pipelineStarter.start(_) >> { String json ->
      startedPipeline = mapper.readValue(json, Pipeline)
    }

    Map requestedPipeline = [
      trigger         : [
        parameters: [
          otherParam: 'from pipeline'
        ]
      ],
      parameterConfig : [
        [
          name       : "region",
          default    : "us-west-1",
          description: "region for the deployment"
        ],
        [
          name       : "key1",
          default    : "value1",
          description: "region for the deployment"
        ],
        [
          name       : "otherParam",
          default    : "defaultOther",
          description: "region for the deployment"
        ]
      ],
      pipelineConfigId: '${parameters.otherParam}',
      id              : '${parameters.key1}',
      name            : '${parameters.region}'
    ]

    when:
    controller.orchestrate(requestedPipeline, null)

    then:
    startedPipeline.id == 'value1'
    startedPipeline.name == 'us-west-1'
    startedPipeline.pipelineConfigId == 'from pipeline'
  }

  def "an empty string does not get overriden with default values"() {
    given:
    Pipeline startedPipeline = null
    pipelineStarter.start(_) >> { String json ->
      startedPipeline = mapper.readValue(json, Pipeline)
    }

    Map requestedPipeline = [
      trigger         : [
        parameters: [
          otherParam: ''
        ]
      ],
      parameterConfig : [
        [
          name       : "otherParam",
          default    : "defaultOther",
          description: "region for the deployment"
        ]
      ],
      pipelineConfigId: '${parameters.otherParam}'
    ]

    when:
    controller.orchestrate(requestedPipeline, null)

    then:
    startedPipeline.pipelineConfigId == ''
  }

  def 'limits artifacts in buildInfo based on environment configuration'() {
    given:
    env.withProperty(OperationsController.MAX_ARTIFACTS_PROP, maxArtifacts.toString())
    env.withProperty(OperationsController.PREFERRED_ARTIFACTS_PROP, preferredArtifacts)
    Pipeline startedPipeline = null
    pipelineStarter.start(_) >> { String json ->
      startedPipeline = mapper.readValue(json, Pipeline)
    }
    igor.getBuild(master, job, buildNumber) >> buildInfo

    when:
    controller.orchestrate(requestedPipeline, 'foo')

    then:
    with(startedPipeline) {
      trigger.type == requestedPipeline.trigger.type
      trigger.master == master
      trigger.job == job
      trigger.buildNumber == buildNumber
      trigger.buildInfo.artifacts == expectedArtifacts.collect { [fileName: it] }
    }

    where:
    maxArtifacts | preferredArtifacts | expectedArtifacts
    1            | 'deb'              | ['foo1.deb']
    2            | 'deb'              | ['foo1.deb', 'foo2.rpm']
    2            | 'deb,properties'   | ['foo1.deb', 'foo3.properties']
    2            | 'properties,rpm'   | ['foo3.properties', 'foo2.rpm']


    master = "master"
    job = "job"
    buildNumber = 1337
    requestedPipeline = [
      trigger: [
        type       : "jenkins",
        master     : master,
        job        : job,
        buildNumber: buildNumber,
        user       : 'foo'
      ]
    ]
    buildInfo = [result: "SUCCESS", artifacts: [
      [fileName: 'foo1.deb'],
      [fileName: 'foo2.rpm'],
      [fileName: 'foo3.properties'],
      [fileName: 'foo4.yml'],
      [fileName: 'foo5.json'],
      [fileName: 'foo6.xml'],
      [fileName: 'foo7.txt'],
    ]]
  }

}
