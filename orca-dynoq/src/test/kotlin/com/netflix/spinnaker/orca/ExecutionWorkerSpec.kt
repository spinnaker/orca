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
import com.netflix.spinnaker.orca.Command.RunTask
import com.netflix.spinnaker.orca.Event.*
import com.netflix.spinnaker.orca.Event.ConfigurationError.*
import com.netflix.spinnaker.orca.ExecutionStatus.*
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
import org.jetbrains.spek.api.dsl.context
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
    val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())

    val singleTaskStage = object : StageDefinitionBuilder {
      override fun getType() = "singleTaskStage"
      override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: TaskNode.Builder) {
        builder.withTask("dummy", DummyTask::class.java)
      }
    }

    val multiTaskStage = object : StageDefinitionBuilder {
      override fun getType() = "multiTaskStage"
      override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: TaskNode.Builder) {
        builder
          .withTask("dummy1", DummyTask::class.java)
          .withTask("dummy2", DummyTask::class.java)
          .withTask("dummy3", DummyTask::class.java)
      }
    }

    val executionWorker = ExecutionWorker(
      commandQ,
      eventQ,
      repository,
      clock,
      listOf(singleTaskStage, multiTaskStage)
    )

    describe("when disabled in discovery") {
      beforeGroup {
        executionWorker.onApplicationEvent(RemoteStatusChangedEvent(StatusChangeEvent(UP, OUT_OF_SERVICE)))
      }

      afterGroup {
        reset(commandQ, eventQ, repository)
      }

      action("the worker polls the queue") {
        executionWorker.pollOnce()
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
        }

        afterGroup {
          reset(commandQ, eventQ, repository)
        }

        action("the worker polls the queue") {
          executionWorker.pollOnce()
        }

        it("does nothing") {
          verifyZeroInteractions(commandQ, repository)
        }
      }

      describe("starting a stage") {
        describe("with a single initial task") {
          val event = StageStarting(Pipeline::class.java, "1", "1")
          val pipeline = Pipeline.builder().withId(event.executionId).build()
          val stage = PipelineStage(pipeline, singleTaskStage.type)

          beforeGroup {
            stage.id = event.stageId
            pipeline.stages.add(stage)
          }

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

          it("updates the stage status") {
            argumentCaptor<Stage<Pipeline>>().apply {
              verify(repository).storeStage(capture())
              assertThat(firstValue.status, equalTo(RUNNING))
              assertThat(firstValue.startTime, equalTo(clock.millis()))
            }
          }

          it("attaches tasks to the stage") {
            argumentCaptor<Stage<Pipeline>>().apply {
              verify(repository).storeStage(capture())
              assertThat(firstValue.tasks, hasSize(equalTo(1)))
              firstValue.tasks.first().apply {
                assertThat(id, equalTo("1"))
                assertThat(name, equalTo("dummy"))
                assertThat(implementingClass.name, equalTo(DummyTask::class.java.name))
                assertThat(isStageStart, equalTo(true))
                assertThat(isStageEnd, equalTo(true))
              }
            }
          }
        }

        describe("with several linear tasks") {
          val event = StageStarting(Pipeline::class.java, "1", "1")
          val pipeline = Pipeline.builder().withId(event.executionId).build()
          val stage = PipelineStage(pipeline, multiTaskStage.type)

          beforeGroup {
            stage.id = event.stageId
            pipeline.stages.add(stage)

            whenever(eventQ.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)

            executionWorker.pollOnce()
          }

          afterGroup {
            reset(commandQ, eventQ, repository)
          }

          it("attaches tasks to the stage") {
            argumentCaptor<Stage<Pipeline>>().apply {
              verify(repository).storeStage(capture())
              firstValue.apply {
                assertThat(tasks, hasSize(equalTo(3)))
                tasks[0].apply {
                  assertThat(id, equalTo("1"))
                  assertThat(name, equalTo("dummy1"))
                  assertThat(implementingClass.name, equalTo(DummyTask::class.java.name))
                  assertThat(isStageStart, equalTo(true))
                  assertThat(isStageEnd, equalTo(false))
                }
                tasks[1].apply {
                  assertThat(id, equalTo("2"))
                  assertThat(name, equalTo("dummy2"))
                  assertThat(implementingClass.name, equalTo(DummyTask::class.java.name))
                  assertThat(isStageStart, equalTo(false))
                  assertThat(isStageEnd, equalTo(false))
                }
                tasks[2].apply {
                  assertThat(id, equalTo("3"))
                  assertThat(name, equalTo("dummy3"))
                  assertThat(implementingClass.name, equalTo(DummyTask::class.java.name))
                  assertThat(isStageStart, equalTo(false))
                  assertThat(isStageEnd, equalTo(true))
                }
              }
            }
          }
        }
      }

      describe("when a task completes successfully") {
        val event = TaskComplete(Pipeline::class.java, "1", "1", "1", SUCCEEDED)

        describe("the stage contains further tasks") {
          val pipeline = Pipeline.builder().withId(event.executionId).build()
          val stage = PipelineStage(pipeline, multiTaskStage.type)

          beforeGroup {
            stage.id = event.stageId
            stage.buildTasks(multiTaskStage)
            pipeline.stages.add(stage)

            whenever(eventQ.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup {
            reset(commandQ, eventQ, repository)
          }

          action("the worker polls the queue") {
            executionWorker.pollOnce()
          }

          it("updates the task state in the stage") {
            argumentCaptor<Stage<Pipeline>>().apply {
              verify(repository).storeStage(capture())
              firstValue.tasks.first().apply {
                assertThat(status, equalTo(SUCCEEDED))
                assertThat(endTime, equalTo(clock.millis()))
              }
            }
          }

          it("runs the next task") {
            verify(commandQ)
              .push(RunTask(
                Pipeline::class.java,
                event.executionId,
                event.stageId,
                "2",
                DummyTask::class.java
              ))
          }
        }

        describe("the stage is complete") {
          val pipeline = Pipeline.builder().withId(event.executionId).build()
          val stage1 = PipelineStage(pipeline, singleTaskStage.type)
          val stage2 = PipelineStage(pipeline, singleTaskStage.type)

          beforeGroup {
            stage1.id = event.stageId
            stage1.buildTasks(singleTaskStage)
            pipeline.stages.add(stage1)

            stage2.id = "2"
            pipeline.stages.add(stage2)

            whenever(eventQ.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup {
            reset(commandQ, eventQ, repository)
          }

          action("the worker polls the queue") {
            executionWorker.pollOnce()
          }

          it("updates the task state in the stage") {
            argumentCaptor<Stage<Pipeline>>().apply {
              verify(repository).storeStage(capture())
              firstValue.tasks.last().apply {
                assertThat(status, equalTo(SUCCEEDED))
                assertThat(endTime, equalTo(clock.millis()))
              }
            }
          }

          it("emits an event to signal the stage is complete") {
            verify(eventQ)
              .push(StageComplete(
                event.executionType,
                event.executionId,
                event.stageId,
                SUCCEEDED
              ))
          }
        }
      }

      setOf(TERMINAL, CANCELED).forEach { status ->
        describe("when a task completes with $status status") {
          val event = TaskComplete(Pipeline::class.java, "1", "1", "1", status)
          val pipeline = Pipeline.builder().withId(event.executionId).build()
          val stage = PipelineStage(pipeline, multiTaskStage.type)

          beforeGroup {
            stage.id = event.stageId
            stage.buildTasks(multiTaskStage)
            pipeline.stages.add(stage)

            whenever(eventQ.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup {
            reset(commandQ, eventQ, repository)
          }

          action("the worker polls the queue") {
            executionWorker.pollOnce()
          }

          it("updates the task state in the stage") {
            argumentCaptor<Stage<Pipeline>>().apply {
              verify(repository).storeStage(capture())
              firstValue.tasks.first().apply {
                assertThat(status, equalTo(status))
                assertThat(endTime, equalTo(clock.millis()))
              }
            }
          }

          it("fails the stage") {
            verify(eventQ).push(StageComplete(
              event.executionType,
              event.executionId,
              event.stageId,
              status
            ))
          }

          it("does not run the next task") {
            verifyZeroInteractions(commandQ)
          }
        }
      }

      describe("when a stage completes successfully") {
        val event = StageComplete(Pipeline::class.java, "1", "1", SUCCEEDED)

        context("it is the last stage") {
          val pipeline = Pipeline.builder().withId(event.executionId).build()
          val stage = PipelineStage(pipeline, singleTaskStage.type)

          beforeGroup {
            stage.id = event.stageId
            pipeline.stages.add(stage)

            whenever(eventQ.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup {
            reset(commandQ, eventQ, repository)
          }

          action("the worker polls the queue") {
            executionWorker.pollOnce()
          }

          it("updates the stage state") {
            argumentCaptor<Stage<Pipeline>>().apply {
              verify(repository).storeStage(capture())
              assertThat(firstValue.status, equalTo(SUCCEEDED))
              assertThat(firstValue.endTime, equalTo(clock.millis()))
            }
          }

          it("emits an event indicating the pipeline is complete") {
            verify(eventQ).push(ExecutionComplete(
              event.executionType,
              event.executionId,
              SUCCEEDED
            ))
          }

          it("does not emit any commands") {
            verifyZeroInteractions(commandQ)
          }
        }

        context("there is a single downstream stage") {
          val pipeline = Pipeline.builder().withId(event.executionId).build()
          val stage1 = PipelineStage(pipeline, singleTaskStage.type)
          val stage2 = PipelineStage(pipeline, singleTaskStage.type)

          beforeGroup {
            stage1.id = event.stageId
            stage1.refId = event.stageId
            pipeline.stages.add(stage1)
            stage2.id = "2"
            stage2.requisiteStageRefIds = setOf(stage1.refId)
            pipeline.stages.add(stage2)

            whenever(eventQ.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup {
            reset(commandQ, eventQ, repository)
          }

          action("the worker polls the queue") {
            executionWorker.pollOnce()
          }

          it("updates the stage state") {
            argumentCaptor<Stage<Pipeline>>().apply {
              verify(repository).storeStage(capture())
              assertThat(firstValue.status, equalTo(SUCCEEDED))
              assertThat(firstValue.endTime, equalTo(clock.millis()))
            }
          }

          it("runs the next stage") {
            verify(eventQ).push(StageStarting(
              event.executionType,
              event.executionId,
              stage2.id
            ))
          }

          it("does not run any tasks") {
            verifyZeroInteractions(commandQ)
          }
        }

        context("there are multiple downstream stages") {
          val pipeline = Pipeline.builder().withId(event.executionId).build()
          val stage1 = PipelineStage(pipeline, singleTaskStage.type)
          val stage2 = PipelineStage(pipeline, singleTaskStage.type)
          val stage3 = PipelineStage(pipeline, singleTaskStage.type)

          beforeGroup {
            stage1.id = event.stageId
            stage1.refId = event.stageId
            pipeline.stages.add(stage1)
            stage2.id = "2"
            stage2.requisiteStageRefIds = setOf(stage1.refId)
            pipeline.stages.add(stage2)
            stage3.id = "3"
            stage3.requisiteStageRefIds = setOf(stage1.refId)
            pipeline.stages.add(stage3)

            whenever(eventQ.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup {
            reset(commandQ, eventQ, repository)
          }

          action("the worker polls the queue") {
            executionWorker.pollOnce()
          }

          it("runs the next stages") {
            argumentCaptor<StageStarting>().apply {
              verify(eventQ, times(2)).push(capture())
              assertThat(allValues.map { it.stageId }.toSet(), equalTo(setOf(stage2.id, stage3.id)))
            }
          }
        }
      }

      setOf(TERMINAL, CANCELED).forEach { status ->
        describe("when a stage fails with $status status") {
          val event = StageComplete(Pipeline::class.java, "1", "1", status)
          val pipeline = Pipeline.builder().withId(event.executionId).build()
          val stage1 = PipelineStage(pipeline, singleTaskStage.type)
          val stage2 = PipelineStage(pipeline, singleTaskStage.type)

          beforeGroup {
            stage1.id = event.stageId
            stage1.refId = event.stageId
            pipeline.stages.add(stage1)
            stage2.id = "2"
            stage2.requisiteStageRefIds = listOf(stage1.refId)
            pipeline.stages.add(stage2)

            whenever(eventQ.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup {
            reset(commandQ, eventQ, repository)
          }

          action("the worker polls the queue") {
            executionWorker.pollOnce()
          }

          it("updates the stage state") {
            argumentCaptor<Stage<Pipeline>>().apply {
              verify(repository).storeStage(capture())
              assertThat(firstValue.status, equalTo(status))
              assertThat(firstValue.endTime, equalTo(clock.millis()))
            }
          }

          it("does not run any downstream stages") {
            verify(eventQ, never()).push(isA<StageStarting>())
          }

          it("emits an event indicating the pipeline failed") {
            verify(eventQ).push(ExecutionComplete(
              event.executionType,
              event.executionId,
              status
            ))
          }
        }
      }

      describe("when an execution completes successfully") {
        val event = ExecutionComplete(Pipeline::class.java, "1", SUCCEEDED)
        val pipeline = Pipeline.builder().withId(event.executionId).build()

        beforeGroup {
          whenever(eventQ.poll())
            .thenReturn(event)
          whenever(repository.retrievePipeline(event.executionId))
            .thenReturn(pipeline)
        }

        afterGroup {
          reset(commandQ, eventQ, repository)
        }

        action("the worker polls the queue") {
          executionWorker.pollOnce()
        }

        it("updates the execution") {
          argumentCaptor<Pipeline>().apply {
            verify(repository).store(capture())
            assertThat(firstValue.status, equalTo(SUCCEEDED))
            assertThat(firstValue.endTime, equalTo(clock.millis()))
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
          }

          afterGroup {
            reset(commandQ, eventQ, repository)
          }

          action("the worker polls the queue") {
            executionWorker.pollOnce()
          }

          it("does not run any tasks") {
            verifyZeroInteractions(commandQ)
          }

          it("emits an error event") {
            verify(eventQ).push(isA<InvalidExecutionId>())
          }
        }

        describe("no such stage") {
          val pipeline = Pipeline.builder().withId(event.executionId).build()

          beforeGroup {
            whenever(eventQ.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup {
            reset(commandQ, eventQ, repository)
          }

          action("the worker polls the queue") {
            executionWorker.pollOnce()
          }

          it("does not run any tasks") {
            verifyZeroInteractions(commandQ)
          }

          it("emits an error event") {
            verify(eventQ).push(isA<InvalidStageId>())
          }
        }
      }

      setOf(
        InvalidExecutionId(Pipeline::class.java, "1"),
        InvalidStageId(Pipeline::class.java, "1", "1"),
        InvalidTaskType(Pipeline::class.java, "1", "1", InvalidTask::class.java.name)
      ).forEach { event ->
        describe("handing a ${event.javaClass.simpleName} event") {
          val pipeline = Pipeline.builder().withId(event.executionId).build()

          beforeGroup {
            whenever(eventQ.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup {
            reset(commandQ, eventQ, repository)
          }

          action("the worker polls the queue") {
            executionWorker.pollOnce()
          }

          it("marks the execution as terminal") {
            verify(eventQ).push(ExecutionComplete(
              Pipeline::class.java,
              event.executionId,
              TERMINAL
            ))
          }
        }
      }
    }
  }
})
