/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import java.util.concurrent.atomic.AtomicInteger
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import spock.lang.Specification

class TopApplicationExecutionCleanupPollingNotificationAgentSpec extends Specification {
  void "filter should only consider SUCCEEDED executions"() {
    given:
    def filter = new TopApplicationExecutionCleanupPollingNotificationAgent().filter

    expect:
    ExecutionStatus.values().each {
      def stage = new PipelineStage(new Pipeline(), "")
      stage.status = it

      filter.call(new Pipeline(stages: [stage])) == (it == ExecutionStatus.SUCCEEDED)
    }
  }

  void "mapper should extract id, startTime, status, and pipelineConfigId"() {
    given:
    def pipeline = new Pipeline()
    pipeline.id = "ID1"
    pipeline.pipelineConfigId = "P1"
    pipeline.startTime = 1000

    and:
    def mapper = new TopApplicationExecutionCleanupPollingNotificationAgent().mapper

    expect:
    with(mapper.call(pipeline)) {
      id == "ID1"
      startTime == 1000
      pipelineConfigId == "P1"
      status == ExecutionStatus.NOT_STARTED
    }
  }

  void "tick should cleanup each application with > threshold # of executions"() {
    given:
    def startTime = new AtomicInteger(0)
    def orchestrations = buildExecutions(startTime, 3)
    def pipelines = buildExecutions(startTime, 3, "P1") + buildExecutions(startTime, 5, "P2")

    def agent = new TopApplicationExecutionCleanupPollingNotificationAgent(threshold: 2)
    agent.jedisPool = Mock(Pool) {
      1 * getResource() >> {
        return Mock(Jedis) {
          1 * keys("orchestration:app:*") >> { ["orchestration:app:app1"] }
          1 * scard("orchestration:app:app1") >> { return orchestrations.size() }
          0 * _
        }
      }
    }
    agent.executionRepository = Mock(ExecutionRepository) {
      1 * retrieveOrchestrationsForApplication("app1", _) >> { return rx.Observable.from(orchestrations) }
      0 * _
    }

    when:
    agent.tick()

    then:
    1 * agent.executionRepository.deleteOrchestration(orchestrations[0].id)
  }

  private
  static Collection<Execution> buildExecutions(AtomicInteger startTime, int count, String pipelineConfigId = null) {
    (1..count).collect {
      def stage = new PipelineStage(new Pipeline(), "")
      stage.startTime = startTime.incrementAndGet()
      stage.status = ExecutionStatus.SUCCEEDED
      stage.tasks = [new DefaultTask()]

      def execution = new Pipeline(stages: [stage])
      execution.id = stage.startTime as String
      execution.pipelineConfigId = pipelineConfigId

      execution
    }
  }
}
