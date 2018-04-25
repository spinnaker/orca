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

import com.netflix.spinnaker.orca.qos.BufferState.ACTIVE
import com.netflix.spinnaker.orca.qos.BufferState.INACTIVE
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.*
import java.time.Duration

internal object RequestMetricsBufferStateSupplierTest : Spek({

  val metrics = mock<RequestMetrics>()
  val averageRequestDurationThreshold = Duration.ofSeconds(5)
  val errorPercentageThreshold = 25
  val subject = RequestMetricsBufferStateSupplier(
    metrics,
    averageRequestDurationThreshold,
    errorPercentageThreshold
  )

  describe("controlling execution buffering with request metrics") {
    given("everything looks good") {
      beforeGroup {
        whenever(metrics.averageDuration) doReturn averageRequestDurationThreshold.minusSeconds(1)
        whenever(metrics.errorPercentage) doReturn (errorPercentageThreshold - 1).toDouble()
      }

      afterGroup { reset(metrics) }

      it("does not buffer incoming executions") {
        assertThat(subject.get()).isEqualTo(INACTIVE)
      }
    }

    given("average request durations are up") {
      beforeGroup {
        whenever(metrics.averageDuration) doReturn averageRequestDurationThreshold.plusSeconds(1)
        whenever(metrics.errorPercentage) doReturn (errorPercentageThreshold - 1).toDouble()
      }

      afterGroup { reset(metrics) }

      it("buffers incoming executions") {
        assertThat(subject.get()).isEqualTo(ACTIVE)
      }
    }

    given("request error rates are up") {
      beforeGroup {
        whenever(metrics.averageDuration) doReturn averageRequestDurationThreshold.minusSeconds(1)
        whenever(metrics.errorPercentage) doReturn (errorPercentageThreshold + 1).toDouble()
      }

      afterGroup { reset(metrics) }

      it("buffers incoming executions") {
        assertThat(subject.get()).isEqualTo(ACTIVE)
      }
    }
  }
})
