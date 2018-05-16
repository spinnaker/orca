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

package com.netflix.spinnaker.q.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.common.hash.Hashing
import com.netflix.spinnaker.KotlinOpen
import com.netflix.spinnaker.q.*
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.q.metrics.*
import com.netflix.spinnaker.q.migration.SerializationMigrator
import org.funktionale.partials.partially1
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCommands
import redis.clients.jedis.ScriptingCommands
import redis.clients.jedis.Transaction
import redis.clients.jedis.params.sortedset.ZAddParams
import redis.clients.util.Pool
import java.io.IOException
import java.lang.String.format
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Duration.ZERO
import java.time.Instant
import java.time.temporal.TemporalAmount
import java.util.*

@KotlinOpen
class RedisQueue(
  private val queueName: String,
  private val pool: Pool<Jedis>,
  private val clock: Clock,
  private val lockTtlSeconds: Int = 10,
  private val mapper: ObjectMapper,
  private val serializationMigrator: Optional<SerializationMigrator>,
  override val ackTimeout: TemporalAmount = Duration.ofMinutes(1),
  override val deadMessageHandlers: List<DeadMessageCallback>,
  override val publisher: EventPublisher
) : MonitorableQueue {

  private val log: Logger = LoggerFactory.getLogger(javaClass)

  private val queueKey = "$queueName.queue"
  private val unackedKey = "$queueName.unacked"
  private val messagesKey = "$queueName.messages"
  private val locksKey = "$queueName.locks"

  // TODO: use AttemptsAttribute instead
  private val attemptsKey = "$queueName.attempts"

  // Internal ObjectMapper that enforces deterministic property ordering for use only in hashing.
  private val hashObjectMapper = ObjectMapper().copy().apply {
    enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
  }

  init {
    log.info("Configured queue: $queueName")
  }

  override fun poll(callback: (Message, () -> Unit) -> Unit) {
    pool.resource.use { redis ->
      redis.readMessageWithLock()
        ?.also { (fingerprint, scheduledTime, json) ->
          val ack = this::ackMessage.partially1(fingerprint)
          redis.readMessage(fingerprint, json) { message ->
            val attempts = message.getAttribute<AttemptsAttribute>()?.attempts
              ?: 0
            val maxAttempts = message.getAttribute<MaxAttemptsAttribute>()?.maxAttempts
              ?: 0

            if (maxAttempts > 0 && attempts > maxAttempts) {
              log.warn("Message $fingerprint with payload $message exceeded $maxAttempts retries")
              handleDeadMessage(message)
              redis.removeMessage(fingerprint)
              fire(MessageDead)
            } else {
              fire(MessageProcessing(message, scheduledTime, clock.instant()))
              callback(message, ack)
            }
          }
        }
      fire(QueuePolled)
    }
  }

  override fun push(message: Message, delay: TemporalAmount) {
    pool.resource.use { redis ->
      redis.firstFingerprint(queueKey, message.fingerprint()).also { fingerprint ->
        if (fingerprint != null) {
          log.warn("Re-prioritizing message as an identical one is already on the queue: $fingerprint, message: $message")
          redis.zadd(queueKey, score(delay), fingerprint)
          fire(MessageDuplicate(message))
        } else {
          redis.queueMessage(message, delay)
          fire(MessagePushed(message))
        }
      }
    }
  }

  override fun reschedule(message: Message, delay: TemporalAmount) {
    pool.resource.use { redis ->
      val fingerprint = message.fingerprint().latest
      log.debug("Re-scheduling message: $message, fingerprint: $fingerprint to deliver in $delay")
      val status: Long = redis.zadd(queueKey, score(delay), fingerprint, ZAddParams.zAddParams().xx())
      if (status.toInt() == 1) {
        fire(MessageRescheduled(message))
      } else {
        fire(MessageNotFound(message))
      }
    }
  }

  override fun ensure(message: Message, delay: TemporalAmount) {
    pool.resource.use { redis ->
      val fingerprint = message.fingerprint()
      if (!redis.anyZismember(queueKey, fingerprint.all) && !redis.anyZismember(unackedKey, fingerprint.all)) {
        log.debug("Pushing ensured message onto queue as it does not exist in queue or unacked sets")
        push(message, delay)
      }
    }
  }

  @Scheduled(fixedDelayString = "\${queue.retry.frequency.ms:10000}")
  override fun retry() {
    pool.resource.use { redis ->
      redis
        .zrangeByScore(unackedKey, 0.0, score())
        .let { fingerprints ->
          if (fingerprints.size > 0) {
            fingerprints
              .map { "$locksKey:$it" }
              .let { redis.del(*it.toTypedArray()) }
          }

          fingerprints.forEach { fingerprint ->
            val attempts = redis.hgetInt(attemptsKey, fingerprint)
            if (attempts >= Queue.maxRetries) {
              redis.readMessageWithoutLock(fingerprint) { message ->
                log.warn("Message $fingerprint with payload $message exceeded max retries")
                handleDeadMessage(message)
                redis.removeMessage(fingerprint)
              }
              fire(MessageDead)
            } else {
              if (redis.zismember(queueKey, fingerprint)) {
                redis
                  .multi {
                    zrem(unackedKey, fingerprint)
                    zadd(queueKey, score(), fingerprint)
                    // we only need to read the message for metrics purposes
                    hget(messagesKey, fingerprint)
                  }
                  .let { (_, _, json) ->
                    mapper
                      .readValue<Message>(runSerializationMigration(json as String))
                      .let { message ->
                        log.warn("Not retrying message $fingerprint because an identical message is already on the queue")
                        fire(MessageDuplicate(message))
                      }
                  }
              } else {
                log.warn("Retrying message $fingerprint after $attempts attempts")
                redis.requeueMessage(fingerprint)
                fire(MessageRetried)
              }
            }
          }
        }
        .also {
          fire(RetryPolled)
        }
    }
  }

  override fun readState(): QueueState =
    pool.resource.use { redis ->
      redis.multi {
        zcard(queueKey)
        zcount(queueKey, 0.0, score())
        zcard(unackedKey)
        hlen(messagesKey)
      }
        .map { (it as Long).toInt() }
        .let { (queued, ready, processing, messages) ->
          return QueueState(
            depth = queued,
            ready = ready,
            unacked = processing,
            orphaned = messages - (queued + processing)
          )
        }
    }

  override fun containsMessage(predicate: (Message) -> Boolean): Boolean =
    pool.resource.use { redis ->
      var found = false
      var cursor = "0"
      while (!found) {
        redis.hscan(messagesKey, cursor).apply {
          found = result
            .map { mapper.readValue<Message>(it.value) }
            .any(predicate)
          cursor = stringCursor
        }
        if (cursor == "0") break
      }
      return found
    }

  override fun toString() = "RedisQueue[$queueName]"

  private fun ackMessage(fingerprint: String) {
    pool.resource.use { redis ->
      if (redis.zismember(queueKey, fingerprint)) {
        // only remove this message from the unacked queue as a matching one has
        // been put on the main queue
        redis.multi {
          zrem(unackedKey, fingerprint)
          del("$locksKey:$fingerprint")
        }
      } else {
        redis.removeMessage(fingerprint)
      }
      fire(MessageAcknowledged)
    }
  }

  private fun Jedis.queueMessage(message: Message, delay: TemporalAmount = ZERO) {
    val fingerprint = message.fingerprint().latest

    // ensure the message has the attempts tracking attribute
    message.setAttribute(
      message.getAttribute() ?: AttemptsAttribute()
    )

    multi {
      hset(messagesKey, fingerprint, mapper.writeValueAsString(message))
      zadd(queueKey, score(delay), fingerprint)
    }
  }

  private fun Jedis.requeueMessage(fingerprint: String) {
    multi {
      zrem(unackedKey, fingerprint)
      zadd(queueKey, score(), fingerprint)
    }
  }

  private fun Jedis.removeMessage(fingerprint: String) {
    multi {
      zrem(queueKey, fingerprint)
      zrem(unackedKey, fingerprint)
      hdel(messagesKey, fingerprint)
      del("$locksKey:$fingerprint")

      // TODO: use AttemptAttribute instead
      hdel(attemptsKey, fingerprint)
    }
  }

  private fun Jedis.readMessageWithoutLock(fingerprint: String, block: (Message) -> Unit) {
    eval(READ_MESSAGE_WITHOUT_LOCK,
      listOf(
        queueKey,
        unackedKey,
        messagesKey,
        attemptsKey
      ),
      listOf(
        fingerprint,
        format("%f", score(ackTimeout)),
        format("%f", score())
      )
    ).let {
      readMessage(fingerprint, it as String?, block)
    }
  }

  private fun ScriptingCommands.readMessageWithLock(): Triple<String, Instant, String?>? {
    val response = eval(READ_MESSAGE_WITH_LOCK, listOf(
      queueKey,
      unackedKey,
      locksKey,
      messagesKey,
      attemptsKey
    ), listOf(
      score().toString(),
      10.toString(), // TODO rz - make this configurable.
      lockTtlSeconds.toString(),
      format("%f", score(ackTimeout)),
      format("%f", score())
    ))
    if (response is List<*>) {
      return Triple(
        response[0].toString(), // fingerprint
        Instant.ofEpochMilli(response[1].toString().toLong()), // fingerprintScore
        response[2]?.toString() // message
      )
    }
    if (response == "ReadLockFailed") {
      // This isn't a "bad" thing, but means there's more work than keiko can process in a cycle
      // in this case, but may be a signal to tune `peekFingerprintCount`
      fire(LockFailed)
    }
    return null
  }

  /**
   * Tries to read the message with the specified [fingerprint] passing it to
   * [block]. If it's not accessible for whatever reason any references are
   * cleaned up.
   */
  private fun Jedis.readMessage(fingerprint: String, json: String?, block: (Message) -> Unit) {
    if (json == null) {
      log.error("Payload for message $fingerprint is missing")
      // clean up what is essentially an unrecoverable message
      removeMessage(fingerprint)
    } else {
      try {
        val message = mapper.readValue<Message>(runSerializationMigration(json))
          .apply {
            // TODO: AttemptsAttribute could replace `attemptsKey`
            val currentAttempts = (getAttribute() ?: AttemptsAttribute())
              .run { copy(attempts = attempts + 1) }
            setAttribute(currentAttempts)
          }

        hset(messagesKey, fingerprint, mapper.writeValueAsString(message))

        block.invoke(message)
      } catch (e: IOException) {
        log.error("Failed to read message $fingerprint, requeuing...", e)
        requeueMessage(fingerprint)
      }
    }
  }

  private fun runSerializationMigration(json: String): String {
    if (serializationMigrator.isPresent) {
      return serializationMigrator.get().migrate(json)
    }
    return json
  }

  private fun handleDeadMessage(message: Message) {
    deadMessageHandlers.forEach {
      it.invoke(this, message)
    }
  }

  /**
   * @return current time (plus optional [delay]) converted to a score for a
   * Redis sorted set.
   */
  private fun score(delay: TemporalAmount = ZERO) =
    clock.instant().plus(delay).toEpochMilli().toDouble()

  private inline fun <reified R> ObjectMapper.readValue(content: String): R =
    readValue(content, R::class.java)

  private fun Jedis.multi(block: Transaction.() -> Unit) =
    multi().use { tx ->
      tx.block()
      tx.exec()
    }

  private fun JedisCommands.hgetInt(key: String, field: String, default: Int = 0) =
    hget(key, field)?.toInt() ?: default

  private fun JedisCommands.zismember(key: String, member: String) =
    zrank(key, member) != null

  private fun JedisCommands.anyZismember(key: String, members: Set<String>) =
    members.any { zismember(key, it) }

  private fun JedisCommands.firstFingerprint(key: String, fingerprint: Fingerprint) =
    fingerprint.all.firstOrNull { zismember(key, it) }

  @Deprecated("Hashes the attributes property, which is mutable")
  private fun Message.hashV1() =
    Hashing
      .murmur3_128()
      .hashString(toString(), Charset.defaultCharset())
      .toString()

  private fun Message.hashV2() =
    hashObjectMapper.convertValue(this, MutableMap::class.java)
      .apply { remove("attributes") }
      .let {
        Hashing
          .murmur3_128()
          .hashString("v2:${hashObjectMapper.writeValueAsString(it)}", StandardCharsets.UTF_8)
          .toString()
      }

  private fun Message.fingerprint() =
    hashV2().let { Fingerprint(latest = it, all = setOf(it, hashV1())) }

  internal data class Fingerprint(
    val latest: String,
    val all: Set<String> = setOf()
  )
}

private const val READ_MESSAGE = """
  local java_scientific = function(x)
    return string.format("%.12E", x):gsub("\+", "")
  end

  -- get the message, move the fingerprint to the unacked queue and return
  local message = redis.call("HGET", messagesKey, fingerprint)

  -- check for an ack timeout override on the message
  local unackScore = unackDefaultScore
  if type(message) == "string" and message ~= nil then
    local ackTimeoutOverride = tonumber(cjson.decode(message)["ackTimeoutMs"])
    if ackTimeoutOverride ~= nil and unackBaseScore ~= nil then
      unackScore = unackBaseScore + ackTimeoutOverride
    end
  end

  unackScore = java_scientific(unackScore)

  redis.call("ZREM", queueKey, fingerprint)
  redis.call("ZADD", unackKey, unackScore, fingerprint)
  redis.call("HINCRBY", attemptsKey, fingerprint, 1)
"""

private const val READ_MESSAGE_WITH_LOCK = """
  local queueKey = KEYS[1]
  local unackKey = KEYS[2]
  local lockKey = KEYS[3]
  local messagesKey = KEYS[4]
  local attemptsKey = KEYS[5]
  local maxScore = ARGV[1]
  local peekFingerprintCount = ARGV[2]
  local lockTtlSeconds = ARGV[3]
  local unackDefaultScore = ARGV[4]
  local unackBaseScore = ARGV[5]

  local not_empty = function(x)
    return (type(x) == "table") and (not x.err) and (#x ~= 0)
  end

  local acquire_lock = function(fingerprints, locksKey, lockTtlSeconds)
    if not_empty(fingerprints) then
      local i=1
      while (i <= #fingerprints) do
        redis.call("ECHO", "attempting lock on " .. fingerprints[i])
        if redis.call("SET", locksKey .. ":" .. fingerprints[i], "\uD83D\uDD12", "EX", lockTtlSeconds, "NX") then
          redis.call("ECHO", "acquired lock on " .. fingerprints[i])
          return fingerprints[i], fingerprints[i+1]
        end
        i=i+2
      end
    end
    return nil, nil
  end

  -- acquire a lock on a fingerprint
  local fingerprints = redis.call("ZRANGEBYSCORE", queueKey, 0.0, maxScore, "WITHSCORES", "LIMIT", 0, peekFingerprintCount)
  local fingerprint, fingerprintScore = acquire_lock(fingerprints, lockKey, lockTtlSeconds)

  -- no lock could be acquired
  if fingerprint == nil then
    if #fingerprints == 0 then
      return "NoReadyMessages"
    end
    return "AcquireLockFailed"
  end

  $READ_MESSAGE

  return {fingerprint, fingerprintScore, message}
"""

// TODO rz - I don't think we should be incrementing attempts here...
private const val READ_MESSAGE_WITHOUT_LOCK = """
  local queueKey = KEYS[1]
  local unackKey = KEYS[2]
  local messagesKey = KEYS[3]
  local attemptsKey = KEYS[4]
  local fingerprint = ARGV[1]
  local unackDefaultScore = ARGV[2]
  local unackBaseScore = ARGV[3]

  $READ_MESSAGE

  return message
"""
