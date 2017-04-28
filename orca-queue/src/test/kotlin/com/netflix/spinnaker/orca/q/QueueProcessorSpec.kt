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

import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.netflix.appinfo.InstanceInfo.InstanceStatus.OUT_OF_SERVICE
import com.netflix.appinfo.InstanceInfo.InstanceStatus.UP
import com.netflix.discovery.StatusChangeEvent
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

class QueueProcessorSpec : Spek({
  describe("execution workers") {
    val queue: Queue = mock()
    val startExecutionHandler: MessageHandler<StartExecution> = mock()
    val configurationErrorHandler: MessageHandler<ConfigurationError> = mock()
    val registry: Registry = mock {
      on { createId(any<String>()) } doReturn mock<Id>()
      on { counter(any<Id>()) } doReturn mock<Counter>()
    }

    var queueProcessor: QueueProcessor? = null

    fun resetMocks() = reset(queue, startExecutionHandler, configurationErrorHandler)

    beforeGroup {
      queueProcessor = QueueProcessor(
        queue,
        directExecutor(),
        registry,
        listOf(startExecutionHandler, configurationErrorHandler)
      )
    }

    describe("when disabled in discovery") {
      beforeGroup {
        queueProcessor!!.onApplicationEvent(RemoteStatusChangedEvent(StatusChangeEvent(UP, OUT_OF_SERVICE)))
      }

      afterGroup(::resetMocks)

      action("the worker runs") {
        queueProcessor!!.pollOnce()
      }

      it("does not poll the queue") {
        verifyZeroInteractions(queue)
      }
    }

    describe("when enabled in discovery") {
      val instanceUpEvent = RemoteStatusChangedEvent(StatusChangeEvent(OUT_OF_SERVICE, UP))

      beforeGroup {
        queueProcessor!!.onApplicationEvent(instanceUpEvent)
      }

      describe("when a message is on the queue") {
        context("it is a supported message type") {
          val message = StartExecution(Pipeline::class.java, "1", "foo")

          beforeGroup {
            whenever(startExecutionHandler.messageType) doReturn StartExecution::class.java
            whenever(configurationErrorHandler.messageType) doReturn ConfigurationError::class.java

            whenever(queue.poll(any())).then {
              @Suppress("UNCHECKED_CAST")
              val callback = it.arguments.first() as QueueCallback
              callback.invoke(message, {})
            }
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            queueProcessor!!.pollOnce()
          }

          it("passes the message to the correct handler") {
            verify(startExecutionHandler).invoke(eq(message))
          }

          it("does not invoke other handlers") {
            verify(configurationErrorHandler, never()).invoke(any())
          }
        }

        context("it is a subclass of a supported message type") {
          val message = InvalidExecutionId(Pipeline::class.java, "1", "foo")

          beforeGroup {
            whenever(startExecutionHandler.messageType) doReturn StartExecution::class.java
            whenever(configurationErrorHandler.messageType) doReturn ConfigurationError::class.java

            whenever(queue.poll(any())).then {
              @Suppress("UNCHECKED_CAST")
              val callback = it.arguments.first() as QueueCallback
              callback.invoke(message, {})
            }
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            queueProcessor!!.pollOnce()
          }

          it("passes the message to the correct handler") {
            verify(configurationErrorHandler).invoke(eq(message))
          }

          it("does not invoke other handlers") {
            verify(startExecutionHandler, never()).invoke(any())
          }
        }

        context("it is an unsupported message type") {
          val message = StartStage(Pipeline::class.java, "1", "foo", "1")

          beforeGroup {
            whenever(startExecutionHandler.messageType) doReturn StartExecution::class.java
            whenever(configurationErrorHandler.messageType) doReturn ConfigurationError::class.java

            whenever(queue.poll(any())).then {
              @Suppress("UNCHECKED_CAST")
              val callback = it.arguments.first() as QueueCallback
              callback.invoke(message, {})
            }
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            assertThat({ queueProcessor!!.pollOnce() }, throws<IllegalStateException>())
          }

          it("does not invoke any handlers") {
            verify(startExecutionHandler, never()).invoke(any())
            verify(configurationErrorHandler, never()).invoke(any())
          }
        }
      }
    }
  }
})
