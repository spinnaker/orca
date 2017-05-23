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
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.q.Message
import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.ScheduledAction
import com.netflix.spinnaker.orca.q.metrics.MonitoredQueue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCommands
import redis.clients.jedis.Transaction
import redis.clients.util.Pool
import toInstant
import java.io.Closeable
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Duration.ZERO
import java.time.Instant
import java.time.temporal.TemporalAmount
import java.util.UUID.randomUUID
import javax.annotation.PreDestroy

class RedisQueue(
  queueName: String,
  private val pool: Pool<Jedis>,
  private val clock: Clock,
  private val currentInstanceId: String,
  private val lockTtlSeconds: Int = Duration.ofDays(1).seconds.toInt(),
  override val ackTimeout: TemporalAmount = Duration.ofMinutes(1),
  override val deadMessageHandler: (Queue, Message) -> Unit,
  override val registry: Registry
) : MonitoredQueue, Closeable {

  private val mapper = ObjectMapper().apply {
    registerModule(KotlinModule())
  }
  private val log: Logger = LoggerFactory.getLogger(javaClass)

  private val queueKey = queueName + ".queue"
  private val unackedKey = queueName + ".unacked"
  private val messagesKey = queueName + ".messages"
  private val attemptsKey = queueName + ".attempts"
  private val locksKey = queueName + ".locks"
  private val lastQueuePollKey = queueName + ".poll.timestamp"
  private val lastRedeliveryPollKey = queueName + ".redelivery.timestamp"

  private val redeliveryWatcher = ScheduledAction(this::redeliver)

  override fun poll(callback: (Message, () -> Unit) -> Unit) {
    pool.resource.use { redis ->
      redis.apply {
        pop(queueKey, unackedKey, ackTimeout)
          ?.also { id -> hincrBy(attemptsKey, id, 1) }
          ?.let { id ->
            readMessage(id) { payload ->
              callback.invoke(payload) {
                ack(id)
                ackCounter.increment()
              }
            }
          }
        setCurrentTimestamp(lastQueuePollKey)
      }
    }
  }

  override fun push(message: Message, delay: TemporalAmount) {
    pool.resource.use { redis ->
      val id = randomUUID().toString()
      redis.multi {
        hset(messagesKey, id, mapper.writeValueAsString(message))
        zadd(queueKey, score(delay), id)
      }
    }
    pushCounter.increment()
  }

  override val queueDepth: Int
    get() = pool.resource.use { it.zcard(queueKey).toInt() }

  override val unackedDepth: Int
    get() = pool.resource.use { it.zcard(unackedKey).toInt() }

  override val lastQueuePoll: Instant?
    get() = pool.resource.use { it.getInstant(lastQueuePollKey) }

  override val lastRedeliveryPoll: Instant?
    get() = pool.resource.use { it.getInstant(lastRedeliveryPollKey) }

  @PreDestroy override fun close() {
    log.info("stopping redelivery watcher for $this")
    redeliveryWatcher.close()
  }

  private fun ack(id: String) {
    pool.resource.use { redis ->
      redis.multi {
        zrem(unackedKey, id)
        hdel(messagesKey, id)
        hdel(attemptsKey, id)
      }
    }
  }

  internal fun redeliver() {
    pool.resource.use { redis ->
      redis.apply {
        zrangeByScore(unackedKey, 0.0, score())
          .let { ids ->
            if (ids.size > 0) {
              ids.map { "$locksKey:$it" }.let { del(*it.toTypedArray()) }
            }

            ids.forEach { id ->
              val attempts = hgetInt(attemptsKey, id)
              if (attempts >= Queue.maxRedeliveries) {
                readMessage(id) { message ->
                  log.warn("Message $id with payload $message exceeded max re-deliveries")
                  handleDeadMessage(message)
                  ack(id)
                }
                deadMessageCounter.increment()
              } else {
                log.warn("Re-delivering message $id after $attempts attempts")
                move(unackedKey, queueKey, ZERO, setOf(id))
                redeliverCounter.increment()
              }
            }
          }
          .also {
            setCurrentTimestamp(lastRedeliveryPollKey)
          }
      }
    }
  }

  /**
   * Tries to read the message with the specified [id] passing it to [block].
   * If it's not accessible for whatever reason any references are cleaned up.
   */
  private fun Jedis.readMessage(id: String, block: (Message) -> Unit) {
    val json = hget(messagesKey, id)
    if (json == null) {
      log.error("Payload for message $id is missing")
      // clean up what is essentially an unrecoverable message
      multi {
        zrem(queueKey, id)
        zrem(unackedKey, id)
        hdel(attemptsKey, id)
      }
    } else {
      try {
        val message = mapper.readValue<Message>(json)
        block.invoke(message)
      } catch(e: IOException) {
        log.error("Failed to read message $id", e)
        multi {
          zrem(queueKey, id)
          zrem(unackedKey, id)
          hdel(messagesKey, id)
          hdel(attemptsKey, id)
        }
      }
    }
  }

  private fun handleDeadMessage(it: Message) {
    deadMessageHandler.invoke(this, it)
  }

  /**
   * Attempt to acquire a lock on a single value from a sorted set [from], adding
   * them to sorted set [to] (with optional [delay]).
   */
  private fun Jedis.pop(from: String, to: String, delay: TemporalAmount = ZERO) =
    zrangeByScore(from, 0.0, score(), 0, 1)
      .takeIf {
        // TODO: this isn't right, `it` is a set (often an empty one)
        getSet("$locksKey:$it", currentInstanceId) in listOf(null, currentInstanceId)
      }
      ?.also {
        expire("$locksKey:$it", lockTtlSeconds)
        move(from, to, delay, it)
      }
      ?.firstOrNull()

  /**
   * Move [values] from sorted set [from] to sorted set [to]
   */
  private fun Jedis.move(from: String, to: String, delay: TemporalAmount, values: Set<String>) {
    if (values.isNotEmpty()) {
      val score = score(delay)
      multi {
        zrem(from, *values.toTypedArray())
        zadd(to, values.associate { Pair(it, score) })
      }
    }
  }

  private fun JedisCommands.setCurrentTimestamp(key: String) =
    set(key, clock.millis().toString())

  private fun JedisCommands.hgetInt(key: String, field: String, default: Int = 0) =
    hget(key, field)?.toInt() ?: default

  private fun JedisCommands.getInt(key: String, default: Int = 0) =
    get(key)?.toInt() ?: default

  private fun JedisCommands.getInstant(key: String) =
    get(key)?.toLong()?.toInstant()

  /**
   * @return current time (plus optional [delay]) converted to a score for a
   * Redis sorted set.
   */
  private fun score(delay: TemporalAmount = ZERO) =
    clock.instant().plus(delay).toEpochMilli().toDouble()

  inline fun <reified R> ObjectMapper.readValue(content: String): R =
    readValue(content, R::class.java)

  private fun Jedis.multi(block: Transaction.() -> Unit) =
    multi().use { tx ->
      tx.block()
      tx.exec()
    }
}
