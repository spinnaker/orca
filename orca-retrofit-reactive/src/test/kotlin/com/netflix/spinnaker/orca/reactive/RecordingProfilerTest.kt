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

import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.*
import org.springframework.http.HttpStatus
import retrofit.Profiler.RequestInformation
import java.time.Duration

internal object RecordingProfilerTest : Spek({

  val metrics = mock<WritableRequestMetrics>()
  val subject = RecordingProfiler(metrics)

  describe("recording request times") {
    val requestInfo = RequestInformation("GET", "https://river/", "/whatever", 0, "application/json")
    val duration = Duration.ofSeconds(15)
    val status = HttpStatus.OK

    on("tracking some requests") {
      subject.afterCall(requestInfo, duration.toMillis(), status.value(), null)
    }

    it("records average request duration") {
      verify(metrics).record(duration, status)
    }
  }
})
