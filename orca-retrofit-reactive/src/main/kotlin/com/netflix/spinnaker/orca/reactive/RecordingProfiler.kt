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

import org.springframework.http.HttpStatus
import retrofit.Profiler
import java.time.Duration

/**
 * Tracks timing and status information from requests to a Retrofit endpoint.
 */
class RecordingProfiler(
  private val metrics: WritableRequestMetrics
) : Profiler<Nothing> {

  override fun beforeCall(): Nothing? = null

  override fun afterCall(
    requestInfo: Profiler.RequestInformation,
    elapsedTime: Long,
    statusCode: Int,
    beforeCallData: Nothing?
  ) = metrics.record(
    Duration.ofMillis(elapsedTime),
    HttpStatus.valueOf(statusCode)
  )
}

interface RequestMetrics {
  val averageDuration: Duration
  val errorPercentage: Double
}

interface WritableRequestMetrics : RequestMetrics {
  fun record(duration: Duration, statusCode: HttpStatus)
}
