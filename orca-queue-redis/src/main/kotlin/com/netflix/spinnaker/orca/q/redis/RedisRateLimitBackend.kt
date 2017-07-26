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
package com.netflix.spinnaker.orca.q.redis

import com.netflix.spinnaker.orca.q.trafficshaping.ratelimit.RateLimit
import com.netflix.spinnaker.orca.q.trafficshaping.ratelimit.RateLimitBackend
import com.netflix.spinnaker.orca.q.trafficshaping.ratelimit.RateLimitContext
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Keys used, all namespaced by the interceptor's name, e.g. "appRateLimit":
 *
 * - queue:trafficShaping:{ns}:learning - interceptor flag to enable/disable
 * - queue:trafficShaping:{ns}:capacity - number of actions per second
 * - queue:trafficShaping:{ns}:ignoring - a list of ignored subjects
 * - queue:trafficShaping:{ns}:enforcing - a list of enforced subjects
 * - queue:trafficShaping:{ns}:{subject}:learning - subject-specific learning flag
 * - queue:trafficShaping:{ns}:{subject}:capacity - subject-specific capacity
 * - queue:trafficShaping:{ns}:duration - rate limit duration
 */
class RedisRateLimitBackend(
  private val pool: Pool<Jedis>,
  private val clock: Clock
) : RateLimitBackend {

  override fun incrementAndGet(subject: String, context: RateLimitContext): RateLimit {
    pool.resource.use { redis ->
      val key = "queue:trafficShaping:${context.namespace}:$subject"
      val count = redis.get(key)
      val ttl = getTTL(redis, key, count == null)

      val newCount = redis.incr(key)
      if (newCount == 1L) {
        redis.pexpire(key, ttl.minusMillis(clock.instant().toEpochMilli()).toEpochMilli())
      }

      val capacity = getCapacity(redis, context.namespace, subject, context.capacity)

      val limiting = Math.max(capacity - newCount, 0) == 0L
      if (isLearning(redis, context.namespace, subject, !context.enforcing)) {
        return RateLimit(limiting, Duration.ZERO, false)
      }

      return RateLimit(limiting, Duration.of(getDuration(redis, context.namespace, context.duration), ChronoUnit.MILLIS), true)
    }
  }

  private fun getTTL(redis: Jedis, key: String, missingKey: Boolean): Instant
    = if (missingKey) clock.instant().plusMillis(1000) else clock.instant().plusMillis(redis.pttl(key))

  private fun isLearning(redis: Jedis, ns: String, subject: String, default: Boolean): Boolean {
    val ignoring = redis.smembers("queue:trafficShaping:$ns:ignoring")
    if (ignoring.contains(subject)) {
      return true
    }

    val enforcing = redis.smembers("queue:trafficShaping:$ns:enforcing")
    if (enforcing.contains(subject)) {
      return false
    }

    val nsLearning: String? = redis.get("queue:trafficShaping:$ns:learning")
    val appLearning: String? = redis.get("queue:trafficShaping:$ns:$subject:learning")

    return appLearning?.toBoolean() ?: nsLearning?.toBoolean() ?: default
  }

  private fun getCapacity(redis: Jedis, ns: String, subject: String, default: Int): Int {
    val subjectCap: String? = redis.get("queue:trafficShaping:$ns:$subject:capacity")
    if (subjectCap != null) {
      return subjectCap.toInt()
    }

    val nsCap: String? = redis.get("queue:trafficShaping:$ns:capacity")
    if (nsCap != null) {
      return nsCap.toInt()
    }

    return default
  }

  private fun getDuration(redis: Jedis, ns: String, default: Long): Long {
    val nsDuration: String? = redis.get("queue.trafficShaping:$ns:duration")
    if (nsDuration != null) {
      return nsDuration.toLong()
    }

    return default
  }
}
