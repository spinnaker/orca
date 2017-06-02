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
import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.metrics.QueueEvent.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.ApplicationListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.annotation.PostConstruct

/**
 * Monitors a queue and generates Atlas metrics.
 */
@Component
@ConditionalOnBean(MonitorableQueue::class)
open class AtlasQueueMonitor
@Autowired constructor(
  private val queue: MonitorableQueue,
  private val registry: Registry,
  private val clock: Clock
)
  : ApplicationListener<QueueEvent> {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun onApplicationEvent(event: QueueEvent) {
    when (event) {
      is QueuePolled -> _lastQueuePoll.set(event.instant)
      is RetryPolled -> _lastRetryPoll.set(event.instant)
      is MessagePushed -> pushCounter.increment()
      is MessageAcknowledged -> ackCounter.increment()
      is MessageRetried -> retryCounter.increment()
      is MessageDead -> deadMessageCounter.increment()
      else -> log.error("Unhandled event $event")
    }
  }

  @Scheduled(fixedRateString = "\${queue.depth.metric.frequency:1000}")
  fun pollQueueDepth() {
    _lastQueueDepth.set(queue.queueDepth)
    _lastUnackedDepth.set(queue.unackedDepth)
    _lastReadyDepth.set(queue.readyDepth)
    _lastOrphanedMessages.set(queue.orphanedMessages)
  }

  @PostConstruct fun registerGauges() {
    log.info("Monitorable queue implementation $queue found. Exporting metrics to Atlas.")

    registry.gauge("queue.depth", this, {
      it.lastQueueDepth.toDouble()
    })
    registry.gauge("queue.unacked.depth", this, {
      it.lastUnackedDepth.toDouble()
    })
    registry.gauge("queue.ready.depth", this, {
      it.lastReadyDepth.toDouble()
    })
    registry.gauge("queue.orphaned.messages", this, {
      it.lastOrphanedMessages.toDouble()
    })
    registry.gauge("queue.last.poll.age", this, {
      Duration
        .between(it.lastQueuePoll, clock.instant())
        .toMillis()
        .toDouble()
    })
    registry.gauge("queue.last.retry.check.age", this, {
      Duration
        .between(it.lastRetryPoll, clock.instant())
        .toMillis()
        .toDouble()
    })
  }

  /**
   * The last time the [Queue.poll] method was executed.
   */
  val lastQueuePoll: Instant
    get() = _lastQueuePoll.get()
  private val _lastQueuePoll = AtomicReference<Instant>(clock.instant())

  /**
   * The time the last [Queue.retry] method was executed.
   */
  val lastRetryPoll: Instant
    get() = _lastRetryPoll.get()
  private val _lastRetryPoll = AtomicReference<Instant>(clock.instant())

  /**
   * Number of messages on the queue when last measured with
   * [MonitorableQueue.queueDepth].
   */
  val lastQueueDepth: Int
    get() = _lastQueueDepth.get()
  private val _lastQueueDepth = AtomicInteger()

  /**
   * Number of un-acknowledged messages on the queue when last measured with
   * [MonitorableQueue.unackedDepth].
   */
  val lastUnackedDepth: Int
    get() = _lastUnackedDepth.get()
  private val _lastUnackedDepth = AtomicInteger()

  /**
   * Number of ready messages on the queue when last measured with
   * [MonitorableQueue.readyDepth].
   */
  val lastReadyDepth: Int
    get() = _lastReadyDepth.get()
  private val _lastReadyDepth = AtomicInteger()

  /**
   * Number of orphaned messages when last measured with
   * [MonitorableQueue.orphanedMessages].
   */
  val lastOrphanedMessages: Int
    get() = _lastOrphanedMessages.get()
  private val _lastOrphanedMessages = AtomicInteger()

  /**
   * Count of messages pushed to the queue.
   */
  private val pushCounter: Counter
    get() = registry.counter("queue.pushed.messages")

  /**
   * Count of messages successfully processed and acknowledged.
   */
  private val ackCounter: Counter
    get() = registry.counter("queue.acknowledged.messages")

  /**
   * Count of messages that have been retried. This does not mean unique
   * messages, so retrying the same message again will still increment this
   * count.
   */
  private val retryCounter: Counter
    get() = registry.counter("queue.retried.messages")

  /**
   * Count of messages that have exceeded [Queue.maxRetries] retry
   * attempts and have been sent to the dead message handler.
   */
  private val deadMessageCounter: Counter
    get() = registry.counter("queue.dead.messages")
}
