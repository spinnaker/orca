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
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.throws
import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith

@RunWith(JUnitPlatform::class)
class MessageHandlerSpec : Spek({

  val queue: Queue = mock()
  val handleCallback: (Message) -> Unit = mock()

  val handler = object : MessageHandler<StartExecution> {
    override val queue
      get() = queue

    override val messageType
      get() = StartExecution::class.java

    override fun handle(message: StartExecution) {
      handleCallback.invoke(message)
    }
  }

  fun resetMocks() = reset(queue, handleCallback)

  describe("message acknowledgment") {
    context("when a message is processed successfully") {
      val message = StartExecution(Pipeline::class.java, "1", "foo")

      afterGroup(::resetMocks)

      action("a message is handled") {
        handler.invoke(message)
      }

      it("invokes the handler") {
        verify(handleCallback).invoke(message)
      }
    }

    context("when the handler throws an exception") {
      val message = StartExecution(Pipeline::class.java, "1", "foo")

      beforeGroup {
        whenever(handleCallback.invoke(any()))
          .thenThrow(RuntimeException("o noes"))
      }

      afterGroup(::resetMocks)

      action("a message is handled") {
        assertThat(
          { handler.invoke(message) },
          throws(isA<RuntimeException>())
        )
      }
    }

    context("when the handler is passed the wrong type of message") {
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
  }
})
