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

package com.netflix.spinnaker.orca.q

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import com.netflix.appinfo.InstanceInfo.InstanceStatus.OUT_OF_SERVICE
import com.netflix.appinfo.InstanceInfo.InstanceStatus.UP
import com.netflix.discovery.StatusChangeEvent
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.pipeline.BranchingStageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.newStage
import com.netflix.spinnaker.orca.pipeline.TaskNode.Builder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.model.Task
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.Message.*
import com.netflix.spinnaker.orca.q.Message.ConfigurationError.*
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import java.lang.RuntimeException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@RunWith(JUnitPlatform::class)
class ExecutionWorkerSpec : Spek({
  describe("execution workers") {
    val queue: Queue = mock()
    val repository: ExecutionRepository = mock()
    val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
    val task: DummyTask = mock()

    val singleTaskStage = object : StageDefinitionBuilder {
      override fun getType() = "singleTaskStage"
      override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: Builder) {
        builder.withTask("dummy", DummyTask::class.java)
      }
    }

    val multiTaskStage = object : StageDefinitionBuilder {
      override fun getType() = "multiTaskStage"
      override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: Builder) {
        builder
          .withTask("dummy1", DummyTask::class.java)
          .withTask("dummy2", DummyTask::class.java)
          .withTask("dummy3", DummyTask::class.java)
      }
    }

    val stageWithSyntheticBefore = object : StageDefinitionBuilder {
      override fun getType() = "stageWithSyntheticBefore"
      override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: Builder) {
        builder.withTask("dummy", DummyTask::class.java)
      }

      override fun <T : Execution<T>> aroundStages(stage: Stage<T>) = listOf(
        newStage(stage.execution, singleTaskStage.type, "pre1", mutableMapOf(), stage, STAGE_BEFORE),
        newStage(stage.execution, singleTaskStage.type, "pre2", mutableMapOf(), stage, STAGE_BEFORE)
      )
    }

    val stageWithSyntheticAfter = object : StageDefinitionBuilder {
      override fun getType() = "stageWithSyntheticAfter"
      override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: Builder) {
        builder.withTask("dummy", DummyTask::class.java)
      }

      override fun <T : Execution<T>> aroundStages(stage: Stage<T>) = listOf(
        newStage(stage.execution, singleTaskStage.type, "post1", mutableMapOf(), stage, STAGE_AFTER),
        newStage(stage.execution, singleTaskStage.type, "post2", mutableMapOf(), stage, STAGE_AFTER)
      )
    }

    val stageWithParallelBranches = object : BranchingStageDefinitionBuilder {
      override fun <T : Execution<T>> parallelContexts(stage: Stage<T>): Collection<Map<String, Any>> =
        listOf(
          mapOf("region" to "us-east-1"),
          mapOf("region" to "us-west-2"),
          mapOf("region" to "eu-west-1")
        )

      override fun preBranchGraph(stage: Stage<*>, builder: Builder) {
        builder.withTask("pre-branch", DummyTask::class.java)
      }

      override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: Builder) {
        builder.withTask("in-branch", DummyTask::class.java)
      }

      override fun postBranchGraph(stage: Stage<*>, builder: Builder) {
        builder.withTask("post-branch", DummyTask::class.java)
      }
    }

    val rollingPushStage = object : StageDefinitionBuilder {
      override fun getType() = "rolling"
      override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: Builder) {
        builder
          .withTask("beforeLoop", DummyTask::class.java)
          .withLoop { subGraph ->
            subGraph
              .withTask("startLoop", DummyTask::class.java)
              .withTask("inLoop", DummyTask::class.java)
              .withTask("endLoop", DummyTask::class.java)
          }
          .withTask("afterLoop", DummyTask::class.java)
      }
    }

    val worker = ExecutionWorker(
      queue,
      repository,
      clock,
      listOf(
        singleTaskStage,
        multiTaskStage,
        stageWithSyntheticBefore,
        stageWithSyntheticAfter,
        stageWithParallelBranches,
        rollingPushStage
      ),
      listOf(task)
    )

    fun resetMocks() = reset(queue, repository, task)

    describe("when disabled in discovery") {
      beforeGroup {
        worker.onApplicationEvent(RemoteStatusChangedEvent(StatusChangeEvent(UP, OUT_OF_SERVICE)))
      }

      afterGroup(::resetMocks)

      action("the worker runs") {
        worker.pollOnce()
      }

      it("does not poll the queue") {
        verifyZeroInteractions(queue)
      }
    }

    describe("when enabled in discovery") {
      val instanceUpEvent = RemoteStatusChangedEvent(StatusChangeEvent(OUT_OF_SERVICE, UP))

      beforeGroup {
        worker.onApplicationEvent(instanceUpEvent)
      }

      describe("no events on the queue") {
        beforeGroup {
          whenever(queue.poll())
            .thenReturn(null)
        }

        afterGroup(::resetMocks)

        action("the worker polls the queue") {
          worker.pollOnce()
        }

        it("does not try to ack non-existent messages") {
          verify(queue, never()).ack(anyOrNull())
        }
      }

      describe("starting an execution") {
        context("with a single initial stage") {
          val pipeline = pipeline {
            stage {
              type = singleTaskStage.type
            }
          }
          val event = ExecutionStarting(Pipeline::class.java, pipeline.id)

          beforeGroup {
            whenever(queue.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("marks the execution as running") {
            verify(repository).updateStatus(event.executionId, RUNNING)
          }

          it("starts the first stage") {
            verify(queue).push(StageStarting(
              event.executionType,
              event.executionId,
              pipeline.stages.first().id
            ))
          }

          it("acks the message") {
            verify(queue).ack(event)
          }
        }

        context("with multiple initial stages") {
          val pipeline = pipeline {
            stage {
              type = singleTaskStage.type
            }
            stage {
              type = singleTaskStage.type
            }
          }
          val event = ExecutionStarting(Pipeline::class.java, pipeline.id)

          beforeGroup {
            whenever(queue.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("starts all the initial stages") {
            argumentCaptor<StageStarting>().apply {
              verify(queue, times(2)).push(capture())
              assertThat(
                allValues.map { it.stageId }.toSet(),
                equalTo(pipeline.stages.map { it.id }.toSet())
              )
            }
          }
        }
      }

      describe("starting a stage") {
        context("with a single initial task") {
          val pipeline = pipeline {
            stage {
              type = singleTaskStage.type
            }
          }
          val event = StageStarting(Pipeline::class.java, pipeline.id, pipeline.stages.first().id)

          beforeGroup {
            whenever(queue.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
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

          it("raises an event to indicate the task is starting") {
            verify(queue).push(TaskStarting(
              event.executionType,
              event.executionId,
              event.stageId,
              "1"
            ))
          }

          it("acks the message") {
            verify(queue).ack(event)
          }
        }

        context("with several linear tasks") {
          val pipeline = pipeline {
            stage {
              type = multiTaskStage.type
            }
          }
          val event = StageStarting(Pipeline::class.java, pipeline.id, pipeline.stages.first().id)

          beforeGroup {
            whenever(queue.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          afterGroup(::resetMocks)

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

          it("raises an event to indicate the first task is starting") {
            verify(queue).push(TaskStarting(
              event.executionType,
              event.executionId,
              event.stageId,
              "1"
            ))
          }
        }

        context("with synthetic stages") {
          context("before the main stage") {
            val pipeline = pipeline {
              stage {
                type = stageWithSyntheticBefore.type
              }
            }
            val event = StageStarting(Pipeline::class.java, pipeline.id, pipeline.stages.first().id)

            beforeGroup {
              whenever(queue.poll())
                .thenReturn(event)
              whenever(repository.retrievePipeline(event.executionId))
                .thenReturn(pipeline)
            }

            action("the worker polls the queue") {
              worker.pollOnce()
            }

            afterGroup(::resetMocks)

            it("attaches the synthetic stage to the pipeline") {
              argumentCaptor<Pipeline>().apply {
                verify(repository).store(capture())
                assertThat(firstValue.stages.size, equalTo(3))
                assertThat(firstValue.stages.map { it.id }, equalTo(listOf("${event.stageId}-1-pre1", "${event.stageId}-2-pre2", event.stageId)))
              }
            }

            it("raises an event to indicate the synthetic stage is starting") {
              verify(queue).push(StageStarting(
                event.executionType,
                event.executionId,
                pipeline.stages.first().id
              ))
            }
          }

          context("after the main stage") {
            val pipeline = pipeline {
              stage {
                type = stageWithSyntheticAfter.type
              }
            }
            val event = StageStarting(Pipeline::class.java, pipeline.id, pipeline.stages.first().id)

            beforeGroup {
              whenever(queue.poll())
                .thenReturn(event)
              whenever(repository.retrievePipeline(event.executionId))
                .thenReturn(pipeline)
            }

            afterGroup(::resetMocks)

            action("the worker polls the queue") {
              worker.pollOnce()
            }

            it("attaches the synthetic stage to the pipeline") {
              argumentCaptor<Pipeline>().apply {
                verify(repository).store(capture())
                assertThat(firstValue.stages.size, equalTo(3))
                assertThat(firstValue.stages.map { it.id }, equalTo(listOf(event.stageId, "${event.stageId}-1-post1", "${event.stageId}-2-post2")))
              }
            }

            it("raises an event to indicate the first task is starting") {
              verify(queue).push(TaskStarting(
                event.executionType,
                event.executionId,
                event.stageId,
                "1"
              ))
            }
          }
        }

        context("with other upstream stages that are incomplete") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              status = SUCCEEDED
              type = singleTaskStage.type
            }
            stage {
              refId = "2"
              status = RUNNING
              type = singleTaskStage.type
            }
            stage {
              refId = "3"
              requisiteStageRefIds = setOf("1", "2")
              type = singleTaskStage.type
            }
          }
          val event = StageStarting(Pipeline::class.java, pipeline.id, pipeline.stageByRef("3").id)

          beforeGroup {
            whenever(queue.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("doesn't build its tasks") {
            assertThat(pipeline.stageByRef("3").tasks, isEmpty)
          }

          it("waits for the other upstream stage to complete") {
            verify(queue, never()).push(isA<TaskStarting>())
          }
        }
      }

      describe("when a task starts") {
        val pipeline = pipeline {
          stage {
            type = singleTaskStage.type
            singleTaskStage.buildTasks(this)
          }
        }
        val event = TaskStarting(Pipeline::class.java, pipeline.id, pipeline.stages.first().id, "1")

        beforeGroup {
          whenever(queue.poll())
            .thenReturn(event)
          whenever(repository.retrievePipeline(event.executionId))
            .thenReturn(pipeline)
        }

        afterGroup(::resetMocks)

        action("the worker polls the queue") {
          worker.pollOnce()
        }

        it("marks the task as running") {
          argumentCaptor<Stage<Pipeline>>().apply {
            verify(repository).storeStage(capture())
            firstValue.tasks.first().apply {
              assertThat(status, equalTo(RUNNING))
              assertThat(startTime, equalTo(clock.millis()))
            }
          }
        }

        it("runs the task") {
          verify(queue).push(RunTask(
            event.executionType,
            event.executionId,
            event.stageId,
            event.taskId,
            DummyTask::class.java
          ))
        }

        it("acks the message") {
          verify(queue).ack(event)
        }
      }

      describe("running a task") {
        val pipeline = pipeline {
          stage {
            type = "whatever"
          }
        }
        val command = RunTask(Pipeline::class.java, pipeline.id, pipeline.stages.first().id, "1", DummyTask::class.java)

        describe("that completes successfully") {
          val taskResult = DefaultTaskResult(SUCCEEDED)

          beforeGroup {
            whenever(queue.poll())
              .thenReturn(command)
            whenever(task.execute(any<Stage<*>>()))
              .thenReturn(taskResult)
            whenever(repository.retrievePipeline(command.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("executes the task") {
            verify(task).execute(pipeline.stages.first())
          }

          it("emits a failure event") {
            argumentCaptor<TaskComplete>().apply {
              verify(queue).push(capture())
              assertThat(firstValue.status, equalTo(SUCCEEDED))
            }
          }

          it("acks the message") {
            verify(queue).ack(command)
          }
        }

        describe("that is not yet complete") {
          val taskResult = DefaultTaskResult(RUNNING)
          val taskBackoffMs = 30_000L

          beforeGroup {
            whenever(queue.poll())
              .thenReturn(command)
            whenever(task.execute(any()))
              .thenReturn(taskResult)
            whenever(task.backoffPeriod)
              .thenReturn(taskBackoffMs)
            whenever(repository.retrievePipeline(command.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("re-queues the command") {
            verify(queue).push(command, taskBackoffMs, TimeUnit.MILLISECONDS)
          }

          it("acks the message") {
            verify(queue).ack(command)
          }
        }

        describe("that fails") {
          val taskResult = DefaultTaskResult(TERMINAL)

          beforeGroup {
            whenever(queue.poll())
              .thenReturn(command)
            whenever(task.execute(any()))
              .thenReturn(taskResult)
            whenever(repository.retrievePipeline(command.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("emits a failure event") {
            argumentCaptor<TaskComplete>().apply {
              verify(queue).push(capture())
              assertThat(firstValue.status, equalTo(TERMINAL))
            }
          }

          it("acks the message") {
            verify(queue).ack(command)
          }
        }

        describe("that throws an exception") {
          beforeGroup {
            whenever(queue.poll())
              .thenReturn(command)
            whenever(task.execute(any()))
              .thenThrow(RuntimeException("o noes"))
            whenever(repository.retrievePipeline(command.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("emits a failure event") {
            argumentCaptor<TaskComplete>().apply {
              verify(queue).push(capture())
              assertThat(firstValue.status, equalTo(TERMINAL))
            }
          }

          it("acks the message") {
            verify(queue).ack(command)
          }
        }

        describe("when the execution has stopped") {
          beforeGroup {
            pipeline.status = TERMINAL

            whenever(queue.poll())
              .thenReturn(command)
            whenever(repository.retrievePipeline(command.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("emits an event indicating that the task was canceled") {
            verify(queue).push(TaskComplete(
              command.executionType,
              command.executionId,
              command.stageId,
              command.taskId,
              CANCELED
            ))
          }

          it("does not execute the task") {
            verifyZeroInteractions(task)
          }

          it("acks the message") {
            verify(queue).ack(command)
          }
        }
      }

      describe("when a task completes successfully") {
        describe("the stage contains further tasks") {
          val pipeline = pipeline {
            stage {
              type = multiTaskStage.type
              multiTaskStage.buildTasks(this)
            }
          }
          val event = TaskComplete(Pipeline::class.java, pipeline.id, pipeline.stages.first().id, "1", SUCCEEDED)

          beforeGroup {
            whenever(queue.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
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
            verify(queue)
              .push(TaskStarting(
                Pipeline::class.java,
                event.executionId,
                event.stageId,
                "2"
              ))
          }

          it("acks the message") {
            verify(queue).ack(event)
          }
        }

        describe("the stage is complete") {
          val pipeline = pipeline {
            stage {
              type = singleTaskStage.type
              singleTaskStage.buildTasks(this)
            }
          }
          val event = TaskComplete(Pipeline::class.java, pipeline.id, pipeline.stages.first().id, "1", SUCCEEDED)

          beforeGroup {
            whenever(queue.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
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
            verify(queue)
              .push(StageComplete(
                event.executionType,
                event.executionId,
                event.stageId,
                SUCCEEDED
              ))
          }
        }

        context("the stage has synthetic after stages") {
          val pipeline = pipeline {
            stage {
              type = stageWithSyntheticAfter.type
              stageWithSyntheticAfter.buildTasks(this)
              stageWithSyntheticAfter.buildSyntheticStages(this)
            }
          }
          val event = TaskComplete(Pipeline::class.java, pipeline.id, pipeline.stages.first().id, "1", SUCCEEDED)

          beforeGroup {
            whenever(queue.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
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

          it("triggers the first after stage") {
            verify(queue)
              .push(StageStarting(
                event.executionType,
                event.executionId,
                pipeline.stages[1].id
              ))
          }
        }
      }

      setOf(TERMINAL, CANCELED).forEach { status ->
        describe("when a task completes with $status status") {
          val pipeline = pipeline {
            stage {
              type = multiTaskStage.type
              multiTaskStage.buildTasks(this)
            }
          }
          val event = TaskComplete(Pipeline::class.java, pipeline.id, pipeline.stages.first().id, "1", status)

          beforeGroup {
            whenever(queue.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
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
            verify(queue).push(StageComplete(
              event.executionType,
              event.executionId,
              event.stageId,
              status
            ))
          }

          it("does not run the next task") {
            verify(queue, never()).push(any<RunTask>())
          }

          it("acks the message") {
            verify(queue).ack(event)
          }
        }
      }

      describe("when a stage completes successfully") {
        context("it is the last stage") {
          val pipeline = pipeline {
            stage {
              type = singleTaskStage.type
            }
          }
          val event = StageComplete(Pipeline::class.java, pipeline.id, pipeline.stages.first().id, SUCCEEDED)

          beforeGroup {
            whenever(queue.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("updates the stage state") {
            argumentCaptor<Stage<Pipeline>>().apply {
              verify(repository).storeStage(capture())
              assertThat(firstValue.status, equalTo(SUCCEEDED))
              assertThat(firstValue.endTime, equalTo(clock.millis()))
            }
          }

          it("emits an event indicating the pipeline is complete") {
            verify(queue).push(ExecutionComplete(
              event.executionType,
              event.executionId,
              SUCCEEDED
            ))
          }

          it("does not emit any commands") {
            verify(queue, never()).push(any<RunTask>())
          }

          it("acks the message") {
            verify(queue).ack(event)
          }
        }

        context("there is a single downstream stage") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              type = singleTaskStage.type
            }
            stage {
              refId = "2"
              requisiteStageRefIds = setOf("1")
              type = singleTaskStage.type
            }
          }
          val event = StageComplete(Pipeline::class.java, pipeline.id, pipeline.stages.first().id, SUCCEEDED)

          beforeGroup {
            whenever(queue.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("updates the stage state") {
            argumentCaptor<Stage<Pipeline>>().apply {
              verify(repository).storeStage(capture())
              assertThat(firstValue.status, equalTo(SUCCEEDED))
              assertThat(firstValue.endTime, equalTo(clock.millis()))
            }
          }

          it("runs the next stage") {
            verify(queue).push(StageStarting(
              event.executionType,
              event.executionId,
              pipeline.stages.last().id
            ))
          }

          it("does not run any tasks") {
            verify(queue, never()).push(any<RunTask>())
          }
        }

        context("there are multiple downstream stages") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              type = singleTaskStage.type
            }
            stage {
              refId = "2"
              requisiteStageRefIds = setOf("1")
              type = singleTaskStage.type
            }
            stage {
              refId = "3"
              requisiteStageRefIds = setOf("1")
              type = singleTaskStage.type
            }
          }
          val event = StageComplete(Pipeline::class.java, pipeline.id, pipeline.stages.first().id, SUCCEEDED)

          beforeGroup {
            whenever(queue.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("runs the next stages") {
            argumentCaptor<StageStarting>().apply {
              verify(queue, times(2)).push(capture())
              assertThat(
                allValues.map { it.stageId }.toSet(),
                equalTo(pipeline.stages[1..2].map { it.id }.toSet())
              )
            }
          }
        }
      }

      setOf(TERMINAL, CANCELED).forEach { status ->
        describe("when a stage fails with $status status") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              type = singleTaskStage.type
            }
            stage {
              refId = "2"
              requisiteStageRefIds = listOf("1")
              type = singleTaskStage.type
            }
          }
          val event = StageComplete(Pipeline::class.java, pipeline.id, pipeline.stages.first().id, status)

          beforeGroup {
            whenever(queue.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("updates the stage state") {
            argumentCaptor<Stage<Pipeline>>().apply {
              verify(repository).storeStage(capture())
              assertThat(firstValue.status, equalTo(status))
              assertThat(firstValue.endTime, equalTo(clock.millis()))
            }
          }

          it("does not run any downstream stages") {
            verify(queue, never()).push(isA<StageStarting>())
          }

          it("emits an event indicating the pipeline failed") {
            verify(queue).push(ExecutionComplete(
              event.executionType,
              event.executionId,
              status
            ))
          }

          it("acks the message") {
            verify(queue).ack(event)
          }
        }
      }

      describe("when a synthetic stage completes successfully") {
        context("before the main stage") {
          val pipeline = pipeline {
            stage {
              type = stageWithSyntheticBefore.type
              stageWithSyntheticBefore.buildSyntheticStages(this)
              stageWithSyntheticBefore.buildTasks(this)
            }
          }

          context("there are more before stages") {
            val event = StageComplete(Pipeline::class.java, pipeline.id, pipeline.stages.first().id, SUCCEEDED)
            beforeGroup {
              whenever(repository.retrievePipeline(pipeline.id))
                .thenReturn(pipeline)
              whenever(queue.poll())
                .thenReturn(event)
            }

            afterGroup(::resetMocks)

            action("the worker polls the queue") {
              worker.pollOnce()
            }

            it("runs the next synthetic stage") {
              verify(queue).push(StageStarting(
                event.executionType,
                event.executionId,
                pipeline.stages[1].id
              ))
            }
          }

          context("it is the last before stage") {
            val event = StageComplete(Pipeline::class.java, pipeline.id, pipeline.stages[1].id, SUCCEEDED)
            beforeGroup {
              whenever(repository.retrievePipeline(pipeline.id))
                .thenReturn(pipeline)
              whenever(queue.poll())
                .thenReturn(event)
            }

            afterGroup(::resetMocks)

            action("the worker polls the queue") {
              worker.pollOnce()
            }

            it("runs the next synthetic stage") {
              verify(queue).push(TaskStarting(
                event.executionType,
                event.executionId,
                pipeline.stages[2].id,
                "1"
              ))
            }
          }
        }

        context("after the main stage") {
          val pipeline = pipeline {
            stage {
              type = stageWithSyntheticAfter.type
              stageWithSyntheticAfter.buildSyntheticStages(this)
              stageWithSyntheticAfter.buildTasks(this)
            }
          }

          context("there are more after stages") {
            val event = StageComplete(Pipeline::class.java, pipeline.id, pipeline.stages[1].id, SUCCEEDED)
            beforeGroup {
              whenever(repository.retrievePipeline(pipeline.id))
                .thenReturn(pipeline)
              whenever(queue.poll())
                .thenReturn(event)
            }

            afterGroup(::resetMocks)

            action("the worker polls the queue") {
              worker.pollOnce()
            }

            it("runs the next synthetic stage") {
              verify(queue).push(StageStarting(
                event.executionType,
                event.executionId,
                pipeline.stages.last().id
              ))
            }
          }

          context("it is the last after stage") {
            val event = StageComplete(Pipeline::class.java, pipeline.id, pipeline.stages.last().id, SUCCEEDED)
            beforeGroup {
              whenever(repository.retrievePipeline(pipeline.id))
                .thenReturn(pipeline)
              whenever(queue.poll())
                .thenReturn(event)
            }

            afterGroup(::resetMocks)

            action("the worker polls the queue") {
              worker.pollOnce()
            }

            it("signals the completion of the parent stage") {
              verify(queue).push(StageComplete(
                event.executionType,
                event.executionId,
                pipeline.stages.first().id,
                SUCCEEDED
              ))
            }
          }
        }
      }

      describe("when an execution completes successfully") {
        val pipeline = pipeline { }
        val event = ExecutionComplete(Pipeline::class.java, pipeline.id, SUCCEEDED)

        beforeGroup {
          whenever(queue.poll())
            .thenReturn(event)
          whenever(repository.retrievePipeline(event.executionId))
            .thenReturn(pipeline)
        }

        afterGroup(::resetMocks)

        action("the worker polls the queue") {
          worker.pollOnce()
        }

        it("updates the execution") {
          verify(repository).updateStatus(event.executionId, SUCCEEDED)
        }

        it("acks the message") {
          verify(queue).ack(event)
        }
      }

      describe("running a branching stage") {
        context("when the stage starts") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              type = stageWithParallelBranches.type
            }
          }
          val event = StageStarting(Pipeline::class.java, pipeline.id, pipeline.stageByRef("1").id)

          beforeGroup {
            whenever(repository.retrievePipeline(pipeline.id))
              .thenReturn(pipeline)
            whenever(queue.poll()).thenReturn(event)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("builds tasks for the main branch") {
            val stage = pipeline.stageById(event.stageId)
            assertThat(stage.tasks.map(Task::getName), equalTo(listOf("post-branch")))
          }

          it("builds synthetic stages for each parallel branch") {
            assertThat(pipeline.stages.size, equalTo(4))
            assertThat(
              pipeline.stages.map { it.type },
              allElements(equalTo(stageWithParallelBranches.type))
            )
            // TODO: contexts, etc.
          }

          it("runs the parallel stages") {
            argumentCaptor<StageStarting>().apply {
              verify(queue, times(3)).push(capture())
              assertThat(
                allValues.map { pipeline.stageById(it.stageId).parentStageId },
                allElements(equalTo(event.stageId))
              )
            }
          }
        }

        context("when one branch starts") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              type = stageWithParallelBranches.type
              stageWithParallelBranches.buildSyntheticStages(this)
              stageWithParallelBranches.buildTasks(this)
            }
          }
          val event = StageStarting(Pipeline::class.java, pipeline.id, pipeline.stages[0].id)

          beforeGroup {
            whenever(repository.retrievePipeline(pipeline.id))
              .thenReturn(pipeline)
            whenever(queue.poll()).thenReturn(event)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("builds tasks for the branch") {
            val stage = pipeline.stageById(event.stageId)
            assertThat(stage.tasks, !isEmpty)
            assertThat(stage.tasks.map(Task::getName), equalTo(listOf("in-branch")))
          }

          it("does not build more synthetic stages") {
            val stage = pipeline.stageById(event.stageId)
            assertThat(pipeline.stages.map(Stage<Pipeline>::getParentStageId), !hasElement(stage.id))
          }
        }

        context("when one branch completes") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              type = stageWithParallelBranches.type
              stageWithParallelBranches.buildSyntheticStages(this)
              stageWithParallelBranches.buildTasks(this)
            }
          }
          val event = StageComplete(Pipeline::class.java, pipeline.id, pipeline.stages[0].id, SUCCEEDED)

          beforeGroup {
            whenever(repository.retrievePipeline(pipeline.id))
              .thenReturn(pipeline)
            whenever(queue.poll()).thenReturn(event)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("waits for other branches to finish") {
            verify(queue, never()).push(any())
          }
        }

        context("when all branches are complete") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              type = stageWithParallelBranches.type
              stageWithParallelBranches.buildSyntheticStages(this)
              stageWithParallelBranches.buildTasks(this)
            }
          }
          val event = StageComplete(Pipeline::class.java, pipeline.id, pipeline.stages[0].id, SUCCEEDED)

          beforeGroup {
            pipeline.stages.forEach {
              if (it.syntheticStageOwner == STAGE_BEFORE && it.id != event.stageId) {
                it.status = SUCCEEDED
              }
            }

            whenever(repository.retrievePipeline(pipeline.id))
              .thenReturn(pipeline)
            whenever(queue.poll()).thenReturn(event)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("runs any post-branch tasks") {
            verify(queue).push(isA<TaskStarting>())
          }
        }
      }

      describe("running a rolling push stage") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            type = rollingPushStage.type
          }
        }

        context("when the stage starts") {
          val event = StageStarting(Pipeline::class.java, pipeline.id, pipeline.stageByRef("1").id)

          beforeGroup {
            whenever(repository.retrievePipeline(pipeline.id))
              .thenReturn(pipeline)
            whenever(queue.poll()).thenReturn(event)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("builds tasks for the main branch") {
            pipeline.stageById(event.stageId).let { stage ->
              assertThat(stage.tasks.size, equalTo(5))
              assertThat(stage.tasks[0].isLoopStart, equalTo(false))
              assertThat(stage.tasks[1].isLoopStart, equalTo(true))
              assertThat(stage.tasks[2].isLoopStart, equalTo(false))
              assertThat(stage.tasks[3].isLoopStart, equalTo(false))
              assertThat(stage.tasks[4].isLoopStart, equalTo(false))
              assertThat(stage.tasks[0].isLoopEnd, equalTo(false))
              assertThat(stage.tasks[1].isLoopEnd, equalTo(false))
              assertThat(stage.tasks[2].isLoopEnd, equalTo(false))
              assertThat(stage.tasks[3].isLoopEnd, equalTo(true))
              assertThat(stage.tasks[4].isLoopEnd, equalTo(false))
            }
          }

          it("runs the parallel stages") {
            argumentCaptor<TaskStarting>().apply {
              verify(queue).push(capture())
              assertThat(firstValue.taskId, equalTo("1"))
            }
          }
        }

        context("when the last task in the loop returns REDIRECT") {
          val event = TaskComplete(Pipeline::class.java, pipeline.id, pipeline.stageByRef("1").id, "4", REDIRECT)

          beforeGroup {
            pipeline.stageByRef("1").apply {
              tasks[0].status = SUCCEEDED
              tasks[1].status = SUCCEEDED
              tasks[2].status = SUCCEEDED
            }

            whenever(repository.retrievePipeline(pipeline.id))
              .thenReturn(pipeline)
            whenever(queue.poll()).thenReturn(event)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("repeats the loop") {
            argumentCaptor<TaskStarting>().apply {
              verify(queue).push(capture())
              assertThat(firstValue.taskId, equalTo("2"))
            }
          }

          it("resets the status of the loop tasks") {
            argumentCaptor<Stage<Pipeline>>().apply {
              // TODO: shouldn't be invoked twice
              verify(repository, times(2)).storeStage(capture())
              assertThat(secondValue.tasks[1..3].map(Task::getStatus), allElements(equalTo(NOT_STARTED)))
            }
          }
        }
      }

      setOf(TERMINAL, CANCELED).forEach { status ->
        describe("when an execution fails with $status status") {
          val pipeline = pipeline { }
          val event = ExecutionComplete(Pipeline::class.java, pipeline.id, status)

          beforeGroup {
            whenever(queue.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("updates the execution") {
            verify(repository).updateStatus(event.executionId, status)
          }

          it("acks the message") {
            verify(queue).ack(event)
          }
        }
      }

      describe("invalid commands") {

        val event = StageStarting(Pipeline::class.java, "1", "1")

        describe("no such execution") {
          beforeGroup {
            whenever(queue.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenThrow(ExecutionNotFoundException("No Pipeline found for ${event.executionId}"))
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("emits an error event") {
            verify(queue).push(isA<InvalidExecutionId>())
          }
        }

        describe("no such stage") {
          val pipeline = pipeline {
            id = event.executionId
          }

          beforeGroup {
            whenever(queue.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("emits an error event") {
            verify(queue).push(isA<InvalidStageId>())
          }
        }

        describe("no such task") {
          val pipeline = pipeline {
            stage {
              type = "whatever"
            }
          }
          val command = RunTask(Pipeline::class.java, pipeline.id, pipeline.stages.first().id, "1", InvalidTask::class.java)

          beforeGroup {
            whenever(queue.poll())
              .thenReturn(command)
            whenever(repository.retrievePipeline(command.executionId))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("does not run any tasks") {
            verifyZeroInteractions(task)
          }

          it("emits an error event") {
            verify(queue).push(isA<InvalidTaskType>())
          }

          it("acks the message") {
            verify(queue).ack(command)
          }
        }
      }

      setOf(
        InvalidExecutionId(Pipeline::class.java, "1"),
        InvalidStageId(Pipeline::class.java, "1", "1"),
        InvalidTaskType(Pipeline::class.java, "1", "1", InvalidTask::class.java.name)
      ).forEach { event ->
        describe("handing a ${event.javaClass.simpleName} event") {
          beforeGroup {
            whenever(queue.poll())
              .thenReturn(event)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker.pollOnce()
          }

          it("marks the execution as terminal") {
            verify(queue).push(ExecutionComplete(
              Pipeline::class.java,
              event.executionId,
              TERMINAL
            ))
          }

          it("acks the message") {
            verify(queue).ack(event)
          }
        }
      }
    }
  }
})
