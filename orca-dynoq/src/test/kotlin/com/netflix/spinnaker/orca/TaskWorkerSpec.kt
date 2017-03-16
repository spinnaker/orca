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

      it("does nothing") {
        verify(eventQ, never()).push(any())
      }
    }

    describe("running a task that completes successfully") {

      val command = Command(Pipeline::class.java, "1", "1", DummyTask::class.java)
      val taskResult = DefaultTaskResult(SUCCEEDED)
      val pipeline = Pipeline.builder().withId(command.executionId).build()
      val stage = PipelineStage(pipeline, "whatever")

      beforeGroup {
        stage.id = command.stageId
        pipeline.stages.add(stage)

        whenever(commandQ.poll())
          .thenReturn(command)
          .thenReturn(null)
        whenever(task.execute(stage))
          .thenReturn(taskResult)
        whenever(repository.retrievePipeline(command.executionId))
          .thenReturn(pipeline)

        taskWorker.start()
      }

      it("executes the task") {
        verify(task).execute(stage)
      }

      it("emits a success event") {
        verify(eventQ).push(isA<Event.TaskSucceeded>())
      }
    }

    describe("running a task that is not complete") {
      val command = Command(Pipeline::class.java, "1", "1", DummyTask::class.java)
      val taskResult = DefaultTaskResult(RUNNING)
      val pipeline = Pipeline.builder().withId(command.executionId).build()
      val stage = PipelineStage(pipeline, "whatever")

      beforeGroup {
        stage.id = command.stageId
        pipeline.stages.add(stage)

        whenever(commandQ.poll())
          .thenReturn(command)
          .thenReturn(null)
        whenever(task.execute(stage))
          .thenReturn(taskResult)
        whenever(repository.retrievePipeline(command.executionId))
          .thenReturn(pipeline)

        taskWorker.start()
      }

      it("re-queues the command") {
        verify(commandQ).push(command)
      }
    }

  }

})


interface DummyTask : Task

