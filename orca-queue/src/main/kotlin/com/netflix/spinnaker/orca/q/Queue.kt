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

import org.threeten.extra.Temporals.chronoUnit
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

interface Queue {
  /**
   * @return the next message from the queue or `null` if the queue contains no
   * messages (or none whose delay has expired).
   */
  fun poll(): Message?

  /**
   * Push [message] for immediate delivery.
   */
  fun push(message: Message): Unit

  /**
   * Push [message] for delivery after [delay].
   */
  fun push(message: Message, delay: Duration)

  /**
   * The expired time after which un-acknowledged messages will be re-delivered.
   */
  val ackTimeout: Duration

  /**
   * Acknowledge successful processing of [message]. The message will not
   * subsequently be re-delivered.
   */
  fun ack(message: Message): Unit
}

fun <Q : Queue> Q.push(message: Message, delay: Long, unit: ChronoUnit) =
  push(message, Duration.of(delay, unit))

fun <Q : Queue> Q.push(message: Message, delay: Long, unit: TimeUnit) =
  push(message, delay, chronoUnit(unit))

fun <Q : Queue> Q.push(message: Message, delay: Pair<Long, TimeUnit>) =
  push(message, delay.first, delay.second)
