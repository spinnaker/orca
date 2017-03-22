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
import com.netflix.appinfo.InstanceInfo.InstanceStatus.OUT_OF_SERVICE
import com.netflix.appinfo.InstanceInfo.InstanceStatus.UP
import com.netflix.discovery.StatusChangeEvent
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.Command.RunTask
import com.netflix.spinnaker.orca.q.Event.ConfigurationError.*
import com.netflix.spinnaker.orca.q.Event.TaskComplete
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit.MILLISECONDS

@RunWith(JUnitPlatform::class)
internal class TaskWorkerSpec : Spek({

  describe("task workers") {

    val commandQ: CommandQueue = mock()
    val eventQ: EventQueue = mock()
    val task: DummyTask = mock()
    val repository: ExecutionRepository = mock()

    val taskWorker = TaskWorker(commandQ, eventQ, repository, listOf(task))

    describe("when disabled in discovery") {
      beforeGroup {
        taskWorker.onApplicationEvent(RemoteStatusChangedEvent(StatusChangeEvent(UP, OUT_OF_SERVICE)))
      }

      afterGroup {
        reset(commandQ, eventQ, task, repository)
      }

      action("the worker polls the queue") {
        taskWorker.pollOnce()
      }

      it("does not poll the queue") {
        verifyZeroInteractions(commandQ)
      }
    }

    describe("when enabled in discovery") {
      beforeGroup {
        taskWorker.onApplicationEvent(RemoteStatusChangedEvent(StatusChangeEvent(OUT_OF_SERVICE, UP)))
      }

      describe("no commands on the queue") {
        beforeGroup {
          whenever(commandQ.poll())
            .thenReturn(null)
        }

        afterGroup {
          reset(commandQ, eventQ, task, repository)
        }

        action("the worker polls the queue") {
          taskWorker.pollOnce()
        }

        it("does nothing") {
          verifyZeroInteractions(eventQ)
        }

        it("does not ack non-existent messages") {
          verify(commandQ, never()).ack(anyOrNull())
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
            whenever(commandQ.poll())
              .thenReturn(command)
            whenever(task.execute(any<Stage<*>>()))
              .thenReturn(taskResult)
            whenever(repository.retrievePipeline(command.executionId))
              .thenReturn(pipeline)
          }

          afterGroup {
            reset(commandQ, eventQ, task, repository)
          }

          action("the worker polls the queue") {
            taskWorker.pollOnce()
          }

          it("executes the task") {
            verify(task).execute(pipeline.stages.first())
          }

          it("emits a failure event") {
            argumentCaptor<TaskComplete>().apply {
              verify(eventQ).push(capture())
              assertThat(firstValue.status, equalTo(SUCCEEDED))
            }
          }

          it("acks the message") {
            verify(commandQ).ack(command)
          }
        }

        describe("that is not yet complete") {
          val taskResult = DefaultTaskResult(RUNNING)
          val taskBackoffMs = 30_000L

          beforeGroup {
            whenever(commandQ.poll())
              .thenReturn(command)
            whenever(task.execute(any()))
              .thenReturn(taskResult)
            whenever(task.backoffPeriod)
              .thenReturn(taskBackoffMs)
            whenever(repository.retrievePipeline(command.executionId))
              .thenReturn(pipeline)
          }

          afterGroup {
            reset(commandQ, eventQ, task, repository)
          }

          action("the worker polls the queue") {
            taskWorker.pollOnce()
          }

          it("re-queues the command") {
            verify(commandQ).push(command, taskBackoffMs, MILLISECONDS)
          }

          it("acks the message") {
            verify(commandQ).ack(command)
          }
        }

        describe("that fails") {
          val taskResult = DefaultTaskResult(TERMINAL)

          beforeGroup {
            whenever(commandQ.poll())
              .thenReturn(command)
            whenever(task.execute(any()))
              .thenReturn(taskResult)
            whenever(repository.retrievePipeline(command.executionId))
              .thenReturn(pipeline)
          }

          afterGroup {
            reset(commandQ, eventQ, task, repository)
          }

          action("the worker polls the queue") {
            taskWorker.pollOnce()
          }

          it("emits a failure event") {
            argumentCaptor<TaskComplete>().apply {
              verify(eventQ).push(capture())
              assertThat(firstValue.status, equalTo(TERMINAL))
            }
          }

          it("acks the message") {
            verify(commandQ).ack(command)
          }
        }

        describe("that throws an exception") {
          beforeGroup {
            whenever(commandQ.poll())
              .thenReturn(command)
            whenever(task.execute(any()))
              .thenThrow(RuntimeException("o noes"))
            whenever(repository.retrievePipeline(command.executionId))
              .thenReturn(pipeline)
          }

          afterGroup {
            reset(commandQ, eventQ, task, repository)
          }

          action("the worker polls the queue") {
            taskWorker.pollOnce()
          }

          it("emits a failure event") {
            argumentCaptor<TaskComplete>().apply {
              verify(eventQ).push(capture())
              assertThat(firstValue.status, equalTo(TERMINAL))
            }
          }

          it("acks the message") {
            verify(commandQ).ack(command)
          }
        }

        describe("when the execution has stopped") {
          beforeGroup {
            pipeline.status = TERMINAL

            whenever(commandQ.poll())
              .thenReturn(command)
            whenever(repository.retrievePipeline(command.executionId))
              .thenReturn(pipeline)
          }

          afterGroup {
            reset(commandQ, eventQ, task, repository)
          }

          action("the worker polls the queue") {
            taskWorker.pollOnce()
          }

          it("emits an event indicating that the task was canceled") {
            verify(eventQ).push(TaskComplete(
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
            verify(commandQ).ack(command)
          }
        }
      }

      describe("invalid commands") {

        describe("no such execution") {
          val command = RunTask(Pipeline::class.java, "1", "1", "1", DummyTask::class.java)

          beforeGroup {
            whenever(commandQ.poll())
              .thenReturn(command)
            whenever(repository.retrievePipeline(command.executionId))
              .thenThrow(ExecutionNotFoundException("No Pipeline found for ${command.executionId}"))
          }

          afterGroup {
            reset(commandQ, eventQ, task, repository)
          }

          action("the worker polls the queue") {
            taskWorker.pollOnce()
          }

          it("does not run any tasks") {
            verifyZeroInteractions(task)
          }

          it("emits an error event") {
            verify(eventQ).push(isA<InvalidExecutionId>())
          }

          it("acks the message") {
            verify(commandQ).ack(command)
          }
        }

        describe("no such stage") {
          val pipeline = pipeline { }
          val command = RunTask(Pipeline::class.java, pipeline.id, "1", "1", DummyTask::class.java)

          beforeGroup {
            whenever(commandQ.poll())
              .thenReturn(command)
            whenever(repository.retrievePipeline(command.executionId))
              .thenReturn(pipeline)
          }

          afterGroup {
            reset(commandQ, eventQ, task, repository)
          }

          action("the worker polls the queue") {
            taskWorker.pollOnce()
          }

          it("does not run any tasks") {
            verifyZeroInteractions(task)
          }

          it("emits an error event") {
            verify(eventQ).push(isA<InvalidStageId>())
          }

          it("acks the message") {
            verify(commandQ).ack(command)
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
            whenever(commandQ.poll())
              .thenReturn(command)
            whenever(repository.retrievePipeline(command.executionId))
              .thenReturn(pipeline)
          }

          afterGroup {
            reset(commandQ, eventQ, task, repository)
          }

          action("the worker polls the queue") {
            taskWorker.pollOnce()
          }

          it("does not run any tasks") {
            verifyZeroInteractions(task)
          }

          it("emits an error event") {
            verify(eventQ).push(isA<InvalidTaskType>())
          }

          it("acks the message") {
            verify(commandQ).ack(command)
          }
        }
      }
    }
  }
})
