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

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.q.Message.ExecutionStarting
import com.netflix.spinnaker.orca.time.MutableClock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import java.io.Closeable
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit.HOURS

abstract class QueueSpec<out Q : Queue>(
  createQueue: () -> Q,
  triggerRedeliveryCheck: (Q) -> Unit
) : Spek({

  fun shutdown(queue: Q) {
    if (queue is Closeable) {
      queue.close()
    }
  }

  describe("polling the queue") {
    context("there are no messages") {
      val queue = createQueue.invoke()

      afterGroup { shutdown(queue) }

      it("returns null") {
        assertThat(queue.poll(), absent())
      }
    }

    context("there is a single message") {
      val queue = createQueue.invoke()
      val message = ExecutionStarting(Pipeline::class.java, "1")

      beforeGroup {
        queue.push(message)
      }

      afterGroup { shutdown(queue) }

      it("returns the queued message") {
        assertThat(queue.poll()?.id, equalTo(message.id))
      }

      it("returns no further messages") {
        assertThat(queue.poll(), absent())
      }
    }

    context("there are multiple messages") {
      val queue = createQueue.invoke()
      val message1 = ExecutionStarting(Pipeline::class.java, "1")
      val message2 = ExecutionStarting(Pipeline::class.java, "2")

      beforeGroup {
        queue.push(message1)
        queue.push(message2)
      }

      afterGroup { shutdown(queue) }

      it("returns the messages in the order they were queued") {
        assertThat(queue.poll()?.id, equalTo(message1.id))
        assertThat(queue.poll()?.id, equalTo(message2.id))
      }

      it("returns no further messages") {
        assertThat(queue.poll(), absent())
      }
    }

    context("there is a delayed message") {
      val delay = Duration.ofHours(1)

      context("whose delay has not expired") {
        val queue = createQueue.invoke()
        val message = ExecutionStarting(Pipeline::class.java, "1")

        beforeGroup {
          queue.push(message, delay.toHours(), HOURS)
        }

        afterGroup { shutdown(queue) }

        it("returns null when polled") {
          assertThat(queue.poll(), absent())
        }
      }

      context("whose delay has expired") {
        val queue = createQueue.invoke()
        val message = ExecutionStarting(Pipeline::class.java, "1")

        beforeGroup {
          queue.push(message, delay.toHours(), HOURS)
          clock.incrementBy(delay)
        }

        afterGroup { shutdown(queue) }

        it("returns the message when polled") {
          assertThat(queue.poll()?.id, equalTo(message.id))
        }
      }
    }
  }

  describe("acknowledging a non-existent message") {
    val queue = createQueue.invoke()

    afterGroup { shutdown(queue) }

    it("ignores invalid ack calls") {
      assertThat(
        { queue.ack(ExecutionStarting(Pipeline::class.java, "1")) },
        !throws<Exception>()
      )
    }
  }

  describe("message redelivery") {
    context("a message is acknowledged") {
      val queue = createQueue.invoke()
      val message = ExecutionStarting(Pipeline::class.java, "1")

      beforeGroup {
        queue.push(message)
      }

      afterGroup { shutdown(queue) }

      action("the queue is polled then the message acknowledged") {
        queue.poll()?.let(queue::ack)
        clock.incrementBy(queue.ackTimeout)
        triggerRedeliveryCheck(queue)
      }

      it("does not re-deliver the message") {
        assertThat(queue.poll(), absent())
      }
    }

    context("a message is not acknowledged") {
      val queue = createQueue.invoke()
      val message = ExecutionStarting(Pipeline::class.java, "1")

      beforeGroup {
        queue.push(message)
      }

      afterGroup { shutdown(queue) }

      action("the queue is polled then the message acknowledged") {
        queue.poll()
        clock.incrementBy(queue.ackTimeout)
        triggerRedeliveryCheck(queue)
      }

      it("does not re-deliver the message") {
        assertThat(queue.poll()?.id, equalTo(message.id))
      }
    }

    context("a message is not acknowledged more than once") {
      val queue = createQueue.invoke()
      val message = ExecutionStarting(Pipeline::class.java, "1")

      beforeGroup {
        queue.push(message)
      }

      afterGroup { shutdown(queue) }

      action("the queue is polled then the message acknowledged") {
        (1..2).forEach {
          assertThat(queue.poll()?.id, equalTo(message.id))
          clock.incrementBy(queue.ackTimeout)
          triggerRedeliveryCheck(queue)
        }
      }

      it("does not re-deliver the message") {
        assertThat(queue.poll()?.id, equalTo(message.id))
      }
    }
  }
}) {
  companion object {
    val clock = MutableClock(Instant.now())
  }
}
