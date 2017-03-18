/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasSize
import com.netflix.appinfo.InstanceInfo.InstanceStatus.OUT_OF_SERVICE
import com.netflix.appinfo.InstanceInfo.InstanceStatus.UP
import com.netflix.discovery.StatusChangeEvent
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import com.netflix.spinnaker.orca.Event.StageStarting
import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(JUnitPlatform::class)
class ExecutionWorkerSpec : Spek({
  describe("execution workers") {

    val commandQ: CommandQueue = mock()
    val eventQ: EventQueue = mock()
    val repository: ExecutionRepository = mock()
    val builder = object : StageDefinitionBuilder {
      override fun getType() = "whatever"

      override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: TaskNode.Builder) {
        builder.withTask("dummy", DummyTask::class.java)
      }
    }
    val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())

    val executionWorker = ExecutionWorker(commandQ, eventQ, repository, clock, listOf(builder))

    describe("when disabled in discovery") {
      beforeGroup {
        executionWorker.onApplicationEvent(RemoteStatusChangedEvent(StatusChangeEvent(UP, OUT_OF_SERVICE)))
        executionWorker.pollOnce()
      }

      afterGroup {
        reset(commandQ, eventQ, repository)
      }

      it("does not poll the queue") {
        verify(eventQ, never()).poll()
      }
    }

    describe("when enabled in discovery") {
      val instanceUpEvent = RemoteStatusChangedEvent(StatusChangeEvent(OUT_OF_SERVICE, UP))

      beforeGroup {
        executionWorker.onApplicationEvent(instanceUpEvent)
      }

      describe("no events on the queue") {
        beforeGroup {
          whenever(eventQ.poll())
            .thenReturn(null)

          executionWorker.pollOnce()
        }

        afterGroup {
          reset(commandQ, eventQ, repository)
        }

        it("does nothing") {
          verifyZeroInteractions(commandQ, repository)
        }
      }

      describe("starting a stage") {

        val event = StageStarting(Pipeline::class.java, "1", "1")
        val pipeline = Pipeline.builder().withId(event.executionId).build()
        val stage = PipelineStage(pipeline, "whatever")

        beforeGroup {
          stage.id = event.stageId
          pipeline.stages.add(stage)
        }

        describe("with a single initial task") {
          val argumentCaptor = argumentCaptor<Stage<Pipeline>>()

          beforeGroup {
            whenever(eventQ.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)

            executionWorker.pollOnce()
          }

          afterGroup {
            reset(commandQ, eventQ, repository)
          }

          it ("updates the stage status") {
            verify(repository).storeStage(argumentCaptor.capture())
            argumentCaptor.firstValue.apply {
              assertThat(status, equalTo(RUNNING))
              assertThat(startTime, equalTo(clock.millis()))
            }
          }

          it ("attaches tasks to the stage") {
            verify(repository).storeStage(argumentCaptor.capture())
            argumentCaptor.firstValue.apply {
              assertThat(tasks, hasSize(equalTo(1)))
              tasks.first().apply {
                assertThat(id, equalTo("1"))
                assertThat(name, equalTo("dummy"))
                assertThat(implementingClass.name, equalTo(DummyTask::class.java.name))
                assertThat(isStageStart(), equalTo(true))
                assertThat(isStageEnd(), equalTo(true))
              }
            }
          }
        }
      }

      describe("invalid commands") {

        val event = StageStarting(Pipeline::class.java, "1", "1")

        describe("no such execution") {
          beforeGroup {
            whenever(eventQ.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenThrow(ExecutionNotFoundException("No Pipeline found for ${event.executionId}"))

            executionWorker.pollOnce()
          }

          afterGroup {
            reset(commandQ, eventQ, repository)
          }

          it("does not run any tasks") {
            verifyZeroInteractions(commandQ)
          }

          it("emits an error event") {
            verify(eventQ).push(isA<Event.ConfigurationError.InvalidExecutionId>())
          }
        }

        describe("no such stage") {
          val pipeline = Pipeline.builder().withId(event.executionId).build()

          beforeGroup {
            whenever(eventQ.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)

            executionWorker.pollOnce()
          }

          afterGroup {
            reset(commandQ, eventQ, repository)
          }

          it("does not run any tasks") {
            verifyZeroInteractions(commandQ)
          }

          it("emits an error event") {
            verify(eventQ).push(isA<Event.ConfigurationError.InvalidStageId>())
          }
        }
      }
      }
  }
})
