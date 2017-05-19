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

package com.netflix.spinnaker.orca.q.memory

import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.q.Queue
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.threeten.extra.Temporals.chronoUnit
import java.io.Closeable
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.TemporalAmount
import java.util.*
import java.util.UUID.randomUUID
import java.util.concurrent.DelayQueue
import java.util.concurrent.Delayed
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.annotation.PreDestroy

class InMemoryQueue(
  private val clock: Clock,
  override val ackTimeout: TemporalAmount = Duration.ofMinutes(1),
  override val deadMessageHandler: DeadMessageCallback
) : MonitoredQueue, Closeable {

  private val log: Logger = getLogger(javaClass)

  private val queue = DelayQueue<Envelope>()
  private val unacked = DelayQueue<Envelope>()
  private val redeliveryWatcher = ScheduledAction(this::redeliver)
  private val redeliveryCount = AtomicLong()
  private val lastRedeliveryCheck = AtomicReference<Instant?>()
  private val deadLetterCount = AtomicLong()

  override fun poll(callback: (Message, () -> Unit) -> Unit) {
    queue.poll()?.let { envelope ->
      unacked.put(envelope.copy(scheduledTime = clock.instant().plus(ackTimeout)))
      callback.invoke(envelope.payload) {
        ack(envelope.id)
      }
    }
  }

  override fun push(message: Message, delay: TemporalAmount) =
    queue.put(Envelope(message, clock.instant().plus(delay), clock))

  private fun ack(messageId: UUID) {
    unacked.removeIf { it.id == messageId }
  }

  override fun queueState() = QueueMetrics(
    queue.size.toLong(),
    unacked.size.toLong(),
    redeliveryCount.get(),
    deadLetterCount.get(),
    lastRedeliveryCheck.get()
  )

  @PreDestroy override fun close() {
    log.info("stopping redelivery watcher for $this")
    redeliveryWatcher.close()
  }

  internal fun redeliver() {
    val now = clock.instant()
    lastRedeliveryCheck.lazySet(now)
    unacked.pollAll {
      if (it.count >= Queue.maxRedeliveries) {
        deadMessageHandler.invoke(this, it.payload)
        deadLetterCount.incrementAndGet()
      } else {
        log.warn("redelivering unacked message ${it.payload}")
        queue.put(it.copy(scheduledTime = now, count = it.count + 1))
        redeliveryCount.incrementAndGet()
      }
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

internal data class Envelope(
  val id: UUID,
  val payload: Message,
  val scheduledTime: Instant,
  val clock: Clock,
  val count: Int = 1
) : Delayed {
  constructor(payload: Message, scheduledTime: Instant, clock: Clock) :
    this(randomUUID(), payload, scheduledTime, clock)

  override fun compareTo(other: Delayed) =
    getDelay(MILLISECONDS).compareTo(other.getDelay(MILLISECONDS))

  override fun getDelay(unit: TimeUnit) =
    clock.instant().until(scheduledTime, unit.toChronoUnit())
}

private fun TimeUnit.toChronoUnit() = chronoUnit(this)
