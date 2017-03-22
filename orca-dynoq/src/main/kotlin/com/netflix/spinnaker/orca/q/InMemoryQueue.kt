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

import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.threeten.extra.Temporals.chronoUnit
import java.io.Closeable
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.DelayQueue
import java.util.concurrent.Delayed
import java.util.concurrent.Executors.newSingleThreadScheduledExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import javax.annotation.PreDestroy

class InMemoryQueue<T : Message>(
  val clock: Clock,
  val ackTimeout: Duration = Duration.ofMinutes(1)
) : Queue<T>, Closeable {

  private val log: Logger = getLogger(javaClass)

  private val queue = DelayQueue<DelayedMessage<T>>()
  private val unacked = DelayQueue<DelayedMessage<T>>()
  private val executor = newSingleThreadScheduledExecutor()
  private val redeliveryWatcher = executor
    .scheduleWithFixedDelay(this::redeliver, 10, 10, MILLISECONDS)

  override fun poll(): T? {
    val message = queue.poll()
    return message?.let {
      unacked.put(DelayedMessage(it.payload, clock.instant().plus(ackTimeout), clock))
      it.payload
    }
  }

  override fun push(message: T) =
    queue.put(DelayedMessage(message, clock.instant(), clock))

  override fun push(message: T, delay: Long, unit: TimeUnit) =
    queue.put(DelayedMessage(message, clock.instant().plus(delay, unit.toChronoUnit()), clock))

  override fun ack(message: T) {
    unacked.removeIf { it.payload.id == message.id }
  }

  @PreDestroy override fun close() {
    redeliveryWatcher.cancel(false)
    executor.shutdown()
  }

  private fun redeliver() {
    unacked.pollAll {
      log.warn("redelivering unacked message ${it.payload}")
      queue.put(DelayedMessage(it.payload, clock.instant(), clock))
    }
  }

  private fun <T : Delayed> DelayQueue<T>.pollAll(block: (T) -> Unit) {
    var done = false
    while (!done) {
      val value = poll()
      if (value == null) {
        done = true
      } else {
        block.invoke(value)
      }
    }
  }
}

internal data class DelayedMessage<out T : Message>(
  val payload: T,
  val scheduledTime: Instant,
  val clock: Clock
) : Delayed {

  override fun compareTo(other: Delayed) =
    getDelay(MILLISECONDS).compareTo(other.getDelay(MILLISECONDS))

  override fun getDelay(unit: TimeUnit) =
    clock.instant().until(scheduledTime, unit.toChronoUnit())
}

private fun TimeUnit.toChronoUnit() = chronoUnit(this)
