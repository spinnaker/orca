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
import com.natpryce.hamkrest.throws
import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

class MessageHandlerSpec : Spek({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val handleCallback: (Message) -> Unit = mock()

  val handler = object : MessageHandler<StartExecution> {
    override val queue
      get() = queue

    override val repository
      get() = repository

    override val messageType = StartExecution::class.java

    override fun handle(message: StartExecution) {
      handleCallback.invoke(message)
    }
  }

  fun resetMocks() = reset(queue, handleCallback)

  describe("when the handler is passed the wrong type of message") {
    val message = CompleteExecution(Pipeline::class.java, "1", "foo", SUCCEEDED)

    afterGroup(::resetMocks)

    action("a message is handled") {
      assertThat(
        { handler.invoke(message) },
        throws<IllegalArgumentException>()
      )
    }

    it("does not invoke the handler") {
      verifyZeroInteractions(handleCallback)
    }
  }
})
