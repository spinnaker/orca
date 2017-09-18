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

package com.netflix.spinnaker.q

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.lifecycle.CachingMode.SCOPE
import org.jetbrains.spek.subject.SubjectSpek
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors

object QueueProcessorTest : SubjectSpek<QueueProcessor>({
  describe("execution workers") {
    val queue: Queue = mock()
    val simpleMessageHandler: MessageHandler<SimpleMessage> = mock()
    val parentMessageHandler: MessageHandler<ParentMessage> = mock()
    val ackFunction: () -> Unit = mock()
    val activator = object : Activator() {
      private var _enabled = false

      override val enabled
        get() = _enabled

      fun enable() {
        _enabled = true
      }

      fun disable() {
        _enabled = false
      }
    }

    fun resetMocks() = reset(queue, simpleMessageHandler, parentMessageHandler, ackFunction)

    subject(SCOPE) {
      QueueProcessor(
        queue,
        BlockingQueueExecutor(),
        listOf(simpleMessageHandler, parentMessageHandler),
        activator
      )
    }

    describe("when disabled") {
      beforeEachTest {
        activator.disable()
      }

      afterGroup(::resetMocks)

      action("the worker runs") {
        subject.pollOnce()
      }

      it("does not poll the queue") {
        verifyZeroInteractions(queue)
      }
    }

    describe("when enabled") {
      beforeEachTest {
        activator.enable()
      }

      describe("when a message is on the queue") {
        context("it is a supported message type") {
          val message = SimpleMessage("foo")

          beforeGroup {
            whenever(simpleMessageHandler.messageType) doReturn SimpleMessage::class.java
            whenever(parentMessageHandler.messageType) doReturn ParentMessage::class.java

            whenever(queue.poll(any())).then {
              @Suppress("UNCHECKED_CAST")
              val callback = it.arguments.first() as QueueCallback
              callback.invoke(message, ackFunction)
            }
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            subject.pollOnce()
          }

          it("passes the message to the correct handler") {
            verify(simpleMessageHandler).invoke(eq(message))
          }

          it("does not invoke other handlers") {
            verify(parentMessageHandler, never()).invoke(any())
          }

          it("acknowledges the message") {
            verify(ackFunction).invoke()
          }
        }

        context("it is a subclass of a supported message type") {
          val message = ChildMessage("foo")

          beforeGroup {
            whenever(simpleMessageHandler.messageType) doReturn SimpleMessage::class.java
            whenever(parentMessageHandler.messageType) doReturn ParentMessage::class.java

            whenever(queue.poll(any())).then {
              @Suppress("UNCHECKED_CAST")
              val callback = it.arguments.first() as QueueCallback
              callback.invoke(message, ackFunction)
            }
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            subject.pollOnce()
          }

          it("passes the message to the correct handler") {
            verify(parentMessageHandler).invoke(eq(message))
          }

          it("does not invoke other handlers") {
            verify(simpleMessageHandler, never()).invoke(any())
          }

          it("acknowledges the message") {
            verify(ackFunction).invoke()
          }
        }

        context("it is an unsupported message type") {
          val message = UnsupportedMessage("foo")

          beforeGroup {
            whenever(simpleMessageHandler.messageType) doReturn SimpleMessage::class.java
            whenever(parentMessageHandler.messageType) doReturn ParentMessage::class.java

            whenever(queue.poll(any())).then {
              @Suppress("UNCHECKED_CAST")
              val callback = it.arguments.first() as QueueCallback
              callback.invoke(message, ackFunction)
            }
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            assertThat({ subject.pollOnce() }, throws<IllegalStateException>())
          }

          it("does not invoke any handlers") {
            verify(simpleMessageHandler, never()).invoke(any())
            verify(parentMessageHandler, never()).invoke(any())
          }

          it("does not acknowledge the message") {
            verify(ackFunction, never()).invoke()
          }
        }

        context("the handler throws an exception") {
          val message = SimpleMessage("foo")

          beforeGroup {
            whenever(simpleMessageHandler.messageType) doReturn SimpleMessage::class.java
            whenever(parentMessageHandler.messageType) doReturn ParentMessage::class.java

            whenever(queue.poll(any())).then {
              @Suppress("UNCHECKED_CAST")
              val callback = it.arguments.first() as QueueCallback
              callback.invoke(message, ackFunction)
            }

            whenever(simpleMessageHandler.invoke(any())) doThrow DummyException()
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            subject.pollOnce()
          }

          it("does not acknowledge the message") {
            verify(ackFunction, never()).invoke()
          }
        }
      }
    }
  }
})

data class SimpleMessage(val payload: String) : Message()

sealed class ParentMessage : Message()

data class ChildMessage(val payload: String) : ParentMessage()

data class UnsupportedMessage(val payload: String) : Message()

class BlockingThreadExecutor : Executor {

  private val delegate = Executors.newSingleThreadExecutor()

  override fun execute(command: Runnable) {
    val latch = CountDownLatch(1)
    delegate.execute {
      try {
        command.run()
      } finally {
        latch.countDown()
      }
    }
    latch.await()
  }
}

class BlockingQueueExecutor : QueueExecutor {
  override val executor: Executor = BlockingThreadExecutor()
  override fun hasCapacity(): Boolean = true
}

class DummyException : RuntimeException("deliberate exception for test")