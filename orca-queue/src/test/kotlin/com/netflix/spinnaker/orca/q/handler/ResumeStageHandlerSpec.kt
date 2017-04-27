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

import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

class ResumeStageHandlerSpec : Spek({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()

  val handler = ResumeStageHandler(queue, repository)

  fun resetMocks() = reset(queue, repository)

  describe("resuming a paused execution") {
    val pipeline = pipeline {
      application = "spinnaker"
      status = RUNNING
      stage {
        refId = "1"
        status = PAUSED
        task {
          id = "1"
          status = SUCCEEDED
        }
        task {
          id = "2"
          status = PAUSED
        }
        task {
          id = "3"
          status = NOT_STARTED
        }
      }
    }
    val message = ResumeStage(Pipeline::class.java, pipeline.id, pipeline.application, pipeline.stages.first().id)

    beforeGroup {
      whenever(repository.retrievePipeline(pipeline.id)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      handler.handle(message)
    }

    it("sets the stage status to running") {
      verify(repository).storeStage(check {
        it.getId() shouldEqual message.stageId
        it.getStatus() shouldEqual RUNNING
      })
    }

    it("resumes all paused tasks") {
      verify(queue).push(ResumeTask(message, "2"))
      verifyNoMoreInteractions(queue)
    }
  }
})
