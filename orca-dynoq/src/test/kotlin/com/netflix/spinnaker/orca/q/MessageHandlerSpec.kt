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
import com.netflix.spinnaker.orca.q.Message.ExecutionComplete
import com.netflix.spinnaker.orca.q.Message.ExecutionStarting
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

  val handler = object : MessageHandler<ExecutionStarting> {
    override val queue
      get() = queue

    override val messageType
      get() = ExecutionStarting::class.java

    override fun handle(message: ExecutionStarting) {
      handleCallback.invoke(message)
    }
  }

  fun resetMocks() = reset(queue, handleCallback)

  describe("message acknowledgment") {
    context("when a message is processed successfully") {
      val message = ExecutionStarting(Pipeline::class.java, "1", "foo")

      afterGroup(::resetMocks)

      action("a message is handled") {
        handler.handleAndAck(message)
      }

      it("invokes the handler") {
        verify(handleCallback).invoke(message)
      }

      it("acknowledges the message") {
        verify(queue).ack(message)
      }
    }

    context("when the handler throws an exception") {
      val message = ExecutionStarting(Pipeline::class.java, "1", "foo")

      beforeGroup {
        whenever(handleCallback.invoke(any()))
          .thenThrow(RuntimeException("o noes"))
      }

      afterGroup(::resetMocks)

      action("a message is handled") {
        assertThat(
          { handler.handleAndAck(message) },
          throws(isA<RuntimeException>())
        )
      }

      it("does not acknowledge the message") {
        verify(queue, never()).ack(message)
      }
    }

    context("when the handler is passed the wrong type of message") {
      val message = ExecutionComplete(Pipeline::class.java, "1", "foo", SUCCEEDED)

      afterGroup(::resetMocks)

      action("a message is handled") {
        assertThat(
          { handler.handleAndAck(message) },
          throws<IllegalArgumentException>()
        )
      }

      it("does not invoke the handler") {
        verifyZeroInteractions(handleCallback)
      }

      it("does not acknowledge the message") {
        verify(queue, never()).ack(message)
      }
    }
  }
})
