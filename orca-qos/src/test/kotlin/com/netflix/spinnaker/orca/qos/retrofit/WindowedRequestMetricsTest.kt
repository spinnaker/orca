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

package com.netflix.spinnaker.orca.qos.retrofit

import ch.tutteli.atrium.api.cc.infix.en_UK.*
import ch.tutteli.atrium.verbs.expect.*
import com.google.common.testing.FakeTicker
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
        expect(subject.averageDuration) toBe ZERO
      }

      it("reports zero error percentage") {
        expect(subject.errorPercentage) toBe 0.0
      }
    }

    given("a a series of recorded requests") {
      beforeGroup {
        subject.record(Duration.ofSeconds(10), OK)
        subject.record(Duration.ofSeconds(10), SEE_OTHER)
        // below are client or server errors
        subject.record(Duration.ofSeconds(29), TOO_MANY_REQUESTS)
        subject.record(Duration.ofSeconds(5), TOO_MANY_REQUESTS)
        subject.record(Duration.ofSeconds(30), INTERNAL_SERVER_ERROR)
      }

      afterGroup(subject::clear)

      it("reports the average duration") {
        expect(subject.averageDuration) toBe Duration.ofMillis(16_800)
      }

      it("reports the error percentage") {
        expect(subject.errorPercentage) toBe 60.0
      }
    }

    describe("expiring metrics") {
      given("none of the metrics have expired yet") {
        beforeGroup {
          subject.record(Duration.ofSeconds(60), GATEWAY_TIMEOUT)
          subject.record(Duration.ofSeconds(60), GATEWAY_TIMEOUT)
          subject.record(Duration.ofSeconds(59), GATEWAY_TIMEOUT)
          subject.record(Duration.ofSeconds(1), OK)
          // advance time, but not enough to expire any of these requests
          ticker.advance(9, SECONDS)
        }

        afterGroup(subject::clear)

        it("reports average time based on the non-expired metrics") {
          expect(subject.averageDuration) toBe Duration.ofSeconds(45)
        }

        it("reports error percentage based on the non-expired metrics") {
          expect(subject.errorPercentage) toBe 75.0
        }
      }

      given("some of the bad metrics have expired") {
        beforeGroup {
          subject.record(Duration.ofSeconds(60), GATEWAY_TIMEOUT)
          subject.record(Duration.ofSeconds(60), GATEWAY_TIMEOUT)
          ticker.advance(5, SECONDS)
          subject.record(Duration.ofSeconds(59), GATEWAY_TIMEOUT)
          subject.record(Duration.ofSeconds(1), OK)
          // advance time enough that the first batch of requests expire
          ticker.advance(5, SECONDS)
        }

        afterGroup(subject::clear)

        it("reports average time based on the non-expired metrics") {
          expect(subject.averageDuration) toBe Duration.ofSeconds(30)
        }

        it("reports error percentage based on the non-expired metrics") {
          expect(subject.errorPercentage) toBe 50.0
        }
      }

      given("all of the bad metrics have expired") {
        beforeGroup {
          subject.record(Duration.ofSeconds(120), GATEWAY_TIMEOUT)
          subject.record(Duration.ofSeconds(60), GATEWAY_TIMEOUT)
          subject.record(Duration.ofSeconds(59), GATEWAY_TIMEOUT)
          ticker.advance(5, SECONDS)
          subject.record(Duration.ofSeconds(1), OK)
          // advance time enough that the first batch of requests expire
          ticker.advance(5, SECONDS)
        }

        afterGroup(subject::clear)

        it("reports average time based on the non-expired metrics") {
          expect(subject.averageDuration) toBe Duration.ofSeconds(1)
        }

        it("reports error percentage based on the non-expired metrics") {
          expect(subject.errorPercentage) toBe 0.0
        }
      }
    }
  }
})
