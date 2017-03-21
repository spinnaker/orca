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
import java.time.Clock
import java.time.Instant
import java.util.concurrent.DelayQueue
import java.util.concurrent.Delayed
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS

class InMemoryQueue<T>(
  val clock: Clock
) : Queue<T> {

  private val queue = DelayQueue<DelayedWrapper<T>>()

  override fun poll() = queue.poll()?.payload

  override fun push(message: T) =
    queue.put(DelayedWrapper(message, clock.instant(), clock))

  override fun push(message: T, delay: Long, unit: TimeUnit) =
    queue.put(DelayedWrapper(message, clock.instant().plus(delay, unit.toChronoUnit()), clock))
}

internal data class DelayedWrapper<out T>(
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
