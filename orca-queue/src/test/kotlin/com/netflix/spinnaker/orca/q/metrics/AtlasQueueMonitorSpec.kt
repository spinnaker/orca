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

package com.netflix.spinnaker.orca.q.metrics

import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.q.metrics.QueueEvent.*
import com.netflix.spinnaker.orca.time.fixedClock
import com.netflix.spinnaker.spek.shouldEqual
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import java.time.Duration
import java.time.Instant.now

object AtlasQueueMonitorSpec : SubjectSpek<AtlasQueueMonitor>({

  val queue: MonitorableQueue = mock()
  val clock = fixedClock(instant = now().minus(Duration.ofHours(1)))

  val pushCounter: Counter = mock()
  val ackCounter: Counter = mock()
  val retryCounter: Counter = mock()
  val deadCounter: Counter = mock()
  val registry: Registry = mock {
    on { counter("queue.pushed.messages") } doReturn pushCounter
    on { counter("queue.acknowledged.messages") } doReturn ackCounter
    on { counter("queue.retried.messages") } doReturn retryCounter
    on { counter("queue.dead.messages") } doReturn deadCounter
  }

  subject(GROUP) {
    AtlasQueueMonitor(queue, registry, clock)
  }

  fun resetMocks() =
    reset(queue, pushCounter, ackCounter, retryCounter, deadCounter)

  describe("default values") {
    it("reports system uptime if the queue has never been polled") {
      subject.lastQueuePoll shouldEqual clock.instant()
      subject.lastRetryPoll shouldEqual clock.instant()
    }
  }

  describe("responding to queue events") {
    describe("when the queue is polled") {
      afterGroup(::resetMocks)

      val event = QueuePolled(queue)

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onApplicationEvent(event)
      }

      it("updates the last poll time") {
        subject.lastQueuePoll shouldEqual event.instant
      }
    }

    describe("when the retry queue is polled") {
      afterGroup(::resetMocks)

      val event = RetryPolled(queue)

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onApplicationEvent(event)
      }

      it("updates the last poll time") {
        subject.lastRetryPoll shouldEqual event.instant
      }
    }

    describe("when a message is pushed") {
      afterGroup(::resetMocks)

      val event = MessagePushed(queue)

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onApplicationEvent(event)
      }

      it("increments a counter") {
        verify(pushCounter).increment()
      }
    }

    describe("when a message is acknowledged") {
      afterGroup(::resetMocks)

      val event = MessageAcknowledged(queue)

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onApplicationEvent(event)
      }

      it("increments a counter") {
        verify(ackCounter).increment()
      }
    }

    describe("when a message is retried") {
      afterGroup(::resetMocks)

      val event = MessageRetried(queue)

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onApplicationEvent(event)
      }

      it("increments a counter") {
        verify(retryCounter).increment()
      }
    }

    describe("when a message is dead") {
      afterGroup(::resetMocks)

      val event = MessageDead(queue)

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onApplicationEvent(event)
      }

      it("increments a counter") {
        verify(deadCounter).increment()
      }
    }

  }

  describe("checking queue depth") {
    afterGroup(::resetMocks)

    val queueDepth = 4
    val unackedDepth = 2

    beforeGroup {
      whenever(queue.queueDepth) doReturn queueDepth
      whenever(queue.unackedDepth) doReturn unackedDepth
    }

    on("checking queue depth") {
      subject.pollQueueDepth()
    }

    it("updates last known queue depth") {
      subject.lastQueueDepth shouldEqual queueDepth
    }

    it("updates last known unacked depth") {
      subject.lastUnackedDepth shouldEqual unackedDepth
    }
  }
})
