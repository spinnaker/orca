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
import com.netflix.appinfo.InstanceInfo.InstanceStatus.OUT_OF_SERVICE
import com.netflix.appinfo.InstanceInfo.InstanceStatus.UP
import com.netflix.discovery.StatusChangeEvent
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.q.Message.*
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith

@RunWith(JUnitPlatform::class)
class ExecutionWorkerSpec : Spek({
  describe("execution workers") {
    val queue: Queue = mock()
    val executionStartingHandler: MessageHandler<ExecutionStarting> = mock()
    val executionCompleteHandler: MessageHandler<ExecutionComplete> = mock()
    val registry: Registry = mock {
      on { createId(any<String>()) }.thenReturn(mock<Id>())
      on { counter(any<Id>()) }.thenReturn(mock<Counter>())
    }

    var worker: ExecutionWorker? = null

    fun resetMocks() = reset(queue, executionStartingHandler, executionCompleteHandler)

    beforeGroup {
      whenever(executionStartingHandler.messageType).thenReturn(ExecutionStarting::class.java)
      whenever(executionCompleteHandler.messageType).thenReturn(ExecutionComplete::class.java)

      worker = ExecutionWorker(queue, registry, listOf(executionStartingHandler, executionCompleteHandler))
    }

    describe("when disabled in discovery") {
      beforeGroup {
        worker!!.onApplicationEvent(RemoteStatusChangedEvent(StatusChangeEvent(UP, OUT_OF_SERVICE)))
      }

      afterGroup(::resetMocks)

      action("the worker runs") {
        worker!!.pollOnce()
      }

      it("does not poll the queue") {
        verifyZeroInteractions(queue)
      }
    }

    describe("when enabled in discovery") {
      val instanceUpEvent = RemoteStatusChangedEvent(StatusChangeEvent(OUT_OF_SERVICE, UP))

      beforeGroup {
        worker!!.onApplicationEvent(instanceUpEvent)
      }

      describe("no messages on the queue") {
        beforeGroup {
          whenever(queue.poll()).thenReturn(null)
        }

        afterGroup(::resetMocks)

        action("the worker polls the queue") {
          worker!!.pollOnce()
        }

        it("does not try to ack non-existent messages") {
          verify(queue, never()).ack(anyOrNull())
        }
      }

      describe("when a message is on the queue") {
        context("it is a supported message type") {
          val message = ExecutionStarting(Pipeline::class.java, "1", "foo")

          beforeGroup {
            whenever(queue.poll()).thenReturn(message)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            worker!!.pollOnce()
          }

          it("passes the message to the correct handler") {
            verify(executionStartingHandler).handleAndAck(message)
          }

          it("does not invoke other handlers") {
            verifyZeroInteractions(executionCompleteHandler)
          }
        }

        context("it is an unsupported message type") {
          val message = StageStarting(Pipeline::class.java, "1", "foo", "1")

          beforeGroup {
            whenever(queue.poll()).thenReturn(message)
          }

          afterGroup(::resetMocks)

          action("the worker polls the queue") {
            assertThat({ worker!!.pollOnce() }, throws<IllegalStateException>())
          }

          it("does not invoke any handlers") {
            verifyZeroInteractions(
              executionStartingHandler,
              executionCompleteHandler
            )
          }
        }
      }
    }
  }
})
