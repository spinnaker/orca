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

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.Collections2
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.CompoundExecutionOperator
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner
import com.netflix.spinnaker.orca.pipeline.model.*
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import groovy.json.JsonSlurper
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Clock
import java.time.Instant

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator.*
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.*
import static java.time.ZoneOffset.UTC
import static java.time.temporal.ChronoUnit.DAYS
import static java.time.temporal.ChronoUnit.HOURS
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class TaskControllerSpec extends Specification {

  MockMvc mockMvc
  def executionRepository = Mock(ExecutionRepository)
  def front50Service = Mock(Front50Service)
  def executionRunner = Mock(ExecutionRunner)
  def executionOperator = Mock(CompoundExecutionOperator)
  def mapper = new ObjectMapper()
  def registry = new NoopRegistry()

  def clock = Clock.fixed(Instant.now(), UTC)
  int daysOfExecutionHistory = 14
  int numberOfOldPipelineExecutionsToInclude = 2

  ObjectMapper objectMapper = OrcaObjectMapper.newInstance()

  void setup() {
    mockMvc = MockMvcBuilders.standaloneSetup(
      new TaskController(
        front50Service: front50Service,
        executionRepository: executionRepository,
        executionRunner: executionRunner,
        daysOfExecutionHistory: daysOfExecutionHistory,
        numberOfOldPipelineExecutionsToInclude: numberOfOldPipelineExecutionsToInclude,
        clock: clock,
        mapper: mapper,
        registry: registry,
        contextParameterProcessor: new ContextParameterProcessor(),
        executionOperator: executionOperator
      )
    ).build()
  }

  void '/tasks returns a list of active tasks'() {
    when:
    mockMvc.perform(get('/tasks')).andReturn().response

    then:
    1 * executionRepository.retrieve(ORCHESTRATION) >> {
      return rx.Observable.empty()
    }
  }

  void 'should cancel a list of tasks by id'() {
    given:
    def orc1 = Stub(Execution)
    def orc2 = Stub(Execution)
    when:
    def response = mockMvc.perform(
      put('/tasks/cancel').contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(["id1", "id2"]))
    )

    then:
    response.andExpect(status().isAccepted())
    1 * executionOperator.cancel(ORCHESTRATION, 'id1')
    1 * executionOperator.cancel(ORCHESTRATION, 'id2')
    0 * _
  }

  void 'step names are properly translated'() {
    given:
    executionRepository.retrieve(ORCHESTRATION) >> rx.Observable.from([orchestration {
      id = "1"
      application = "covfefe"
      stage {
        type = "test"
        tasks = [new Task(id:'1', name: 'jobOne', startTime: 1L, endTime: 2L, implementingClass: 'Class' ),
                 new Task(id:'2', name: 'jobTwo', startTime: 1L, endTime: 2L, implementingClass: 'Class' )]
      }
    }])

    when:
    def response = mockMvc.perform(get('/tasks')).andReturn().response

    then:
    response.status == 200
    with(new JsonSlurper().parseText(response.contentAsString).first()) {
      steps.name == ['jobOne', 'jobTwo']
    }
  }

  void 'stage contexts are included for orchestrated tasks'() {
    setup:
    def orchestration = orchestration {
      id = "1"
      application = "covfefe"
      stages << new Stage(delegate, "OrchestratedType")
      stages.first().context = [customOutput: "variable"]
    }

    when:
    def response = mockMvc.perform(get('/tasks/1')).andReturn().response

    then:
    executionRepository.retrieve(orchestration.type, orchestration.id) >> orchestration

    new JsonSlurper().parseText(response.contentAsString).variables == [
      [key: "customOutput", value: "variable"]
    ]
  }

  @Unroll
  void 'duplicate empty keys are not saved into the list of variables'() {
    setup:
    def variables = [
      mystringkey: "",
      mystringkey2: "mystringval",
      mymapkey: [:],
      mymapkey2: [
        childkey:"childVal"
      ],
      mylistkey: [],
      mylistkey2: ["why", "oh", "why"]
    ]

    when:
    def entry = [(thekey): value].entrySet().first()
    def result = TaskController.shouldReplace(entry, variables)

    then:
    result == shouldReplace

    where:
    thekey            | value    | shouldReplace
    "mystringkey"     | "hi"     | true
    "mystringkey2"    | ""       | false
    "mymapkey"        | [k: "v"] | true
    "mymapkey2"       | [:]      | false
    "mylistkey"       | ["hi"]   | true
    "mylistkey2"      | ["hi"]   | true
    "mylistkey2"      | []       | false
    "mynewkey"        | [:]      | true
    "mynewkey"        | ["hi"]   | true
    "mynewkey"        | "hi"     | true

  }

  void '/tasks returns [] when there are no tasks'() {
    when:
    MockHttpServletResponse response = mockMvc.perform(get('/tasks')).andReturn().response

    then:
    1 * executionRepository.retrieve(ORCHESTRATION) >> rx.Observable.from([])
    response.status == 200
    response.contentAsString == '[]'
  }

  void '/applications/{application}/tasks filters tasks by application'() {
    when:
    def response = mockMvc.perform(get("/applications/$app/tasks")).andReturn().response

    then:
    1 * executionRepository.retrieveOrchestrationsForApplication(app, _, START_TIME_OR_ID) >> []

    where:
    app = "test"
  }

  void '/applications/{application}/pipelines should only return pipelines from the past two weeks, newest first'() {
    given:
    def app = 'test'
    def pipelines = [
      [pipelineConfigId: "1", id: "old", application: app, startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli()],
      [pipelineConfigId: "1", id: "newer", application: app, startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).plus(2, HOURS).toEpochMilli()],
      [pipelineConfigId: "1", id: 'not-started', application: app],
      [pipelineConfigId: "1", id: 'also-not-started', application: app],

      /*
       * If a pipeline has no recent executions, the most recent N executions should be included
       */
      [pipelineConfigId: "2", id: 'older1', application: app, startTime: clock.instant().minus(daysOfExecutionHistory + 1, DAYS).minus(2, HOURS).toEpochMilli()],
      [pipelineConfigId: "2", id: 'older2', application: app, startTime: clock.instant().minus(daysOfExecutionHistory + 1, DAYS).minus(3, HOURS).toEpochMilli()],
      [pipelineConfigId: "2", id: 'older3', application: app, startTime: clock.instant().minus(daysOfExecutionHistory + 1, DAYS).minus(4, HOURS).toEpochMilli()]
    ]

    executionRepository.retrievePipelinesForPipelineConfigId("1", _) >> rx.Observable.from(pipelines.findAll {
      it.pipelineConfigId == "1"
    }.collect { config ->
      pipeline {
        id = config.id
        application = app
        startTime = config.startTime
        pipelineConfigId = config.pipelineConfigId
      }
    })
    executionRepository.retrievePipelinesForPipelineConfigId("2", _) >> rx.Observable.from(pipelines.findAll {
      it.pipelineConfigId == "2"
    }.collect { config ->
      pipeline {
        id = config.id
        application = app
        startTime = config.startTime
        pipelineConfigId = config.pipelineConfigId
      }
    })
    front50Service.getPipelines(app, false) >> [[id: "1"], [id: "2"]]
    front50Service.getStrategies(app) >> []

    when:
    def response = mockMvc.perform(get("/applications/$app/pipelines")).andReturn().response
    List results = new ObjectMapper().readValue(response.contentAsString, List)

    then:
    results.id == ['not-started', 'also-not-started', 'older2', 'older1', 'newer']
  }

  void '/applications/{application}/evaluateExpressions precomputes values'() {
    given:
    executionRepository.retrieve(Execution.ExecutionType.PIPELINE, "1") >> {
      pipeline {
        id = "1"
        application = "doesn't matter"
        startTime = 1
        pipelineConfigId = "1"
        trigger = new DefaultTrigger("manual", "id", "user", [param1: "param1Value"])
      }
    }

    when:
    def response = mockMvc.perform(
      get("/pipelines/1/evaluateExpression")
        .param("id", "1")
        .param("expression", '${parameters.param1}'))
      .andReturn().response
    Map results = new ObjectMapper().readValue(response.contentAsString, Map)

    then:
    results == [
      result: "param1Value",
      detail: null
    ]
  }

  void '/pipelines should only return the latest pipelines for the provided config ids, newest first'() {
    given:
    def pipelines = [
      [pipelineConfigId: "1", id: "started-1", application: "covfefe", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(), id: 'old-1'],
      [pipelineConfigId: "1", id: "started-2", application: "covfefe", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).plus(2, HOURS).toEpochMilli(), id: 'newer-1'],
      [pipelineConfigId: "1", id: 'not-started-1', application: "covfefe"],
      [pipelineConfigId: "2", id: "started-3", application: "covfefe", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(), id: 'old-2'],
      [pipelineConfigId: "2", id: "started-4", application: "covfefe", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).plus(2, HOURS).toEpochMilli(), id: 'newer-2'],
      [pipelineConfigId: "2", id: 'not-started-2', application: "covfefe"],
      [pipelineConfigId: "3", id: "started-5", application: "covfefe", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(), id: 'old-3']
    ]

    executionRepository.retrievePipelinesForPipelineConfigId("1", _) >> rx.Observable.from(pipelines.findAll {
      it.pipelineConfigId == "1"
    }.collect { config ->
      pipeline {
        id = config.id
        application = "covfefe"
        startTime = config.startTime
        pipelineConfigId = config.pipelineConfigId
      }
    })
    executionRepository.retrievePipelinesForPipelineConfigId("2", _) >> rx.Observable.from(pipelines.findAll {
      it.pipelineConfigId == "2"
    }.collect { config ->
      pipeline {
        id = config.id
        application = "covfefe"
        startTime = config.startTime
        pipelineConfigId = config.pipelineConfigId
      }
    })
    executionRepository.retrievePipelinesForPipelineConfigId("3", _) >> rx.Observable.from(pipelines.findAll {
      it.pipelineConfigId == "3"
    }.collect { config ->
      pipeline {
        id = config.id
        application = "covfefe"
        startTime = config.startTime
        pipelineConfigId = config.pipelineConfigId
      }
    })

    when:
    def response = mockMvc.perform(get("/pipelines?pipelineConfigIds=1,2")).andReturn().response
    List results = new ObjectMapper().readValue(response.contentAsString, List)

    then:
    results.id == ['newer-2', 'newer-1']
  }

  void 'should update existing stage context'() {
    given:
    def pipeline = Execution.newPipeline("covfefe")
    def pipelineStage = new Stage(pipeline, "test", [value: "1"])
    pipelineStage.id = "s1"
    pipeline.stages.add(pipelineStage)

    when:
    def response = mockMvc.perform(patch("/pipelines/$pipeline.id/stages/s1").content(
      objectMapper.writeValueAsString([judgmentStatus: "stop"])
    ).contentType(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    1 * executionRepository.retrieve(pipeline.type, pipeline.id) >> pipeline
    1 * executionRepository.storeStage({ stage ->
      stage.id == "s1" &&
        stage.lastModified.allowedAccounts.isEmpty() &&
        stage.lastModified.user == "anonymous" &&
        stage.context == [
        judgmentStatus: "stop", value: "1", lastModifiedBy: "anonymous"
      ]
    } as Stage)
    1 * executionRunner.reschedule(pipeline)
    0 * _

    and:
    objectMapper.readValue(response.contentAsString, Map).stages*.context == [
      [value: "1", judgmentStatus: "stop", lastModifiedBy: "anonymous"]
    ]
  }

  void '/applications/{application}/pipelines/search should return pipelines of all types if triggerTypes if not provided'() {
    given:
    def app = "covfefe"
    def pipelines = [
      [pipelineConfigId: "1", id: "test-1", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new DockerTrigger("test-account", "test-repo", "1")
      ],
      [pipelineConfigId: "1", id: "test-2", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new GitTrigger("c681a6af-1096-4727-ac9e-70d3b2460228", "github", "spinnaker", "no-match", "orca", "push")
      ],
      [pipelineConfigId: "1", id: "test-3", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new JenkinsTrigger("master", "job", 1, "test-property-file")
      ],
      [pipelineConfigId: "1", id: "test-4", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new ArtifactoryTrigger("libs-demo-local")
      ],
      [pipelineConfigId: "1", id: "test-5", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new NexusTrigger("libs-nexus-local")
      ]
    ]

    ObjectMapper mapper = new ObjectMapper()

    executionRepository.retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(["1"], _, _, _ ) >> pipelines.findAll {
        it.pipelineConfigId == "1"
      }.collect { config ->
        Execution pipeline = pipeline {
          id = config.id
          application = app
          startTime = config.startTime
          pipelineConfigId = config.pipelineConfigId
        }
        config.trigger.setOther(mapper.convertValue(config.trigger, Map.class))
        pipeline.setTrigger(config.trigger)
        return pipeline
      }


    front50Service.getPipelines(app, false) >> [[id: "1"]]

    when:
    def response = mockMvc.perform(get("/applications/${app}/pipelines/search")).andReturn().response
    List results = new ObjectMapper().readValue(response.contentAsString, List)

    then:
    results.id == ['test-1', 'test-2', 'test-3', 'test-4', 'test-5']
  }

  void '/applications/{application}/pipelines/search should only return pipelines of given types'() {
    given:
    def app = "covfefe"
    def pipelines = [
      [pipelineConfigId: "1", id: "test-1", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
        trigger: new DockerTrigger("test-account", "test-repo", "1")
      ],
      [pipelineConfigId: "1", id: "test-2", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(1, HOURS).toEpochMilli(),
        trigger: new GitTrigger("c681a6af-1096-4727-ac9e-70d3b2460228", "github", "spinnaker", "no-match", "orca", "push")
      ],
      [pipelineConfigId: "1", id: "test-3", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
        trigger: new GitTrigger("c681a6af-1096-4727-ac9e-70d3b2460228", "github", "spinnaker", "no-match", "orca", "push")
      ],
      [pipelineConfigId: "1", id: "test-4", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
        trigger: new JenkinsTrigger("master", "job", 1, "test-property-file")
      ],
      [pipelineConfigId: "1", id: "test-5", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new ArtifactoryTrigger("libs-demo-local")
      ],
      [pipelineConfigId: "1", id: "test-6", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new NexusTrigger("libs-nexus-local")
      ]
    ]

    ObjectMapper mapper = new ObjectMapper()

    executionRepository.retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(["1"], _, _, _) >> pipelines.findAll {
      it.pipelineConfigId == "1"
    }.collect { config ->
      Execution pipeline = pipeline {
        id = config.id
        application = app
        startTime = config.startTime
        pipelineConfigId = config.pipelineConfigId
      }
      config.trigger.setOther(mapper.convertValue(config.trigger, Map.class))
      pipeline.setTrigger(config.trigger)
      return pipeline
    }

    front50Service.getPipelines(app, false) >> [[id: "1"]]

    when:
    def response = mockMvc.perform(get("/applications/${app}/pipelines/search?triggerTypes=docker,jenkins")).andReturn().response
    List results = new ObjectMapper().readValue(response.contentAsString, List)

    then:
    results.id == ['test-1', 'test-4']
  }

  void '/applications/{application}/pipelines/search should only return pipelines with a given eventId'() {
    given:
    def app = "covfefe"
    def wrongEventId = "a"
    def eventId = "b"
    def pipelines = [
      [pipelineConfigId: "1", id: "test-1", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new DockerTrigger("test-account", "test-repo", "1"), eventId: wrongEventId
      ],
      [pipelineConfigId: "1", id: "test-2", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(1, HOURS).toEpochMilli(),
       trigger: new GitTrigger("c681a6af-1096-4727-ac9e-70d3b2460228", "github", "spinnaker", "no-match", "orca", "push"), eventId: eventId
      ],
      [pipelineConfigId: "1", id: "test-3", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new GitTrigger("c681a6af-1096-4727-ac9e-70d3b2460228", "github", "spinnaker", "no-match", "orca", "push"), eventId: wrongEventId
      ],
      [pipelineConfigId: "1", id: "test-4", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new JenkinsTrigger("master", "job", 1, "test-property-file"), eventId: eventId
      ],
      [pipelineConfigId: "1", id: "test-5", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new ArtifactoryTrigger("libs-demo-local"), eventId: wrongEventId
      ],
      [pipelineConfigId: "1", id: "test-6", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new NexusTrigger("libs-nexus-local"), eventId: wrongEventId
      ]
    ]

    ObjectMapper mapper = new ObjectMapper()

    executionRepository.retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(["1"], _, _, _) >> pipelines.findAll {
      it.pipelineConfigId == "1"
    }.collect { config ->
      Execution pipeline = pipeline {
        id = config.id
        application = app
        startTime = config.startTime
        pipelineConfigId = config.pipelineConfigId
      }
      config.trigger.setOther(mapper.convertValue(config.trigger, Map.class))
      config.trigger.other.put("eventId", config.eventId)
      pipeline.setTrigger(config.trigger)
      return pipeline
    }

    front50Service.getPipelines(app, false) >> [[id: "1"]]

    when:
    def response = mockMvc.perform(get("/applications/${app}/pipelines/search?eventId=" + eventId)).andReturn().response
    List results = new ObjectMapper().readValue(response.contentAsString, List)

    then:
    results.id == ['test-2', 'test-4']
  }

  void '/applications/{application}/pipelines/search should only return pipelines with a given application'() {
    given:
    def app1 = "app1"
    def app2 = "app2"
    def pipelines = [
      [pipelineConfigId: "1", id: "test-1", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new DockerTrigger("test-account", "test-repo", "1")
      ],
      [pipelineConfigId: "1", id: "test-2", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new GitTrigger("c681a6af-1096-4727-ac9e-70d3b2460228", "github", "spinnaker", "no-match", "orca", "push")
      ],
      [pipelineConfigId: "2", id: "test-3", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new JenkinsTrigger("master", "job", 1, "test-property-file")
      ],
      [pipelineConfigId: "2", id: "test-4", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new JenkinsTrigger("master", "job", 1, "test-property-file")
      ]
    ]

    ObjectMapper mapper = new ObjectMapper()

    executionRepository.retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(["1"], _, _, _) >> pipelines.findAll {
      it.pipelineConfigId == "1"
    }.collect { config ->
      Execution pipeline = pipeline {
        id = config.id
        application = app1
        startTime = config.startTime
        pipelineConfigId = config.pipelineConfigId
      }
      config.trigger.setOther(mapper.convertValue(config.trigger, Map.class))
      pipeline.setTrigger(config.trigger)
      return pipeline
    }

    front50Service.getPipelines(app1, false) >> [[id: "1"]]
    front50Service.getPipelines(app2, false) >> [[id: "2"]]

    when:
    def response = mockMvc.perform(get("/applications/${app1}/pipelines/search")).andReturn().response
    List results = new ObjectMapper().readValue(response.contentAsString, List)

    then:
    results.id == ['test-1', 'test-2']
  }

  void '/applications/{application}/pipelines/search should return executions with a given pipeline name'() {
    given:
    def app = "covfefe"
    def pipelines = [
      [name: "pipeline1", pipelineConfigId: "1", id: "test-1", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new DockerTrigger("test-account", "test-repo", "1")
      ],
      [name: "pipeline2", pipelineConfigId: "2", id: "test-2", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new GitTrigger("c681a6af-1096-4727-ac9e-70d3b2460228", "github", "spinnaker", "no-match", "orca", "push")
      ],
      [name: "pipeline3", pipelineConfigId: "3", id: "test-3", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new JenkinsTrigger("master", "job", 1, "test-property-file")
      ]
    ]

    ObjectMapper mapper = new ObjectMapper()

    executionRepository.retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(["2"], _, _, _) >> pipelines.findAll {
      it.pipelineConfigId == "2"
    }.collect { config ->
      Execution pipeline = pipeline {
        id = config.id
        application = app
        startTime = config.startTime
        pipelineConfigId = config.pipelineConfigId
      }
      config.trigger.setOther(mapper.convertValue(config.trigger, Map.class))
      pipeline.setTrigger(config.trigger)
      pipeline.setName(config.name)
      return pipeline
    }

    front50Service.getPipelines(app, false) >> [[id: "2", name: "pipeline2"]]

    when:
    def response = mockMvc.perform(get("/applications/${app}/pipelines/search?pipelineName=pipeline2")).andReturn().response
    List results = new ObjectMapper().readValue(response.contentAsString, List)

    then:
    results.id == ['test-2']
  }

  void '/applications/{application}/pipelines/search should handle a trigger field that is a string'() {
    given:
    def app = "covfefe"
    def pipelines = [
      [pipelineConfigId: "1", id: "test-1", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new DockerTrigger("test-account", "test-repo", "1")
      ],
      [pipelineConfigId: "1", id: "test-2", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new JenkinsTrigger("master", "job", 1, "test-property-file")
      ],
      [pipelineConfigId: "1", id: "test-3", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(1, HOURS).toEpochMilli(),
       trigger: new DockerTrigger("test-account", "test-repo", "1")
      ]
    ]

    ObjectMapper mapper = new ObjectMapper()

    executionRepository.retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(["1"], _, _, _) >> pipelines.findAll {
      it.pipelineConfigId == "1"
    }.collect { config ->
      Execution pipeline = pipeline {
        id = config.id
        application = app
        startTime = config.startTime
        pipelineConfigId = config.pipelineConfigId
      }
      config.trigger.setOther(mapper.convertValue(config.trigger, Map.class))
      pipeline.setTrigger(config.trigger)
      return pipeline
    }

    front50Service.getPipelines(app, false) >> [[id: "1"]]

    when:
    String encodedTriggerParams = new String(Base64.getEncoder().encode('{"account":"test-account","repository":"test-repo","tag":"1"}'.getBytes()))
    def response = mockMvc.perform(get("/applications/${app}/pipelines/search?trigger=${encodedTriggerParams}")).andReturn().response
    List results = new ObjectMapper().readValue(response.contentAsString, List)

    then:
    results.id == ['test-1', 'test-3']
  }

  void '/applications/{application}/pipelines/search should handle a trigger search field that is a list of maps correctly and deterministicly'() {
    given:
    def app = "covfefe"
    def pipelines = [
      [pipelineConfigId: "1", id: "test-1", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new DockerTrigger("test-account", "test-repo", "1")
      ],
      [pipelineConfigId: "1", id: "test-2", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(1, HOURS).toEpochMilli(),
       trigger: new DockerTrigger("test-account", "test-repo", "1")
      ]
    ]
    pipelines[0].trigger.artifacts.addAll([[name: "a", version: "1"],  [name: "a"]])
    pipelines[1].trigger.artifacts.addAll([[name: "a"], [name: "a", version: "1"]])

    ObjectMapper mapper = new ObjectMapper()

    executionRepository.retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(["1"], _, _, _) >> pipelines.findAll {
      it.pipelineConfigId == "1"
    }.collect { config ->
      Execution pipeline = pipeline {
        id = config.id
        application = app
        startTime = config.startTime
        pipelineConfigId = config.pipelineConfigId
      }
      config.trigger.setOther(mapper.convertValue(config.trigger, Map.class))
      pipeline.setTrigger(config.trigger)
      return pipeline
    }

    front50Service.getPipelines(app, false) >> [[id: "1"]]

    when:
    String encodedTriggerParams1 = new String(Base64.getEncoder().encode('{"artifacts":[{"name":"a","version":"1"},{"name":"a"}]}'.getBytes()))
    def response1 = mockMvc.perform(get("/applications/${app}/pipelines/search?trigger=${encodedTriggerParams1}")).andReturn().response
    List results1 = new ObjectMapper().readValue(response1.contentAsString, List)

    String encodedTriggerParams2 = new String(Base64.getEncoder().encode('{"artifacts":[{"name":"a"},{"name":"a","version":"1"}]}'.getBytes()))
    def response2 = mockMvc.perform(get("/applications/${app}/pipelines/search?trigger=${encodedTriggerParams2}")).andReturn().response
    List results2 = new ObjectMapper().readValue(response2.contentAsString, List)

    then:
    results1.id == ['test-1', 'test-2']
    results2.id == ['test-1', 'test-2']
  }

  void '/applications/{application}/pipelines/search should handle a trigger field that is a map'() {
    given:
    def app = "covfefe"
    def pipelines = [
      [pipelineConfigId: "1", id: "test-1", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new DefaultTrigger("webhook", null, "test"), payload: [a: "1"]
      ],
      [pipelineConfigId: "1", id: "test-2", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(1, HOURS).toEpochMilli(),
       trigger: new DefaultTrigger("webhook", null, "test"), payload: [a: "1", b: "2"]
      ],
      [pipelineConfigId: "1", id: "test-3", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minus(2, HOURS).toEpochMilli(),
       trigger: new DefaultTrigger("webhook", null, "test"), payload: [a: "1", b: "2", c: "3"]
      ]
    ]

    ObjectMapper mapper = new ObjectMapper()

    executionRepository.retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(["1"], _, _, _) >> pipelines.findAll {
      it.pipelineConfigId == "1"
    }.collect { config ->
      Execution pipeline = pipeline {
        id = config.id
        application = app
        startTime = config.startTime
        pipelineConfigId = config.pipelineConfigId
      }
      config.trigger.setOther(mapper.convertValue(config.trigger, Map.class))
      config.trigger.other.put("payload", config.payload)
      pipeline.setTrigger(config.trigger)
      return pipeline
    }

    front50Service.getPipelines(app, false) >> [[id: "1"]]

    when:
    String encodedTriggerParams = new String(Base64.getEncoder().encode('{"payload":{"a":"1","b":"2"}}'.getBytes()))
    def response = mockMvc.perform(get("/applications/${app}/pipelines/search?trigger=${encodedTriggerParams}")).andReturn().response
    List results = new ObjectMapper().readValue(response.contentAsString, List)

    then:
    results.id == ['test-2', 'test-3']
  }

  void 'checkObjectMatchesSubset matches identical strings'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset("this is a test", "this is a test")

    then:
    result
  }

  void 'checkObjectMatchesSubset matches identical case-insensitive strings'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset("THIS is a TEST", "this IS A test")

    then:
    result
  }

  void 'checkObjectMatchesSubset matches strings using regex'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset("this is a test", "this.*")

    then:
    result
  }

  void 'checkObjectMatchesSubset does not match different strings'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset("this is a test", "this is a test ")

    then:
    !result
  }

  void 'checkObjectMatchesSubset matches identical integers'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset(1, 1)

    then:
    result
  }

  void 'checkObjectMatchesSubset integer object matches identical integer primitive'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset(Integer.valueOf(1), 1)

    then:
    result
  }

  void 'checkObjectMatchesSubset matches identical lists'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset(
      ["a", 1, true],
      ["a", 1, true]
    )

    then:
    result
  }

  void 'checkObjectMatchesSubset matches identical lists in any order'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset(
      ["a", 1, true],
      [1, true, "a"]
    )

    then:
    result
  }

  void 'checkObjectMatchesSubset list matches subset'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset(
      ["a", 1, true],
      [true, "a"]
    )

    then:
    result
  }

  void 'checkObjectMatchesSubset list matches empty list'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset(
      ["a", 1, true],
      []
    )

    then:
    result
  }

  void 'checkObjectMatchesSubset list does not non-list'() {
    given:
    def object = ["a", 1, true]

    def subsets = [
      [:],
      "test",
      1,
      false
    ]

    when:
    boolean result = subsets.every{ false == TaskController.checkObjectMatchesSubset(object, it) }

    then:
    result
  }

  void 'checkObjectMatchesSubset matches identical complex lists'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset(
      [
        [],
        ["a", "b", "c"],
        [true, false],
        [:],
        "test",
        [x: 1, y: 2,
          z: [
            q: "asdf",
            r: [
              "t", "u", "v"
              ]
          ]
        ]
      ],
      [
        [],
        ["a", "b", "c"],
        [true, false],
        [:],
        "test",
        [x: 1, y: 2,
         z: [
           q: "asdf",
           r: [
             "t", "u", "v"
           ]
         ]
        ]
      ]
    )

    then:
    result
  }

  void 'checkObjectMatchesSubset matches identical complex lists in any order'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset(
      [
        [],
        ["a", "b", "c"],
        [true, false],
        [:],
        "test",
        [x: 1, y: 2,
         z: [
           q: "asdf",
           r: [
             "t", "u", "v"
           ]
         ]
        ]
      ],
      [
        [y: 2, x: 1,
         z: [
           r: [
             "v", "u", "t"
           ],
           q: "asdf"
         ]
        ],
        [:],
        "test",
        ["c", "a", "b"],
        [false, true],
        []
      ]
    )

    then:
    result
  }

  void 'checkObjectMatchesSubset complex list matches subset'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset(
      [
        [],
        ["a", "b", "c"],
        [true, false],
        [:],
        "test",
        [x: 1, y: 2,
         z: [
           q: "asdf",
           r: [
             "t", "u", "v"
           ]
         ]
        ]
      ],
      [
        [z:
          [
            r: [
              "t"
            ]
          ]
        ],
        "test",
        ["c", "b"],
        [false]
      ]
    )

    then:
    result
  }

  void 'checkObjectMatchesSubset matches list inputs of all permutations'() {
    given:
    def object = [
      [
        name: "a",
        version: 1,
        type: "test",
        timestamp: 1529415883467
      ],
      [
        name: "a",
        version: 1,
        type: "test"
      ],
      [
        name: "a",
        version: 1
      ],
      [
        name: "a"
      ]
    ]

    def subsets = Collections2.permutations(object)

    when:
    boolean result = subsets.every{ TaskController.checkObjectMatchesSubset(object, it) }

    then:
    result
  }

  void 'checkObjectMatchesSubset does not match lists if subset list is larger'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset(
      ["", "", ""],
      ["", "", "", ""]
    )

    then:
    !result
  }

  void 'checkObjectMatchesSubset matches identical maps'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset(
      [d: 1, e: true, f: "a"],
      [d: 1, e: true, f: "a"]
    )

    then:
    result
  }

  void 'checkObjectMatchesSubset matches identical maps in any order'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset(
      [d: 1, e: true, f: "a"],
      [f: "a", d: 1, e: true]
    )

    then:
    result
  }

  void 'checkObjectMatchesSubset map matches subset'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset(
      [d: 1, e: true, f: "a"],
      [f: "a", e: true]
    )

    then:
    result
  }

  void 'checkObjectMatchesSubset map matches empty map'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset(
      [d: 1, e: true, f: "a"],
      [:]
    )

    then:
    result
  }

  void 'checkObjectMatchesSubset map does not non-map'() {
    given:
    def object = [d: 1, e: true, f: "a"]

    def subsets = [
      [],
      "test",
      1,
      false
    ]

    when:
    boolean result = subsets.every{ false == TaskController.checkObjectMatchesSubset(object, it) }

    then:
    result
  }

  void 'checkObjectMatchesSubset matches identical complex maps'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset(
      [
        g: [],
        h: ["a", "b", "c"],
        i: [true, false],
        j: [:],
        k: "test",
        l: [x: 1, y: 2,
         z: [
           q: "asdf",
           r: [
             "t", "u", "v"
           ]
         ]
        ]
      ],
      [
        g: [],
        h: ["a", "b", "c"],
        i: [true, false],
        j: [:],
        k: "test",
        l: [x: 1, y: 2,
            z: [
              q: "asdf",
              r: [
                "t", "u", "v"
              ]
            ]
        ]
      ]
    )

    then:
    result
  }

  void 'checkObjectMatchesSubset matches identical complex maps in any order'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset(
      [
        g: [],
        h: ["a", "b", "c"],
        i: [true, false],
        j: [:],
        k: "test",
        l: [x: 1, y: 2,
            z: [
              q: "asdf",
              r: [
                "t", "u", "v"
              ]
            ]
        ]
      ],
      [
        l: [y: 2, x: 1,
         z: [
           r: [
             "v", "u", "t"
           ],
           q: "asdf"
         ]
        ],
        j: [:],
        k: "test",
        h: ["c", "a", "b"],
        i: [false, true],
        g: []
      ]
    )

    then:
    result
  }

  void 'checkObjectMatchesSubset complex map matches subset'() {
    when:
    boolean result = TaskController.checkObjectMatchesSubset(
      [
        g: [],
        h: ["a", "b", "c"],
        i: [true, false],
        j: [:],
        k: "test",
        l: [x: 1, y: 2,
            z: [
              q: "asdf",
              r: [
                "t", "u", "v"
              ]
            ]
        ]
      ],
      [
        l: [
          z: [
            r: [
              "t"
            ]
          ]
        ],
        k: "test",
        h: ["c", "b"],
        i: [false]
      ]
    )

    then:
    result
  }
}
