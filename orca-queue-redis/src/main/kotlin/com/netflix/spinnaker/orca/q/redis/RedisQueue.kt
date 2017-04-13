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

package com.netflix.spinnaker.orca.q.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.orca.q.Message
import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.ScheduledAction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCommands
import redis.clients.util.Pool
import java.io.Closeable
import java.time.Clock
import java.time.Duration
import java.time.Duration.ZERO
import javax.annotation.PreDestroy

class RedisQueue(
  queueName: String,
  private val pool: Pool<Jedis>,
  private val clock: Clock,
  override val ackTimeout: Duration = Duration.ofMinutes(1)
) : Queue, Closeable {

  private val mapper = ObjectMapper().apply {
    registerModule(KotlinModule())
  }
  private val log: Logger = LoggerFactory.getLogger(javaClass)

  private val queueKey = queueName + ".queue"
  private val unackedKey = queueName + ".unacked"
  private val messagesKey = queueName + ".messages"

  private val redeliveryWatcher = ScheduledAction(this::redeliver)

  override fun poll(): Message? =
    pool.resource.use { redis ->
      redis
        .pop(queueKey, unackedKey, ackTimeout)
        ?.let { redis.hget(messagesKey, it) }
        ?.let { mapper.readValue(it, Message::class.java) }
    }

  override fun push(message: Message) = push(message, ZERO)

  override fun push(message: Message, delay: Duration) {
    pool.resource.use { redis ->
      redis.hset(messagesKey, message.id.toString(), mapper.writeValueAsString(message))
      redis.zadd(queueKey, score(delay), message.id.toString())
    }
  }

  override fun ack(message: Message) {
    pool.resource.use { redis ->
      redis.zrem(unackedKey, message.id.toString())
      redis.hdel(messagesKey, message.id.toString())
    }
  }

  @PreDestroy override fun close() {
    log.info("stopping redelivery watcher for $this")
    redeliveryWatcher.close()
  }

  internal fun redeliver() {
    pool.resource.use { redis ->
      redis.popAll(unackedKey, queueKey).apply {
        if (size > 0) log.warn("Redelivering $size messages")
      }
    }
  }

  /**
   * Pop a single value from sorted set [from] and add them to sorted set [to]
   * (with optional [delay]).
   *
   * @return the popped value or `null`.
   */
  private fun JedisCommands.pop(from: String, to: String, delay: Duration = ZERO) =
    zrangeByScore(from, 0.0, score(), 0, 1)
      .also { move(from, to, delay, it) }
      .firstOrNull()

  /**
   * Pop values from sorted set [from] and add them to sorted set [to] (with
   * optional [delay]).
   *
   * @return the popped values.
   */
  private fun JedisCommands.popAll(from: String, to: String, delay: Duration = ZERO) =
    zrangeByScore(from, 0.0, score())
      .also { move(from, to, delay, it) }

  /**
   * Move [values] from sorted set [from] to sorted set [to]
   */
  private fun JedisCommands.move(from: String, to: String, delay: Duration, values: Set<String>) {
    if (values.isNotEmpty()) {
      assert(zrem(from, *values.toTypedArray()) == values.size.toLong())
      val score = score(delay)
      zadd(
        to,
        values.associateBy(::identity) { score }
      )
    }
  }

  /**
   * @return current time (plus optional [delay]) converted to a score for a
   * Redis sorted set.
   */
  private fun score(delay: Duration = ZERO) =
    clock.instant().plus(delay).toEpochMilli().toDouble()
}

private fun <T> identity(x: T): T = x
