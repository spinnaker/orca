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
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import java.time.Clock.fixed
import java.time.Instant.now
import java.time.ZoneId.systemDefault

@RunWith(JUnitPlatform::class)
class TaskStartingHandlerSpec : Spek({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val clock = fixed(now(), systemDefault())

  val handler = TaskStartingHandler(queue, repository, clock)

  fun resetMocks() = reset(queue, repository)

  describe("when a task starts") {
    val pipeline = pipeline {
      stage {
        type = singleTaskStage.type
        singleTaskStage.buildTasks(this)
      }
    }
    val message = Message.TaskStarting(Pipeline::class.java, pipeline.id, pipeline.stages.first().id, "1")

    beforeGroup {
      whenever(repository.retrievePipeline(message.executionId))
        .thenReturn(pipeline)
    }

    afterGroup(::resetMocks)

    action("the worker polls the queue") {
      handler.handle(message)
    }

    it("marks the task as running") {
      argumentCaptor<Stage<Pipeline>>().apply {
        verify(repository).storeStage(capture())
        firstValue.tasks.first().apply {
          assertThat(status, equalTo(ExecutionStatus.RUNNING))
          assertThat(startTime, equalTo(clock.millis()))
        }
      }
    }

    it("runs the task") {
      verify(queue).push(Message.RunTask(
        message.executionType,
        message.executionId,
        message.stageId,
        message.taskId,
        DummyTask::class.java
      ))
    }
  }
})
