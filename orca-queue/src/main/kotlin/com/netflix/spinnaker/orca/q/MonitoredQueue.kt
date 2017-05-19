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

import java.time.Instant

/**
 * Optional interface [Queue] implementations may support in order to provide
 * hooks for analytics.
 */
interface MonitoredQueue : Queue {

  fun queueState(): QueueMetrics

}

data class QueueMetrics(
  /**
   * Number of messages currently queued for delivery including any not yet due.
   */
  val queueDepth: Long,

  /**
   * Number of messages currently being processed but not yet acknowledged.
   */
  val unackedDepth: Long,

  /**
   * Count of messages that have been re-delivered. This does not mean unique
   * messages, so re-delivering the same message again will still increment this
   * count.
   */
  val redeliveryCount: Long,

  /**
   * Count of messages that have exceeded [Queue.maxRedeliveries] re-delivery
   * attempts and have been sent to the dead message handler.
   */
  val deadLetterCount: Long,

  /**
   * The time the last re-delivery check was executed.
   */
  val lastRedeliveryCheck: Instant?
)
