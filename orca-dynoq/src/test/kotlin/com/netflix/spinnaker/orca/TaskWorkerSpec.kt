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

import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith

@RunWith(JUnitPlatform::class)
internal class TaskWorkerSpec : Spek({

  describe("task workers") {

    val commandQ: CommandQueue = mock()
    val eventQ: EventQueue = mock()
    val task: DummyTask = mock()
    val repository: ExecutionRepository = mock()

    val taskWorker = TaskWorker(commandQ, eventQ, repository, listOf(task))

    describe("no commands on the queue") {
      beforeGroup {
        whenever(commandQ.poll())
          .thenReturn(null)

        taskWorker.start()
      }

      afterGroup {
        reset(commandQ, eventQ, task, repository)
      }

      it("does nothing") {
        verify(eventQ, never()).push(anyOrNull())
      }
    }

    describe("running a task") {

      val command = Command(Pipeline::class.java, "1", "1", DummyTask::class.java)
      val pipeline = Pipeline.builder().withId(command.executionId).build()
      val stage = PipelineStage(pipeline, "whatever")

      beforeGroup {
        stage.id = command.stageId
        pipeline.stages.add(stage)
      }

      describe("that completes successfully") {

        val taskResult = DefaultTaskResult(SUCCEEDED)

        beforeGroup {
          whenever(commandQ.poll())
            .thenReturn(command)
          whenever(task.execute(stage))
            .thenReturn(taskResult)
          whenever(repository.retrievePipeline(command.executionId))
            .thenReturn(pipeline)

          taskWorker.start()
        }

        afterGroup {
          reset(commandQ, eventQ, task, repository)
        }

        it("executes the task") {
          verify(task).execute(stage)
        }

        it("emits a success event") {
          verify(eventQ).push(isA<Event.TaskSucceeded>())
        }
      }

      describe("that is not complete") {

        val taskResult = DefaultTaskResult(RUNNING)

        beforeGroup {
          whenever(commandQ.poll())
            .thenReturn(command)
          whenever(task.execute(stage))
            .thenReturn(taskResult)
          whenever(repository.retrievePipeline(command.executionId))
            .thenReturn(pipeline)

          taskWorker.start()
        }

        afterGroup {
          reset(commandQ, eventQ, task, repository)
        }

        it("re-queues the command") {
          verify(commandQ).push(command)
        }
      }
    }

    describe("invalid commands") {

      describe("no such execution") {
        val command = Command(Pipeline::class.java, "1", "1", DummyTask::class.java)

        beforeGroup {
          whenever(commandQ.poll())
            .thenReturn(command)
          whenever(repository.retrievePipeline(command.executionId))
            .thenThrow(ExecutionNotFoundException("No Pipeline found for ${command.executionId}"))

          taskWorker.start()
        }

        afterGroup {
          reset(commandQ, eventQ, task, repository)
        }

        it("does not run any tasks") {
          verify(task, never()).execute(anyOrNull())
        }

        it("emits an error event") {
          verify(eventQ).push(isA<Event.InvalidExecutionId>())
        }
      }

      describe("no such stage") {
        val command = Command(Pipeline::class.java, "1", "1", DummyTask::class.java)
        val pipeline = Pipeline.builder().withId(command.executionId).build()

        beforeGroup {
          whenever(commandQ.poll())
            .thenReturn(command)
          whenever(repository.retrievePipeline(command.executionId))
            .thenReturn(pipeline)

          taskWorker.start()
        }

        afterGroup {
          reset(commandQ, eventQ, task, repository)
        }

        it("does not run any tasks") {
          verify(task, never()).execute(anyOrNull())
        }

        it("emits an error event") {
          verify(eventQ).push(isA<Event.InvalidStageId>())
        }
      }

      describe("no such task") {
        val command = Command(Pipeline::class.java, "1", "1", InvalidTask::class.java)
        val pipeline = Pipeline.builder().withId(command.executionId).build()
        val stage = PipelineStage(pipeline, "whatever")

        beforeGroup {
          stage.id = command.stageId
          pipeline.stages.add(stage)

          whenever(commandQ.poll())
            .thenReturn(command)
          whenever(repository.retrievePipeline(command.executionId))
            .thenReturn(pipeline)

          taskWorker.start()
        }

        afterGroup {
          reset(commandQ, eventQ, task, repository)
        }

        it("does not run any tasks") {
          verify(task, never()).execute(anyOrNull())
        }

        it("emits an error event") {
          verify(eventQ).push(isA<Event.InvalidTaskType>())
        }
      }

    }
  }

})

interface DummyTask : Task
interface InvalidTask :Task
