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

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.time.MutableClock
import com.netflix.spinnaker.spek.shouldEqual
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import java.io.Closeable
import java.time.Clock

abstract class MonitoredQueueSpec<out Q : MonitoredQueue>(
  createQueue: (Clock, DeadMessageCallback) -> Q,
  triggerRedeliveryCheck: Q.() -> Unit,
  shutdownCallback: (() -> Unit)? = null
) : Spek({

  var queue: Q? = null
  val clock = MutableClock()
  val deadMessageHandler: DeadMessageCallback = mock()

  fun startQueue() {
    queue = createQueue(clock, deadMessageHandler)
  }

  fun resetMocks() = reset(deadMessageHandler)

  fun stopQueue() {
    queue?.let { q ->
      if (q is Closeable) {
        q.close()
      }
    }
    shutdownCallback?.invoke()
  }

  given("an empty queue") {
    beforeGroup(::startQueue)
    afterGroup(::stopQueue)
    afterGroup(::resetMocks)

    it("reports empty") {
      queue!!.queueState().apply {
        queueDepth shouldEqual 0
        unackedDepth shouldEqual 0
      }
    }
  }

  given("a queue with messages") {
    beforeGroup(::startQueue)
    beforeGroup {
      queue!!.push(StartExecution(Pipeline::class.java, "1", "spinnaker"))
    }

    afterGroup(::stopQueue)
    afterGroup(::resetMocks)

    it("reports the messages on the queue") {
      queue!!.queueState().apply {
        queueDepth shouldEqual 1
        unackedDepth shouldEqual 0
      }
    }
  }

  given("in process messages") {
    beforeGroup(::startQueue)
    beforeGroup {
      queue!!.push(StartExecution(Pipeline::class.java, "1", "spinnaker"))
      queue!!.poll { _, _ -> }
    }

    afterGroup(::stopQueue)
    afterGroup(::resetMocks)

    it("reports unacknowledged messages") {
      queue!!.queueState().apply {
        queueDepth shouldEqual 0
        unackedDepth shouldEqual 1
      }
    }
  }

  given("messages have been acknowledged") {
    beforeGroup(::startQueue)
    beforeGroup {
      queue!!.push(StartExecution(Pipeline::class.java, "1", "spinnaker"))
      queue!!.poll { _, ack ->
        ack.invoke()
      }
    }

    afterGroup(::stopQueue)
    afterGroup(::resetMocks)

    it("reports an empty queue") {
      queue!!.queueState().apply {
        queueDepth shouldEqual 0
        unackedDepth shouldEqual 0
      }
    }
  }

  given("no messages have been redelivered") {
    beforeGroup(::startQueue)
    beforeGroup {
      queue!!.push(StartExecution(Pipeline::class.java, "1", "spinnaker"))
      queue!!.poll { _, ack ->
        ack.invoke()
      }
      clock.incrementBy(queue!!.ackTimeout)
      triggerRedeliveryCheck.invoke(queue!!)
    }

    afterGroup(::stopQueue)
    afterGroup(::resetMocks)

    it("reports the time of the last redelivery check") {
      queue!!.queueState().apply {
        redeliveryCount shouldEqual 0
        lastRedeliveryCheck shouldEqual clock.instant()
        deadLetterCount shouldEqual 0
      }
    }
  }

  given("a message has been redelivered") {
    beforeGroup(::startQueue)
    beforeGroup {
      queue!!.push(StartExecution(Pipeline::class.java, "1", "spinnaker"))
      queue!!.poll { _, _ -> }
      clock.incrementBy(queue!!.ackTimeout)
      triggerRedeliveryCheck.invoke(queue!!)
    }

    afterGroup(::stopQueue)
    afterGroup(::resetMocks)

    it("reports the depth with the message re-queued") {
      queue!!.queueState().apply {
        queueDepth shouldEqual 1
        unackedDepth shouldEqual 0
      }
    }

    it("reports the redelivered message") {
      queue!!.queueState().apply {
        redeliveryCount shouldEqual 1
        lastRedeliveryCheck shouldEqual clock.instant()
        deadLetterCount shouldEqual 0
      }
    }
  }

  given("a message has been dead lettered") {
    beforeGroup(::startQueue)
    beforeGroup {
      queue!!.push(StartExecution(Pipeline::class.java, "1", "spinnaker"))
      (1..Queue.maxRedeliveries).forEach {
        queue!!.poll { _, _ -> }
        clock.incrementBy(queue!!.ackTimeout)
        triggerRedeliveryCheck.invoke(queue!!)
      }
    }

    afterGroup(::stopQueue)
    afterGroup(::resetMocks)

    it("reports the depth without the message re-queued") {
      queue!!.queueState().apply {
        queueDepth shouldEqual 0
        unackedDepth shouldEqual 0
      }
    }

    it("reports the redelivered message") {
      queue!!.queueState().apply {
        redeliveryCount shouldEqual (Queue.maxRedeliveries - 1).toLong()
        deadLetterCount shouldEqual 1
      }
    }
  }
})
