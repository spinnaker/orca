/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.notifications.scheduling

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Task
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class OldPipelineCleanupPollingNotificationAgentSpec extends Specification {

  void 'filter should only consider executions older than threshold'() {
    given:
    def clock = Mock(Clock) {
      millis() >> { Duration.ofDays(3).toMillis() }
    }
    def filter = new OldPipelineCleanupPollingNotificationAgent(
      Mock(NotificationClusterLock),
      Mock(PollingAgentExecutionRepository),
      clock,
      5000,
      1,
      5
    ).filter

    expect:
    filter.call(pipeline {
      status = ExecutionStatus.SUCCEEDED
      startTime = Duration.ofDays(1).toMillis()
    }) == true
    filter.call(pipeline {
      status = ExecutionStatus.RUNNING
      startTime = Duration.ofDays(1).toMillis()
    }) == false
    filter.call(pipeline {
      status = ExecutionStatus.SUCCEEDED
      startTime = Duration.ofDays(3).toMillis()
    }) == false
  }

  void 'mapper should extract id, app, pipelineConfigId, status, startTime and buildTime'() {
    given:
    def pipeline = pipeline {
      id = 'ID1'
      application = "orca"
      pipelineConfigId = 'P1'
      startTime = 1000
      buildTime = 1001
      status = ExecutionStatus.SUCCEEDED
    }

    and:
    def mapper = new OldPipelineCleanupPollingNotificationAgent(
      Mock(NotificationClusterLock),
      Mock(PollingAgentExecutionRepository),
      Mock(Clock),
      5000,
      1,
      5
    ).mapper

    expect:
    with(mapper.call(pipeline)) {
      id == "ID1"
      application == "orca"
      pipelineConfigId == "P1"
      startTime == 1000
      buildTime == 1001
      status == ExecutionStatus.SUCCEEDED
    }
  }

  void 'tick should cleanup pipeline with executions older than threshold, but no less than minimum execution limit'() {
    given:
    def startTime = new AtomicInteger(0)
    def pipelines = buildPipelines(startTime, 7, "P1")

    and:
    def clock = Mock(Clock) {
      millis() >> { Duration.ofDays(10).toMillis() }
    }
    def executionRepository = Mock(PollingAgentExecutionRepository) {
      1 * retrieveAllApplicationNames(PIPELINE) >> ["orca"]
      1 * retrievePipelinesForApplication("orca") >> rx.Observable.from(pipelines)
    }
    def agent = new OldPipelineCleanupPollingNotificationAgent(
      Mock(NotificationClusterLock),
      executionRepository,
      clock,
      5000,
      5,
      3
    )

    when:
    agent.tick()

    then:
    1 * executionRepository.delete(PIPELINE, '1')
    1 * executionRepository.delete(PIPELINE, '2')
  }

  private
  static Collection<Execution> buildPipelines(AtomicInteger stageStartTime, int count, String configId) {
    (1..count).collect {
      def time = stageStartTime.incrementAndGet()
      pipeline {
        id = time as String
        application = "orca"
        pipelineConfigId = configId
        startTime = Duration.ofDays(time).toMillis()
        buildTime = time
        status = ExecutionStatus.SUCCEEDED
        stage {
          type = "whatever"
          startTime = time
          status = ExecutionStatus.SUCCEEDED
          tasks = [new Task()]
        }
      }
    }
  }
}
