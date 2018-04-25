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

import com.google.common.testing.FakeTicker
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.*
import org.springframework.http.HttpStatus.*
import java.time.Duration
import java.time.Duration.ZERO
import java.util.concurrent.TimeUnit.SECONDS

internal object WindowedRequestMetricsTest : Spek({

  val ticker = FakeTicker()
  val subject = WindowedRequestMetrics("Subject", Duration.ofSeconds(10), ticker)

  describe("reporting metrics") {
    given("no requests have been recorded") {
      it("reports zero average duration") {
        assertThat(subject.averageDuration).isEqualTo(ZERO)
      }

      it("reports zero error percentage") {
        assertThat(subject.errorPercentage).isEqualTo(0.0)
      }
    }

    given("a bunch of recorded requests") {
      val requests = listOf(
        Pair(Duration.ofSeconds(10), OK),
        Pair(Duration.ofSeconds(10), SEE_OTHER),
        // below are client or server errors
        Pair(Duration.ofSeconds(29), TOO_MANY_REQUESTS),
        Pair(Duration.ofSeconds(5), BAD_REQUEST),
        Pair(Duration.ofSeconds(30), INTERNAL_SERVER_ERROR)
      )

      afterGroup { subject.clear() }

      on("recording the requests") {
        requests.forEach { (duration, status) ->
          subject.record(duration, status)
        }
      }

      it("reports the average duration") {
        assertThat(subject.averageDuration)
          .isEqualTo(Duration.ofMillis(16_800))
      }

      it("reports the error percentage") {
        assertThat(subject.errorPercentage).isEqualTo(60.0)
      }
    }

    describe("expiring metrics") {
      given("none of the metrics have expired yet") {
        beforeGroup {
          subject.record(Duration.ofSeconds(60), GATEWAY_TIMEOUT)
          subject.record(Duration.ofSeconds(60), GATEWAY_TIMEOUT)
          subject.record(Duration.ofSeconds(59), GATEWAY_TIMEOUT)
          subject.record(Duration.ofSeconds(1), OK)
          ticker.advance(9, SECONDS)
        }

        afterGroup { subject.clear() }

        it("reports average time based on the non-expired metrics") {
          assertThat(subject.averageDuration).isEqualTo(Duration.ofSeconds(45))
        }

        it("reports error percentage based on the non-expired metrics") {
          assertThat(subject.errorPercentage).isEqualTo(75.0)
        }
      }

      given("some of the bad metrics have expired") {
        beforeGroup {
          subject.record(Duration.ofSeconds(60), GATEWAY_TIMEOUT)
          subject.record(Duration.ofSeconds(60), GATEWAY_TIMEOUT)
          ticker.advance(5, SECONDS)
          subject.record(Duration.ofSeconds(59), GATEWAY_TIMEOUT)
          subject.record(Duration.ofSeconds(1), OK)
          ticker.advance(5, SECONDS)
        }

        afterGroup { subject.clear() }

        it("reports average time based on the non-expired metrics") {
          assertThat(subject.averageDuration).isEqualTo(Duration.ofSeconds(30))
        }

        it("reports error percentage based on the non-expired metrics") {
          assertThat(subject.errorPercentage).isEqualTo(50.0)
        }
      }

      given("all of the bad metrics have expired") {
        beforeGroup {
          subject.record(Duration.ofSeconds(120), GATEWAY_TIMEOUT)
          subject.record(Duration.ofSeconds(60), GATEWAY_TIMEOUT)
          subject.record(Duration.ofSeconds(59), GATEWAY_TIMEOUT)
          ticker.advance(5, SECONDS)
          subject.record(Duration.ofSeconds(1), OK)
          ticker.advance(5, SECONDS)
        }

        afterGroup { subject.clear() }

        it("reports average time based on the non-expired metrics") {
          assertThat(subject.averageDuration).isEqualTo(Duration.ofSeconds(1))
        }

        it("reports error percentage based on the non-expired metrics") {
          assertThat(subject.errorPercentage).isEqualTo(0.0)
        }
      }
    }
  }
})
