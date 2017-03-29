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
import com.netflix.spinnaker.orca.q.Message.ExecutionComplete
import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.pipeline
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith

@RunWith(JUnitPlatform::class)
class ExecutionCompleteHandlerSpec : Spek({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()

  val handler = ExecutionCompleteHandler(queue, repository)

  fun resetMocks() = reset(queue, repository)

  describe("when an execution completes successfully") {
    val pipeline = pipeline { }
    val message = ExecutionComplete(Pipeline::class.java, pipeline.id, SUCCEEDED)

    beforeGroup {
      whenever(repository.retrievePipeline(message.executionId))
        .thenReturn(pipeline)
    }

    afterGroup(::resetMocks)

    action("the worker polls the queue") {
      handler.handle(message)
    }

    it("updates the execution") {
      verify(repository).updateStatus(message.executionId, SUCCEEDED)
    }
  }

  setOf(TERMINAL, CANCELED).forEach { status ->
    describe("when an execution fails with $status status") {
      val pipeline = pipeline { }
      val message = ExecutionComplete(Pipeline::class.java, pipeline.id, status)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the worker polls the queue") {
        handler.handle(message)
      }

      it("updates the execution") {
        verify(repository).updateStatus(message.executionId, status)
      }
    }
  }
})
