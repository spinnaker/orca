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

package com.netflix.spinnaker.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAmount

class MutableClock(
  private var instant: Instant = Instant.now(),
  private val zone: ZoneId = ZoneId.systemDefault()
) : Clock() {

  override fun withZone(zone: ZoneId) = MutableClock(instant, zone)

  override fun getZone() = zone

  override fun instant() = instant

  fun incrementBy(amount: TemporalAmount) {
    instant = instant.plus(amount)
  }

  fun instant(newInstant: Instant) {
    instant = newInstant
  }
}
