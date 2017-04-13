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

package com.netflix.spinnaker.orca.q.handler

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.q.Message.RunTask
import com.netflix.spinnaker.orca.q.Message.TaskComplete
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import java.lang.RuntimeException
import java.time.Duration

@RunWith(JUnitPlatform::class)
class RunTaskHandlerSpec : Spek({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val task: DummyTask = mock()

  val handler = RunTaskHandler(queue, repository, listOf(task))

  fun resetMocks() = reset(queue, repository, task)

  describe("running a task") {
    val pipeline = pipeline {
      stage {
        type = "whatever"
      }
    }
    val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", DummyTask::class.java)

    describe("that completes successfully") {
      val taskResult = TaskResult(SUCCEEDED)

      beforeGroup {
        whenever(task.execute(any<Stage<*>>())).thenReturn(taskResult)
        whenever(repository.retrievePipeline(message.executionId))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("executes the task") {
        verify(task).execute(pipeline.stages.first())
      }

      it("emits a failure event") {
        argumentCaptor<Message.TaskComplete>().apply {
          verify(queue).push(capture())
          assertThat(firstValue.status, equalTo(SUCCEEDED))
        }
      }
    }

    describe("that is not yet complete") {
      val taskResult = TaskResult(RUNNING)
      val taskBackoffMs = 30_000L

      beforeGroup {
        whenever(task.execute(any())).thenReturn(taskResult)
        whenever(task.backoffPeriod).thenReturn(taskBackoffMs)
        whenever(repository.retrievePipeline(message.executionId))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("re-queues the command") {
        verify(queue).push(message, Duration.ofMillis(taskBackoffMs))
      }
    }

    describe("that fails") {
      val taskResult = TaskResult(TERMINAL)

      beforeGroup {
        whenever(task.execute(any())).thenReturn(taskResult)
        whenever(repository.retrievePipeline(message.executionId))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("emits a failure event") {
        argumentCaptor<TaskComplete>().apply {
          verify(queue).push(capture())
          assertThat(firstValue.status, equalTo(TERMINAL))
        }
      }
    }

    describe("that throws an exception") {
      beforeGroup {
        whenever(task.execute(any())).thenThrow(RuntimeException("o noes"))
        whenever(repository.retrievePipeline(message.executionId))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("emits a failure event") {
        argumentCaptor<TaskComplete>().apply {
          verify(queue).push(capture())
          assertThat(firstValue.status, equalTo(TERMINAL))
        }
      }
    }

    describe("when the execution has stopped") {
      beforeGroup {
        pipeline.status = TERMINAL

        whenever(repository.retrievePipeline(message.executionId))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("emits an event indicating that the task was canceled") {
        verify(queue).push(TaskComplete(
          message.executionType,
          message.executionId,
          "foo",
          message.stageId,
          message.taskId,
          CANCELED
        ))
      }

      it("does not execute the task") {
        verifyZeroInteractions(task)
      }
    }
  }

  describe("no such task") {
    val pipeline = pipeline {
      stage {
        type = "whatever"
      }
    }
    val message = RunTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1", InvalidTask::class.java)

    beforeGroup {
      whenever(repository.retrievePipeline(message.executionId))
        .thenReturn(pipeline)
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      handler.handle(message)
    }

    it("does not run any tasks") {
      verifyZeroInteractions(task)
    }

    it("emits an error event") {
      verify(queue).push(isA<Message.ConfigurationError.InvalidTaskType>())
    }
  }
})
