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
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith

@RunWith(JUnitPlatform::class)
class ExecutionStartingHandlerSpec : Spek({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()

  val handler = ExecutionStartingHandler(queue, repository)

  fun resetMocks() = reset(queue, repository)

  describe("starting an execution") {
    context("with a single initial stage") {
      val pipeline = pipeline {
        stage {
          type = singleTaskStage.type
        }
      }
      val message = Message.ExecutionStarting(Pipeline::class.java, pipeline.id, "foo")

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("marks the execution as running") {
        verify(repository).updateStatus(message.executionId, ExecutionStatus.RUNNING)
      }

      it("starts the first stage") {
        verify(queue).push(Message.StageStarting(
          message.executionType,
          message.executionId,
          "foo",
          pipeline.stages.first().id
        ))
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
      val message = Message.ExecutionStarting(Pipeline::class.java, pipeline.id, "foo")

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("starts all the initial stages") {
        argumentCaptor<Message.StageStarting>().apply {
          verify(queue, times(2)).push(capture())
          assertThat(
            allValues.map { it.stageId }.toSet(),
            equalTo(pipeline.stages.map { it.id }.toSet())
          )
        }
      }
    }
  }

})
