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

import com.google.common.base.Ticker
import com.google.common.cache.CacheBuilder
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.TOO_MANY_REQUESTS
import java.time.Duration
import java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * A mechanism for recording request metrics that has a look-back limit. That is
 * the metrics it reports represent those reported over the last [expireAfter]
 * duration.
 */
class WindowedRequestMetrics
@JvmOverloads constructor(
  override val name: String,
  private val expireAfter: Duration,
  private val ticker: Ticker = Ticker.systemTicker()
) : WritableRequestMetrics {

  private object Unused

  private val cache = CacheBuilder.newBuilder()
    .expireAfterWrite(expireAfter.toMillis(), MILLISECONDS)
    .ticker(ticker)
    .weakKeys() // stops identical values colliding, we want all entries
    .build<RequestMetric, Unused>()

  // I'm sure this is really efficient
  override val averageDuration: Duration
    get() = cache.asMap().keys.map { it.duration }.average()

  override val errorPercentage: Double
    get() = when (cache.size()) {
      0L   -> 0.0
      else -> cache.run {
        // We have to create a new collection as `Cache.size` is only weakly
        // consistent and can report a different value to the actual number of
        // unexpired keys. This is also true of the map returned by `toMap`.
        ArrayList(asMap().keys).percentMatching { it.status.isError }
      }
    }

  override fun record(duration: Duration, statusCode: HttpStatus) {
    cache.put(RequestMetric(duration, statusCode), Unused)
  }

  internal fun clear() {
    cache.invalidateAll()
  }
}

private data class RequestMetric(
  val duration: Duration,
  val status: HttpStatus
)

private fun <E> Collection<E>.percentMatching(predicate: (E) -> Boolean): Double =
  (count(predicate) * 100.0) / size

private val HttpStatus.isError: Boolean
  get() = is5xxServerError || this == TOO_MANY_REQUESTS

private fun Collection<Duration>.average(): Duration =
  map(Duration::toMillis).average().toLong().let(Duration::ofMillis)
