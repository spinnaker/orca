/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.q.memory

import com.google.common.cache.CacheBuilder
import com.netflix.spinnaker.config.RateLimitConfiguration
import com.netflix.spinnaker.orca.q.ratelimit.Rate
import com.netflix.spinnaker.orca.q.ratelimit.RateLimitBackend
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

private data class Bucket(
  var reset: LocalDateTime,
  var count: Int,
  var capacity: Int
)

class InMemoryLeakyBucketRateLimitBackend(
  val rateLimitConfiguration: RateLimitConfiguration
): RateLimitBackend {

  private val buckets = CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.SECONDS).build<String, Bucket>()

  override fun getRate(application: String): Rate {
    val bucket = buckets.get(application, {
      Bucket(
        reset = LocalDateTime.now().plus(rateLimitConfiguration.windowMs, ChronoUnit.MILLIS),
        count = 0,
        capacity = rateLimitConfiguration.capacity
      )
    })

    bucket.count++

    if (bucket.reset.isBefore(LocalDateTime.now())) {
      bucket.reset = LocalDateTime.now().plus(rateLimitConfiguration.windowMs, ChronoUnit.MILLIS)
      bucket.count = 0
    }

    return Rate(
      limiting = bucket.count > bucket.capacity,
      capacity = bucket.capacity,
      windowMs = rateLimitConfiguration.windowMs
    )
  }
}
