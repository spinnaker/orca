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

import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.events.TaskStarted
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.time.fixedClock
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.springframework.context.ApplicationEventPublisher

class StartTaskHandlerSpec : Spek({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val publisher: ApplicationEventPublisher = mock()
  val clock = fixedClock()

  val handler = StartTaskHandler(queue, repository, publisher, clock)

  fun resetMocks() = reset(queue, repository, publisher)

  describe("when a task starts") {
    val pipeline = pipeline {
      stage {
        type = singleTaskStage.type
        singleTaskStage.buildTasks(this)
      }
    }
    val message = StartTask(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, "1")

    beforeGroup {
      whenever(repository.retrievePipeline(message.executionId)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      handler.handle(message)
    }

    it("marks the task as running") {
      verify(repository).storeStage(check {
        it.getTasks().first().apply {
          status shouldEqual RUNNING
          startTime shouldEqual clock.millis()
        }
      })
    }

    it("runs the task") {
      verify(queue).push(RunTask(
        message.executionType,
        message.executionId,
        "foo",
        message.stageId,
        message.taskId,
        DummyTask::class.java
      ))
    }

    it("publishes an event") {
      argumentCaptor<TaskStarted>().apply {
        verify(publisher).publishEvent(capture())
        firstValue.apply {
          executionType shouldEqual pipeline.javaClass
          executionId shouldEqual pipeline.id
          stageId shouldEqual message.stageId
          taskId shouldEqual message.taskId
        }
      }
    }
  }
})
