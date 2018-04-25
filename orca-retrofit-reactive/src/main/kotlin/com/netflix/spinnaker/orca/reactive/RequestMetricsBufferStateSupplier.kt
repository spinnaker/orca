/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.reactive

import com.netflix.spinnaker.orca.qos.BufferState
import com.netflix.spinnaker.orca.qos.BufferState.ACTIVE
import com.netflix.spinnaker.orca.qos.BufferState.INACTIVE
import com.netflix.spinnaker.orca.qos.BufferStateSupplier
import java.time.Duration

/**
 * Determines execution buffering policy based on request metric thresholds.
 */
class RequestMetricsBufferStateSupplier(
  private val metrics: RequestMetrics,
  private val averageRequestDurationThreshold: Duration,
  private val errorPercentageThreshold: Int
) : BufferStateSupplier {
  override fun get(): BufferState =
    when {
      metrics.averageDuration > averageRequestDurationThreshold -> ACTIVE
      metrics.errorPercentage > errorPercentageThreshold        -> ACTIVE
      else                                                      -> INACTIVE
    }
}
