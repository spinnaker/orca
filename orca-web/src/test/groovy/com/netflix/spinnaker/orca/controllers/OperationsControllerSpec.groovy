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

import javax.servlet.http.HttpServletResponse
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.orca.igor.BuildArtifactFilter
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipelinetemplate.PipelineTemplateService
import com.netflix.spinnaker.orca.webhook.config.PreconfiguredWebhookProperties
import com.netflix.spinnaker.orca.webhook.service.WebhookService
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.json.JsonSlurper
import org.apache.log4j.MDC
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.env.MockEnvironment
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.CANCELED
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

class OperationsControllerSpec extends Specification {

  void setup() {
    MDC.clear()
  }

  def executionLauncher = Mock(ExecutionLauncher)
  def buildService = Stub(BuildService)
  def mapper = OrcaObjectMapper.newInstance()
  def executionRepository = Mock(ExecutionRepository)
  def pipelineTemplateService = Mock(PipelineTemplateService)
  def webhookService = Mock(WebhookService)
  def artifactResolver = new ArtifactResolver(mapper, executionRepository)

  def env = new MockEnvironment()
  def buildArtifactFilter = new BuildArtifactFilter(environment: env)

  @Subject
    controller = new OperationsController(
      objectMapper: mapper,
      buildService: buildService,
      buildArtifactFilter: buildArtifactFilter,
      executionRepository: executionRepository,
      pipelineTemplateService: pipelineTemplateService,
      executionLauncher: executionLauncher,
      contextParameterProcessor: new ContextParameterProcessor(),
      webhookService: webhookService,
      artifactResolver: artifactResolver
    )

  @Unroll
  void '#endpoint accepts #contentType'() {
    given:
    def mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    when:
    def resp = mockMvc.perform(
      post(endpoint).contentType(contentType).content('{}')
    ).andReturn().response

    then:
    1 * executionLauncher.start(PIPELINE, _) >> pipeline

    and:
    resp.status == 200
    slurp(resp.contentAsString).ref == "/pipelines/$pipeline.id"

    where:
    contentType << [MediaType.APPLICATION_JSON, MediaType.valueOf('application/context+json')]
    endpoint = "/orchestrate"
    pipeline = Execution.newPipeline("1")
  }

  private Map slurp(String json) {
    new JsonSlurper().parseText(json)
  }

  def "uses trigger details from pipeline if present"() {
    given:
    Execution startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, String json ->
      startedPipeline = mapper.readValue(json, Execution)
      startedPipeline.id = UUID.randomUUID().toString()
      startedPipeline
    }
    buildService.getBuild(buildNumber, master, job) >> buildInfo

    when:
    controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    with(startedPipeline) {
      trigger.type == requestedPipeline.trigger.type
      trigger.master == master
      trigger.job == job
      trigger.buildNumber == buildNumber
      trigger.buildInfo.result == buildInfo.result
    }

    where:
    master = "master"
    job = "job"
    buildNumber = 1337
    requestedPipeline = [
      application: "covfefe",
      trigger    : [
        type       : "jenkins",
        master     : master,
        job        : job,
        buildNumber: buildNumber
      ]
    ]
    buildInfo = [name: job, number: buildNumber, result: "SUCCESS", url: "http://jenkins"]
  }

  def "should not get pipeline execution details from trigger if provided"() {
    given:
    Execution startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, String json ->
      startedPipeline = mapper.readValue(json, Execution)
      startedPipeline.id = UUID.randomUUID().toString()
      startedPipeline
    }

    when:
    controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    0 * executionRepository._

    where:
    requestedPipeline = [
      trigger: [
        type            : "manual",
        parentPipelineId: "12345",
        parentExecution : ['name': 'abc']
      ]
    ]
  }

  def "should get pipeline execution details from trigger if not provided"() {
    given:
    Execution startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, String json ->
      startedPipeline = mapper.readValue(json, Execution)
      startedPipeline.id = UUID.randomUUID().toString()
      startedPipeline
    }
    Execution parentPipeline = pipeline {
      name = "pipeline from orca"
      status = CANCELED
      id = "12345"
      application = "covfefe"
    }

    when:
    controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    1 * executionRepository.retrieve(PIPELINE, "12345") >> parentPipeline

    and:
    with(startedPipeline.trigger) {
      parentExecution != null
      parentExecution.id == "12345"
    }

    where:
    requestedPipeline = [
      application: "covfefe",
      trigger    : [
        type            : "pipeline",
        parentPipelineId: "12345"
      ]
    ]
  }

  def "should get pipeline execution context from a previous execution if not provided and attribute plan is truthy"() {
    given:
    Execution startedPipeline = null
    executionLauncher.start(*_) >> { type, String json ->
      startedPipeline = mapper.readValue(json, Execution)
      startedPipeline.id = UUID.randomUUID().toString()
      startedPipeline
    }

    def previousExecution = pipeline {
      name = "Last executed pipeline"
      status = SUCCEEDED
      id = "12345"
      application = "covfefe"
    }

    when:
    def orchestration = controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    1 * pipelineTemplateService.retrievePipelineOrNewestExecution("12345", _) >> previousExecution
    orchestration.trigger.type == "manual"

    where:
    requestedPipeline = [
      id         : "54321",
      plan       : true,
      type       : "templatedPipeline",
      executionId: "12345"
    ]
  }

  def "trigger user takes precedence over query parameter"() {
    given:
    Execution startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, String json ->
      startedPipeline = mapper.readValue(json, Execution)
      startedPipeline.id = UUID.randomUUID().toString()
      startedPipeline
    }
    buildService.getBuild(buildNumber, master, job) >> buildInfo

    if (queryUser) {
      MDC.put(AuthenticatedRequest.SPINNAKER_USER, queryUser)
    }
    when:
    controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    with(startedPipeline) {
      trigger.type == requestedPipeline.trigger.type
      trigger.master == master
      trigger.job == job
      trigger.buildNumber == buildNumber
      trigger.buildInfo.result == buildInfo.result
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
    buildInfo = [name: job, number: buildNumber, result: "SUCCESS", url: "http://jenkins"]

  }

  def "gets properties file from igor if specified in pipeline"() {
    given:
    Execution startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, String json ->
      startedPipeline = mapper.readValue(json, Execution)
      startedPipeline.id = UUID.randomUUID().toString()
      startedPipeline
    }
    buildService.getBuild(buildNumber, master, job) >> [name: job, number: buildNumber, result: "SUCCESS", url: "http://jenkins"]
    buildService.getPropertyFile(buildNumber, propertyFile, master, job) >> propertyFileContent

    when:
    controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

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
    Execution startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, String json ->
      startedPipeline = mapper.readValue(json, Execution)
    }

    Map requestedPipeline = [
      trigger: [
        type      : "jenkins",
        master    : "master",
        job       : "jon",
        number    : 1,
        properties: [
          key1        : 'val1',
          key2        : 'val2',
          replaceValue: ['val3']
        ]
      ],
      id     : '${trigger.properties.key1}',
      name   : '${trigger.properties.key2}'
    ]

    when:
    controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    startedPipeline.id == 'val1'
    startedPipeline.name == 'val2'
  }

  def "processes pipeline parameters"() {
    given:
    Execution startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, String json ->
      startedPipeline = mapper.readValue(json, Execution)
    }

    Map requestedPipeline = [
      trigger: [
        type      : "manual",
        parameters: [
          key1: 'value1',
          key2: 'value2'
        ]
      ],
      id     : '${parameters.key1}',
      name   : '${parameters.key2}'
    ]

    when:
    controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    startedPipeline.id == 'value1'
    startedPipeline.name == 'value2'
  }

  def "fills out pipeline parameters with defaults"() {
    given:
    Execution startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, String json ->
      startedPipeline = mapper.readValue(json, Execution)
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
    controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    startedPipeline.id == 'value1'
    startedPipeline.name == 'us-west-1'
    startedPipeline.pipelineConfigId == 'from pipeline'
  }

  def "an empty string does not get overriden with default values"() {
    given:
    Execution startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, String json ->
      startedPipeline = mapper.readValue(json, Execution)
      startedPipeline.id = UUID.randomUUID().toString()
      startedPipeline
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
    controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    startedPipeline.pipelineConfigId == ''
  }

  @Unroll
  def 'limits artifacts in buildInfo based on environment configuration'() {
    given:
    env.withProperty(BuildArtifactFilter.MAX_ARTIFACTS_PROP, maxArtifacts.toString())
    env.withProperty(BuildArtifactFilter.PREFERRED_ARTIFACTS_PROP, preferredArtifacts)
    Execution startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, String json ->
      startedPipeline = mapper.readValue(json, Execution)
      startedPipeline.id = UUID.randomUUID().toString()
      startedPipeline
    }
    buildService.getBuild(buildNumber, master, job) >> buildInfo

    when:
    controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    with(startedPipeline) {
      trigger.type == requestedPipeline.trigger.type
      trigger.master == master
      trigger.job == job
      trigger.buildNumber == buildNumber
      trigger.buildInfo.artifacts.fileName == expectedArtifacts
    }

    where:
    maxArtifacts | preferredArtifacts | expectedArtifacts
    1            | 'deb'              | ['foo1.deb']
    2            | 'deb'              | ['foo1.deb', 'foo2.rpm']
    2            | 'deb,properties'   | ['foo1.deb', 'foo3.properties']
    2            | 'properties,rpm'   | ['foo3.properties', 'foo2.rpm']
    1            | 'nupkg'            | ['foo8.nupkg']


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
    buildInfo = [name: job, number: buildNumber, url: "http://jenkins", result: "SUCCESS", artifacts: [
      [fileName: 'foo1.deb', relativePath: "."],
      [fileName: 'foo2.rpm', relativePath: "."],
      [fileName: 'foo3.properties', relativePath: "."],
      [fileName: 'foo4.yml', relativePath: "."],
      [fileName: 'foo5.json', relativePath: "."],
      [fileName: 'foo6.xml', relativePath: "."],
      [fileName: 'foo7.txt', relativePath: "."],
      [fileName: 'foo8.nupkg', relativePath: "."],
    ]]
  }

  def "should not start pipeline when truthy plan pipeline attribute is present"() {
    given:
    def pipelineConfig = [
      plan: true
    ]

    when:
    controller.orchestrate(pipelineConfig, Mock(HttpServletResponse))

    then:
    0 * executionLauncher.start(*_)
  }

  def "should throw validation exception when templated pipeline contains errors"() {
    given:
    def pipelineConfig = [
      plan       : true,
      type       : "templatedPipeline",
      executionId: "12345",
      errors     : [
        'things broke': 'because of the way it is'
      ]
    ]
    def response = Mock(HttpServletResponse)

    when:
    controller.orchestrate(pipelineConfig, response)

    then:
    thrown(InvalidRequestException)
    1 * pipelineTemplateService.retrievePipelineOrNewestExecution("12345", null) >> {
      throw new ExecutionNotFoundException("Not found")
    }
    0 * executionLauncher.start(*_)
  }

  def "should return empty list if webhook stage is not enabled"() {
    given:
    controller.webhookService = null

    when:
    def preconfiguredWebhooks = controller.preconfiguredWebhooks()

    then:
    0 * webhookService.preconfiguredWebhooks
    preconfiguredWebhooks == []
  }

  def "should call webhookService and return correct information"() {
    given:
    def preconfiguredProperties = ["url", "customHeaders", "method", "payload", "waitForCompletion", "statusUrlResolution",
                                   "statusUrlJsonPath", "statusJsonPath", "progressJsonPath", "successStatuses", "canceledStatuses", "terminalStatuses"]

    when:
    def preconfiguredWebhooks = controller.preconfiguredWebhooks()

    then:
    1 * webhookService.preconfiguredWebhooks >> [
      createPreconfiguredWebhook("Webhook #1", "Description #1", "webhook_1"),
      createPreconfiguredWebhook("Webhook #2", "Description #2", "webhook_2")
    ]
    preconfiguredWebhooks == [
      [label: "Webhook #1", description: "Description #1", type: "webhook_1", waitForCompletion: true, preconfiguredProperties: preconfiguredProperties, noUserConfigurableFields: true, parameters: null],
      [label: "Webhook #2", description: "Description #2", type: "webhook_2", waitForCompletion: true, preconfiguredProperties: preconfiguredProperties, noUserConfigurableFields: true, parameters: null]
    ]
  }

  static PreconfiguredWebhookProperties.PreconfiguredWebhook createPreconfiguredWebhook(
    def label, def description, def type) {
    def customHeaders = new HttpHeaders()
    customHeaders.put("header", ["value1"])
    return new PreconfiguredWebhookProperties.PreconfiguredWebhook(
      label: label, description: description, type: type,
      url: "a", customHeaders: customHeaders, method: HttpMethod.POST, payload: "b",
      waitForCompletion: true, statusUrlResolution: PreconfiguredWebhookProperties.StatusUrlResolution.webhookResponse,
      statusUrlJsonPath: "c", statusJsonPath: "d", progressJsonPath: "e", successStatuses: "f", canceledStatuses: "g", terminalStatuses: "h", parameters: null
    )
  }
}
