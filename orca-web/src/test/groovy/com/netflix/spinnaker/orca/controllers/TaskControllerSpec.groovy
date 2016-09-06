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

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import groovy.json.JsonSlurper
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.PipelineStartTracker
import com.netflix.spinnaker.orca.pipeline.model.*
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification
import static java.time.temporal.ChronoUnit.DAYS
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch

class TaskControllerSpec extends Specification {

  MockMvc mockMvc
  def executionRepository = Mock(ExecutionRepository)
  def front50Service = Mock(Front50Service)
  def startTracker = Mock(PipelineStartTracker)

  def clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  int daysOfExecutionHistory = 14
  int numberOfOldPipelineExecutionsToInclude = 2

  ObjectMapper objectMapper = new ObjectMapper()

  void setup() {
    mockMvc = MockMvcBuilders.standaloneSetup(
      new TaskController(
        front50Service: front50Service,
        executionRepository: executionRepository,
        daysOfExecutionHistory: daysOfExecutionHistory,
        numberOfOldPipelineExecutionsToInclude: numberOfOldPipelineExecutionsToInclude,
        startTracker: startTracker,
        clock: clock
      )
    ).build()
  }

  void '/tasks returns a list of active tasks'() {
    when:
    mockMvc.perform(get('/tasks')).andReturn().response

    then:
    1 * executionRepository.retrieveOrchestrations() >> {
      return rx.Observable.empty()
    }
  }

  void 'step names are properly translated'() {
    when:
    def response = mockMvc.perform(get('/tasks')).andReturn().response

    then:
    executionRepository.retrieveOrchestrations() >> rx.Observable.from([new Orchestration(
      stages: [new OrchestrationStage(tasks: [new DefaultTask(name: 'jobOne'), new DefaultTask(name: 'jobTwo')])])])
    with(new JsonSlurper().parseText(response.contentAsString).first()) {
      steps.name == ['jobOne', 'jobTwo']
    }

  }

  void 'stage contexts are included for orchestrated tasks'() {
    setup:
    def orchestration = new Orchestration(id: "1")
    orchestration.stages = [
      new OrchestrationStage(orchestration, "OrchestratedType", ["customOutput": "variable"])
    ]

    when:
    def response = mockMvc.perform(get('/tasks/1')).andReturn().response

    then:
    executionRepository.retrieveOrchestration(orchestration.id) >> orchestration

    new JsonSlurper().parseText(response.contentAsString).variables == [
      [key: "customOutput", value: "variable"]
    ]
  }

  void '/tasks returns [] when there are no tasks'() {
    when:
    MockHttpServletResponse response = mockMvc.perform(get('/tasks')).andReturn().response

    then:
    1 * executionRepository.retrieveOrchestrations() >> rx.Observable.from([])
    response.status == 200
    response.contentAsString == '[]'
  }

  void '/applications/{application}/tasks filters tasks by application'() {
    when:
    def response = mockMvc.perform(get("/applications/$app/tasks")).andReturn().response

    then:
    1 * executionRepository.retrieveOrchestrationsForApplication(app, _) >> rx.Observable.empty()

    where:
    app = "test"
  }

  void '/applications/{application}/tasks only returns un-started and tasks from the past two weeks, sorted newest first'() {
    given:
    def tasks = [
      [startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minusMillis(1).toEpochMilli(), id: 'too-old'] as Orchestration,
      [startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).plusMillis(1).toEpochMilli(), id: 'not-too-old'] as Orchestration,
      [startTime: clock.instant().minus(1, DAYS).toEpochMilli(), id: 'pretty-new'] as Orchestration,
      [id: 'not-started-1'] as Orchestration,
      [id: 'not-started-2'] as Orchestration
    ]
    def app = 'test'
    executionRepository.retrieveOrchestrationsForApplication(app, _) >> rx.Observable.from(tasks)

    when:
    def response = new ObjectMapper().readValue(
    mockMvc.perform(get("/applications/$app/tasks")).andReturn().response.contentAsString, ArrayList)

    then:
    response.id == ['not-started-2', 'not-started-1', 'not-too-old', 'pretty-new']
  }

  void '/applications/{application}/pipelines should only return pipelines from the past two weeks, newest first'() {
    given:
    def pipelines = [
      [pipelineConfigId: "1", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).minusMillis(1).toEpochMilli(), id: 'old'],
      [pipelineConfigId: "1", startTime: clock.instant().minus(daysOfExecutionHistory, DAYS).plusMillis(1).toEpochMilli(), id: 'newer'],
      [pipelineConfigId: "1", id: 'not-started'],
      [pipelineConfigId: "1", id: 'also-not-started'],

      /*
       * If a pipeline has no recent executions, the most recent N executions should be included
       */
      [pipelineConfigId: "2", id: 'older1', startTime: clock.instant().minus(daysOfExecutionHistory + 1, DAYS).minusMillis(1).toEpochMilli()],
      [pipelineConfigId: "2", id: 'older2', startTime: clock.instant().minus(daysOfExecutionHistory + 1, DAYS).minusMillis(2).toEpochMilli()],
      [pipelineConfigId: "2", id: 'older3', startTime: clock.instant().minus(daysOfExecutionHistory + 1, DAYS).minusMillis(3).toEpochMilli()]
    ]
    def app = 'test'

    executionRepository.retrievePipelinesForPipelineConfigId("1", _) >> rx.Observable.from(pipelines.findAll {
      it.pipelineConfigId == "1"
    }.collect {
      new Pipeline(id: it.id, startTime: it.startTime, pipelineConfigId: it.pipelineConfigId)
    })
    executionRepository.retrievePipelinesForPipelineConfigId("2", _) >> rx.Observable.from(pipelines.findAll {
      it.pipelineConfigId == "2"
    }.collect {
      new Pipeline(id: it.id, startTime: it.startTime, pipelineConfigId: it.pipelineConfigId)
    })
    front50Service.getPipelines(app) >> [[id: "1"], [id: "2"]]
    front50Service.getStrategies(app) >> []

    when:
    def response = mockMvc.perform(get("/applications/$app/pipelines")).andReturn().response
    List results = new ObjectMapper().readValue(response.contentAsString, List)

    then:
    results.id == ['not-started', 'also-not-started', 'older2', 'older1', 'newer']
  }

  void 'should update existing stage context'() {
    given:
    def pipelineStage = new PipelineStage(new Pipeline(), "", [value: "1"])
    pipelineStage.id = "s1"

    when:
    def response = mockMvc.perform(patch("/pipelines/p1/stages/s1").content(
      objectMapper.writeValueAsString([judgmentStatus: "stop"])
    ).contentType(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    1 * executionRepository.retrievePipeline("p1") >> {
      [
        stages: [pipelineStage]
      ]
    }
    1 * executionRepository.storeStage({ stage ->
      stage.id == "s1" &&
        stage.lastModified.allowedAccounts == [] &&
        stage.lastModified.user == "anonymous" &&
        stage.context == [
        judgmentStatus: "stop", value: "1", lastModifiedBy: "anonymous"
      ]
    } as PipelineStage)
    objectMapper.readValue(response.contentAsString, Map).stages*.context == [
      [value: "1", judgmentStatus: "stop", , lastModifiedBy: "anonymous"]
    ]
    0 * _
  }
}
