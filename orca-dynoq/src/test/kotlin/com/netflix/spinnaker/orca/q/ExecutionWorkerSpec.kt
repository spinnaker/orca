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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasSize
import com.netflix.appinfo.InstanceInfo.InstanceStatus.OUT_OF_SERVICE
import com.netflix.appinfo.InstanceInfo.InstanceStatus.UP
import com.netflix.discovery.StatusChangeEvent
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.newStage
import com.netflix.spinnaker.orca.pipeline.TaskNode.Builder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.Command.RunTask
import com.netflix.spinnaker.orca.q.Event.*
import com.netflix.spinnaker.orca.q.Event.ConfigurationError.*
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.xcontext
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

    val executionWorker = ExecutionWorker(
      commandQ,
      eventQ,
      repository,
      clock,
      listOf(singleTaskStage, multiTaskStage, stageWithSyntheticBefore, stageWithSyntheticAfter)
    )

    describe("when disabled in discovery") {
      beforeGroup {
        executionWorker.onApplicationEvent(RemoteStatusChangedEvent(StatusChangeEvent(UP, OUT_OF_SERVICE)))
      }

      afterGroup {
        reset(commandQ, eventQ, repository)
      }

      action("the worker runs") {
        executionWorker.pollOnce()
      }

      it("does not poll the queue") {
        verifyZeroInteractions(eventQ)
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

        it("does not try to ack non-existent messages") {
          verify(eventQ, never()).ack(anyOrNull())
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

          it("marks the execution as running") {
            verify(repository).updateStatus(event.executionId, RUNNING)
          }

          it("starts the first stage") {
            verify(eventQ).push(StageStarting(
              event.executionType,
              event.executionId,
              pipeline.stages.first().id
            ))
          }

          it("acks the message") {
            verify(eventQ).ack(event)
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

          it("starts all the initial stages") {
            argumentCaptor<StageStarting>().apply {
              verify(eventQ, times(2)).push(capture())
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
            verify(eventQ).push(TaskStarting(
              event.executionType,
              event.executionId,
              event.stageId,
              "1"
            ))
          }

          it("acks the message") {
            verify(eventQ).ack(event)
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
            whenever(eventQ.poll())
              .thenReturn(event)
            whenever(repository.retrievePipeline(event.executionId))
              .thenReturn(pipeline)
          }

          action("the worker polls the queue") {
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

          it("raises an event to indicate the first task is starting") {
            verify(eventQ).push(TaskStarting(
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
              whenever(eventQ.poll())
                .thenReturn(event)
              whenever(repository.retrievePipeline(event.executionId))
                .thenReturn(pipeline)
            }

            action("the worker polls the queue") {
              executionWorker.pollOnce()
            }

            afterGroup {
              reset(commandQ, eventQ, repository)
            }

            it("attaches the synthetic stage to the pipeline") {
              argumentCaptor<Pipeline>().apply {
                verify(repository).store(capture())
                assertThat(firstValue.stages.size, equalTo(3))
                assertThat(firstValue.stages.map { it.id }, equalTo(listOf("${event.stageId}-1-pre1", "${event.stageId}-2-pre2", event.stageId)))
              }
            }

            it("raises an event to indicate the synthetic stage is starting") {
              verify(eventQ).push(StageStarting(
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
              whenever(eventQ.poll())
                .thenReturn(event)
              whenever(repository.retrievePipeline(event.executionId))
                .thenReturn(pipeline)
            }

            action("the worker polls the queue") {
              executionWorker.pollOnce()
            }

            afterGroup {
              reset(commandQ, eventQ, repository)
            }

            it("attaches the synthetic stage to the pipeline") {
              argumentCaptor<Pipeline>().apply {
                verify(repository).store(capture())
                assertThat(firstValue.stages.size, equalTo(3))
                assertThat(firstValue.stages.map { it.id }, equalTo(listOf(event.stageId, "${event.stageId}-1-post1", "${event.stageId}-2-post2")))
              }
            }

            it("raises an event to indicate the first task is starting") {
              verify(eventQ).push(TaskStarting(
                event.executionType,
                event.executionId,
                event.stageId,
                "1"
              ))
            }
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
          verify(commandQ).push(RunTask(
            event.executionType,
            event.executionId,
            event.stageId,
            event.taskId,
            DummyTask::class.java
          ))
        }

        it("acks the message") {
          verify(eventQ).ack(event)
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
            verify(eventQ)
              .push(TaskStarting(
                Pipeline::class.java,
                event.executionId,
                event.stageId,
                "2"
              ))
          }

          it("acks the message") {
            verify(eventQ).ack(event)
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
          val pipeline = pipeline {
            stage {
              type = multiTaskStage.type
              multiTaskStage.buildTasks(this)
            }
          }
          val event = TaskComplete(Pipeline::class.java, pipeline.id, pipeline.stages.first().id, "1", status)

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

          it("acks the message") {
            verify(eventQ).ack(event)
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

          it("acks the message") {
            verify(eventQ).ack(event)
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
              pipeline.stages.last().id
            ))
          }

          it("does not run any tasks") {
            verifyZeroInteractions(commandQ)
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

          it("acks the message") {
            verify(eventQ).ack(event)
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
              whenever(eventQ.poll())
                .thenReturn(event)
            }

            afterGroup {
              reset(commandQ, eventQ, repository)
            }

            action("the worker polls the queue") {
              executionWorker.pollOnce()
            }

            it("runs the next synthetic stage") {
              verify(eventQ).push(StageStarting(
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
              whenever(eventQ.poll())
                .thenReturn(event)
            }

            afterGroup {
              reset(commandQ, eventQ, repository)
            }

            action("the worker polls the queue") {
              executionWorker.pollOnce()
            }

            it("runs the next synthetic stage") {
              verify(eventQ).push(TaskStarting(
                event.executionType,
                event.executionId,
                pipeline.stages[2].id,
                "1"
              ))
            }
          }
        }

        xcontext("after the main stage") {
          context("there are more after stages") {

          }

          context("it is the last after stage") {

          }
        }
      }

      describe("when an execution completes successfully") {
        val pipeline = pipeline { }
        val event = ExecutionComplete(Pipeline::class.java, pipeline.id, SUCCEEDED)

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
          verify(repository).updateStatus(event.executionId, SUCCEEDED)
        }

        it("acks the message") {
          verify(eventQ).ack(event)
        }
      }

      setOf(TERMINAL, CANCELED).forEach { status ->
        describe("when an execution fails with $status status") {
          val pipeline = pipeline { }
          val event = ExecutionComplete(Pipeline::class.java, pipeline.id, status)

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
            verify(repository).updateStatus(event.executionId, status)
          }

          it("acks the message") {
            verify(eventQ).ack(event)
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
          val pipeline = pipeline {
            id = event.executionId
          }

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
          beforeGroup {
            whenever(eventQ.poll())
              .thenReturn(event)
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

          it("acks the message") {
            verify(eventQ).ack(event)
          }
        }
      }
    }
  }
})

operator fun <E> List<E>.get(indices: IntRange): List<E> =
  subList(indices.start, indices.endInclusive + 1)
