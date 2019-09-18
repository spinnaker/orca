package com.netflix.spinnaker.q.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.hash.Hashing
import com.netflix.spinnaker.KotlinOpen
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.q.AckAttemptsAttribute
import com.netflix.spinnaker.q.AttemptsAttribute
import com.netflix.spinnaker.q.DeadMessageCallback
import com.netflix.spinnaker.q.MaxAttemptsAttribute
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.metrics.MessageAcknowledged
import com.netflix.spinnaker.q.metrics.MessageDead
import com.netflix.spinnaker.q.metrics.MessageNotFound
import com.netflix.spinnaker.q.metrics.MessageProcessing
import com.netflix.spinnaker.q.metrics.MessagePushed
import com.netflix.spinnaker.q.metrics.MessageRescheduled
import com.netflix.spinnaker.q.metrics.MessageRetried
import com.netflix.spinnaker.q.metrics.MonitorableQueue
import com.netflix.spinnaker.q.metrics.QueuePolled
import com.netflix.spinnaker.q.metrics.QueueState
import com.netflix.spinnaker.q.metrics.RetryPolled
import com.netflix.spinnaker.q.metrics.fire
import com.netflix.spinnaker.q.migration.SerializationMigrator
import de.huxhorn.sulky.ulid.ULID
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.vavr.control.Try
import org.funktionale.partials.partially1
import org.jooq.DSLContext
import org.jooq.exception.SQLDialectNotSupportedException
import org.jooq.impl.DSL
import org.jooq.impl.DSL.count
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.select
import org.jooq.impl.DSL.sql
import org.jooq.impl.DSL.table
import org.jooq.impl.DSL.update
import org.jooq.util.mysql.MySQLDSL
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.sql.ResultSet
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.TemporalAmount
import java.util.Optional
import java.util.concurrent.TimeUnit
import kotlin.Exception
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random.Default.nextLong

@KotlinOpen
class SqlQueue(
  queueName: String,
  schemaVersion: Int,
  private val jooq: DSLContext,
  private val clock: Clock,
  private val lockTtlSeconds: Int,
  private val mapper: ObjectMapper,
  private val serializationMigrator: Optional<SerializationMigrator>,
  override val ackTimeout: TemporalAmount = Duration.ofMinutes(1),
  override val deadMessageHandlers: List<DeadMessageCallback>,
  override val canPollMany: Boolean = true,
  override val publisher: EventPublisher,
  private val sqlRetryProperties: SqlRetryProperties,
  private val ULID: ULID = ULID(),
  private val cleanupAfter: TemporalAmount = Duration.ofMinutes(5)
) : MonitorableQueue {

  companion object {
    @Suppress("UnstableApiUsage")
    /**
     * [lockId] is a hash of the hostname, used to claim messages on [queueTable] without
     * performing a locking read.
     */
    private val lockId = Hashing
      .murmur3_128()
      .hashString(InetAddress.getLocalHost().hostName, StandardCharsets.UTF_8)
      .toString()

    private val hashObjectMapper = ObjectMapper().copy().apply {
      enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
    }

    private val lockedAtRegex = """^\w+:(\d+)$""".toRegex()
    private val nameSanitization = """[^A-Za-z0-9_]""".toRegex()

    private val log = LoggerFactory.getLogger(SqlQueue::class.java)
  }

  private val sanitizedName = queueName.replace(nameSanitization, "_")
  private val queueBase = "keiko_v${schemaVersion}_queue"
  private val unackedBase = "keiko_v${schemaVersion}_unacked"
  private val messagesBase = "keiko_v${schemaVersion}_messages"

  private val queueTableName = "${queueBase}_$sanitizedName"
  private val unackedTableName = "${unackedBase}_$sanitizedName"
  private val messagesTableName = "${messagesBase}_$sanitizedName"

  private val queueTable = table(queueTableName)
  private val unackedTable = table(unackedTableName)
  private val messagesTable = table(messagesTableName)

  private val bodyField = field("body")
  private val deliveryField = field("delivery")
  private val expiryField = field("expiry")
  private val fingerprintField = field("fingerprint")
  private val idField = field("id")
  private val lockedField = field("locked")

  private val lockTtlDuration = Duration.ofSeconds(lockTtlSeconds.toLong())

  private val writeRetryBackoffMin = max(sqlRetryProperties.transactions.backoffMs - 25, 25)
  private val writeRetryBackoffMax = max(sqlRetryProperties.transactions.backoffMs + 50, 100)

  init {
    log.info("Configured $javaClass queue: $queueName")
    initTables()
  }

  override fun readState(): QueueState {
    /**
     * Reading counts across all tables in a single query for consistency
     */
    val rs = jooq.select()
      .select(
        select(count())
          .from(queueTable)
          .asField<Int>("depth"),
        select(count())
          .from(queueTable)
          .where(deliveryField.le(clock.instant().toEpochMilli()))
          .asField<Int>("ready"),
        select(count())
          .from(unackedTable)
          .asField<Int>("unacked"),
        select(count())
          .from(messagesTable)
          .asField<Int>("messages")
      )
      .fetch()
      .intoResultSet()

    rs.next()

    val depth = rs.getInt("depth")
    val ready = rs.getInt("ready")
    val unacked = rs.getInt("unacked")
    val messages = rs.getInt("messages")

    return QueueState(
      depth = depth,
      ready = ready,
      unacked = unacked,
      orphaned = messages - (depth + unacked)
    )
  }

  override fun containsMessage(predicate: (Message) -> Boolean): Boolean {
    val batchSize = 100
    var found = false
    var lastId = "0"

    do {
      val rs: ResultSet = withRetry(RetryCategory.READ) {
        jooq.select(idField, fingerprintField, bodyField)
          .from(messagesTable)
          .where(idField.gt(lastId))
          .limit(batchSize)
          .fetch()
          .intoResultSet()
      }

      while (!found && rs.next()) {
        try {
          found = predicate.invoke(mapper.readValue(rs.getString("body")))
        } catch (e: Exception) {
          log.error("Failed reading message with fingerprint: ${rs.getString("fingerprint")} " +
            "message: ${rs.getString("body")}", e)
        }
        lastId = rs.getString("id")
      }
    } while (!found && rs.row == batchSize)

    return found
  }

  override fun poll(callback: (Message, () -> Unit) -> Unit) {
    poll(1, callback)
  }

  /**
   * TODO: Emit metrics: histogram of poll runtime, count of messages grabbed per poll, count of passes
   */
  override fun poll(maxMessages: Int, callback: (Message, () -> Unit) -> Unit) {
    val now = clock.instant().toEpochMilli()
    var changed = 0

    /**
     * Selects the primary key ulid's of up to ([maxMessages] * 3) ready and unlocked messages,
     * sorted by delivery time.
     *
     * To minimize lock contention, this is a non-locking read. The id's returned may be
     * locked or removed by another instance before we can acquire them. We read more id's
     * than [maxMessages] and shuffle them to decrease the likelihood that multiple instances
     * polling concurrently are all competing for the oldest ready messages when many more
     * than [maxMessages] are read.
     *
     * Candidate rows are locked via an autocommit update query by primary key that will
     * only modify unlocked rows. When (candidates > maxMessages), a sliding window is used
     * to traverse the shuffled candidates, sized to (maxMessages - changed) with up-to 3
     * attempts (and update queries) to grab [maxMessages].
     *
     * I.e. if maxMessage == 5 and
     * candidates == [1, 2, 3, 4, 5, 6, 7, 8, 9, 10].shuffle() == [9, 3, 7, 1, 10, 8, 5, 2, 6, 4]
     *
     * - pass1: attempts to claim [9, 3, 7, 1, 10], locks 3 messages
     * - pass2: attempts to claim [8, 5], locks 1 message
     * - pass3: attempt to claim [2], succeeds but if not, there are no further attempts
     * - proceeds to process 5 messages locked via 3 update queries.
     *
     * This makes a trade-off between grabbing the maximum number of ready messages per poll cycle
     * vs. minimizing [poll] runtime which is also critical to throughput. In testing a scenario
     * with up-to 100k ready messages and 7 orca/keiko-sql instances with [fillExecutorEachCycle]
     * enabled and 20 handler threads, instances were able to successfully grab [maxMessages] on
     * each poll cycle > 94% of the time and no poll cycles came up empty handed.
     *
     * Note: this differs from other [Queue.poll] implementations in that the message
     * body is only saved back to the db in order to increment [AttemptsAttribute] if
     * [MaxAttemptsAttribute] has been set to a positive integer. Otherwise,
     * [AttemptsAttribute] is unused.
     */
    val candidates = jooq.select(idField)
      .from(queueTable)
      .where(deliveryField.le(now), lockedField.eq("0"))
      .orderBy(deliveryField.asc())
      .limit(max(10, maxMessages * 3))
      .fetchInto(String::class.java)

    if (candidates == null || candidates.isEmpty()) {
      fire(QueuePolled)
      return
    }

    candidates.shuffle()

    var position = 0
    var passes = 0
    while (changed < maxMessages && position < candidates.size && passes < 3) {
      passes++
      val sliceNext = min(maxMessages - 1 - changed, candidates.size - 1 - position)
      val ids = candidates.slice(IntRange(position, position + sliceNext))
      when (sliceNext) {
        0 -> position++
        else -> position += sliceNext
      }

      changed += jooq.update(queueTable)
        .set(lockedField, "$lockId:$now")
        .where(idField.`in`(*ids.toTypedArray()), lockedField.eq("0"))
        .execute()
    }

    if (changed > 0) {
      val rs = withRetry(RetryCategory.READ) {
        jooq.select(field("q.id").`as`("id"),
          field("q.fingerprint").`as`("fingerprint"),
          field("q.delivery").`as`("delivery"),
          field("m.body").`as`("body"))
          .from(queueTable.`as`("q"))
          .leftOuterJoin(messagesTable.`as`("m"))
          .on(sql("q.fingerprint = m.fingerprint"))
          .where(field("q.locked").like("$lockId%"))
          .fetch()
          .intoResultSet()
      }

      val lockedMessages = mutableListOf<LockedMessage>()
      var ulid = ULID.nextValue()

      while (rs.next()) {
        val fingerprint = rs.getString("fingerprint")
        try {
          val message = mapper.readValue<Message>(runSerializationMigration(rs.getString("body")))
            .apply {
              val currentAttempts = (getAttribute() ?: AttemptsAttribute())
                .run { copy(attempts = attempts + 1) }

              setAttribute(currentAttempts)
            }

          val timeoutOverride = message.ackTimeoutMs ?: 0

          lockedMessages.add(
            LockedMessage(
              queueId = rs.getString("id"),
              fingerprint = fingerprint,
              scheduledTime = Instant.ofEpochMilli(rs.getLong("delivery")),
              message = message,
              expiry = if (timeoutOverride > 0) {
                atTime(Duration.ofMillis(timeoutOverride))
              } else {
                atTime(ackTimeout)
              },
              maxAttempts = message.getAttribute<MaxAttemptsAttribute>()?.maxAttempts ?: 0,
              ackCallback = this::ackMessage.partially1(fingerprint)
            )
          )
        } catch (e: Exception) {
          log.error("Failed reading message for fingerprint: $fingerprint, " +
            "json: ${rs.getString("body")}, removing", e)
          deleteAll(fingerprint)
        }
      }

      val ids = lockedMessages
        .map { it.queueId }
        .toList()

      val maxAttemptsUpdates = lockedMessages
        .filter { it.maxAttempts > 0 }
        .map {
          update(messagesTable)
            .set(bodyField, mapper.writeValueAsString(it.message))
            .where(fingerprintField.eq(it.fingerprint))
        }
        .toList()

      withRetry(RetryCategory.WRITE) {
        jooq.transaction { config ->
          val txn = DSL.using(config)

          txn.insertInto(
            unackedTable,
            idField,
            fingerprintField,
            expiryField)
            .apply {
              lockedMessages.forEach {
                values(ulid.toString(), it.fingerprint, it.expiry)
                ulid = ULID.nextMonotonicValue(ulid)
              }
            }
            .onDuplicateKeyUpdate()
            .set(expiryField, MySQLDSL.values(expiryField) as Any)
            .execute()

          if (maxAttemptsUpdates.isNotEmpty()) {
            txn.batch(maxAttemptsUpdates).execute()
          }
        }
      }

      /**
       * Deleting from queue table outside of the above txn to minimize potential for deadlocks.
       * If an instance crashes after committing the above txn but before the following delete,
       * [retry] will release the locks after [lockTtlSeconds] and another instance will grab them.
       */
      ids.sorted().chunked(4).forEach { chunk ->
        withRetry(RetryCategory.WRITE) {
          jooq.deleteFrom(queueTable)
            .where(idField.`in`(*chunk.toTypedArray()))
            .execute()
        }
      }

      lockedMessages.forEach {
        fire(MessageProcessing(it.message, it.scheduledTime, clock.instant()))
        callback(it.message, it.ackCallback)
      }
    }

    fire(QueuePolled)
  }

  override fun push(message: Message, delay: TemporalAmount) {
    val fingerprint = message.hashV2()
    val ulid = ULID.nextValue()
    val deliveryTime = atTime(delay)

    message.setAttribute(
      message.getAttribute() ?: AttemptsAttribute()
    )

    withRetry(RetryCategory.WRITE) {
      jooq.transaction { config ->
        val txn = DSL.using(config)

        txn.insertInto(messagesTable)
          .set(idField, ulid.toString())
          .set(fingerprintField, fingerprint)
          .set(bodyField, mapper.writeValueAsString(message))
          .onDuplicateKeyUpdate()
          .set(bodyField, MySQLDSL.values(bodyField) as Any)
          .execute()

        txn.insertInto(queueTable)
          .set(idField, ULID.nextMonotonicValue(ulid).toString())
          .set(fingerprintField, fingerprint)
          .set(deliveryField, deliveryTime)
          .set(lockedField, "0")
          .onDuplicateKeyUpdate()
          .set(deliveryField, MySQLDSL.values(deliveryField) as Any)
          .execute()
      }
    }

    fire(MessagePushed(message))
  }

  override fun reschedule(message: Message, delay: TemporalAmount) {
    val fingerprint = message.hashV2()

    withRetry(RetryCategory.WRITE) {
      val rows = jooq.update(queueTable)
        .set(deliveryField, atTime(delay))
        .where(fingerprintField.eq(fingerprint))
        .execute()

      if (rows == 1) {
        log.debug("Rescheduled message: $message, fingerprint: $fingerprint to deliver in $delay")
        fire(MessageRescheduled(message))
      } else {
        log.warn("Failed to reschedule message: $message, fingerprint: $fingerprint, not found " +
          "on queue")
        fire(MessageNotFound(message))
      }
    }
  }

  override fun ensure(message: Message, delay: TemporalAmount) {
    val fingerprint = message.hashV2()
    var missing = false

    withRetry(RetryCategory.WRITE) {
      jooq.transaction { config ->
        val txn = DSL.using(config)

        val queueRows = txn.select(fingerprintField)
          .from(queueTable)
          .where(fingerprintField.eq(fingerprint))
          .limit(1)
          .execute()

        if (queueRows == 0) {
          val unackedRows = txn.select(fingerprintField)
            .from(unackedTable)
            .where(fingerprintField.eq(fingerprint))
            .limit(1)
            .execute()

          if (unackedRows == 0) {
            missing = true
          }
        }
      }
    }

    if (missing) {
      log.debug(
        "Pushing ensured message onto queue as it does not exist in queue or unacked tables"
      )
      push(message, delay)
    }
  }

  private fun expireStaleLocks() {
    val now = clock.instant().toEpochMilli()
    val minMs = now.minus(TimeUnit.SECONDS.toMillis(lockTtlSeconds.toLong()))
    val minUlid = ULID.nextValue(minMs).toString()

    val rs = withRetry(RetryCategory.READ) {
      jooq.select(idField, fingerprintField, deliveryField, lockedField)
        .from(queueTable)
        .where(
          idField.lt(minUlid),
          lockedField.ne("0")
        )
        .fetch()
        .intoResultSet()
    }

    var ulid = ULID.nextValue()

    while (rs.next()) {
      val id = rs.getString("id")
      val lock = rs.getString("locked")
      val fingerprint = rs.getString("fingerprint")
      val lockMatch = lockedAtRegex.find(lock)

      if (lockMatch != null && lockMatch.groupValues.size > 1) {
        if (lockMatch.groupValues[1].toLong() > minMs) {
          /* Not time yet */
          continue
        }
      } else {
        log.error("Failed parsing lockedAt time for message id: $id, " +
          "fingerprint: $fingerprint, lock: $lock, releasing")
      }

      /**
       * No retries inside this transaction, transient failures here will be cleared up at
       * the next retry() interval.
       */
      jooq.transaction { config ->
        val txn = DSL.using(config)

        val deleted = txn.delete(queueTable)
          .where(idField.eq(id), lockedField.eq(lock))
          .execute()

        if (deleted == 1) {
          log.info("releasing stale lock for fingerprint: $fingerprint")
          ulid = ULID.nextMonotonicValue(ulid)

          /**
           * Re-insert with a fresh ulid and for immediate delivery
           */
          txn.insertInto(queueTable)
            .set(idField, ulid.toString())
            .set(fingerprintField, fingerprint)
            .set(deliveryField, now)
            .set(lockedField, "0")
            .execute()
        }
      }
    }
  }

  /**
   * Differs from other [Queue.retry] implementations, as unacked messages are requeued for
   * delivery at now + [lockTtlSeconds] instead of immediately.
   */
  @Scheduled(fixedDelayString = "\${queue.retry.frequency.ms:10000}")
  override fun retry() {
    expireStaleLocks()

    val unackBaseTime = clock.instant().toEpochMilli()

    val rs = jooq.select(field("u.id").`as`("id"),
      field("u.expiry").`as`("expiry"),
      field("u.fingerprint").`as`("fingerprint"),
      field("m.body").`as`("body"))
      .from(unackedTable.`as`("u"))
      .leftOuterJoin(messagesTable.`as`("m"))
      .on(sql("u.fingerprint = m.fingerprint"))
      .where(field("u.expiry").le(unackBaseTime))
      .fetch()
      .intoResultSet()

    while (rs.next()) {
      val fingerprint = rs.getString("fingerprint")
      var rows = 0
      var dlq = false
      var message: Message
      var acks: Int

      try {
        message = mapper.readValue(runSerializationMigration(rs.getString("body")))

        val ackAttemptsAttribute = (message.getAttribute() ?: AckAttemptsAttribute())
          .run { copy(ackAttempts = ackAttempts + 1) }

        message.setAttribute(ackAttemptsAttribute)
        acks = ackAttemptsAttribute.ackAttempts

        val attempts = message.getAttribute<AttemptsAttribute>()?.attempts
          ?: 0
        val maxAttempts = message.getAttribute<MaxAttemptsAttribute>()?.maxAttempts
          ?: 0

        if (ackAttemptsAttribute.ackAttempts >= Queue.maxRetries ||
          (maxAttempts > 0 && attempts > maxAttempts)) {
          log.warn("Message $fingerprint with payload $message exceeded max ack retries")
          dlq = true
        }
      } catch (e: Exception) {
        log.error("Failed to deserialize message $fingerprint, cleaning up", e)
        deleteAll(fingerprint)
        continue
      }

      if (dlq) {
        deleteAll(fingerprint)
        handleDeadMessage(message)
        continue
      }

      jooq.transaction { config ->
        val txn = DSL.using(config)

        rows = txn.delete(unackedTable)
          .where(idField.eq(rs.getString("id")))
          .execute()

        if (rows == 1) {
          log.warn("Retrying message $fingerprint after $acks ack attempts")

          txn.insertInto(queueTable)
            .set(idField, ULID.nextValue().toString())
            .set(fingerprintField, fingerprint)
            .set(deliveryField, atTime(lockTtlDuration))
            .set(lockedField, "0")
            .onDuplicateKeyUpdate()
            .set(deliveryField, MySQLDSL.values(deliveryField) as Any)
            .execute()
        }
      }

      /**
       * Updating message with increment ackAttempt value outside of the above txn to minimize
       * lock times related to the [queueTable] insert. If this does not complete within
       * [lockTtlSeconds], the ackAttempt attribute increment may be lost, making it best effort.
       */
      if (rows == 1) {
        jooq.update(messagesTable)
          .set(bodyField, mapper.writeValueAsString(message))
          .where(fingerprintField.eq(fingerprint))
          .execute()

        fire(MessageRetried)
      }
    }
    fire(RetryPolled)
  }

  // TODO: ideally, this would only run on one instance at a time with a distributed lock
  @Scheduled(fixedDelayString = "\${queue.cleanup.frequency.ms:300000}")
  fun cleanupMessages() {
    val minTime = clock.instant().minus(cleanupAfter)
    val minUlid = ULID.nextValue(minTime.toEpochMilli()).toString()

    val rs = withRetry(RetryCategory.READ) {
      jooq.select(
        field("m.id").`as`("mid"),
        field("q.id").`as`("qid"),
        field("u.id").`as`("uid")
      )
        .from(messagesTable.`as`("m"))
        .leftOuterJoin(queueTable.`as`("q"))
        .on(sql("m.fingerprint = q.fingerprint"))
        .leftOuterJoin(unackedTable.`as`("u"))
        .on(sql("m.fingerprint = u.fingerprint"))
        .where(field("m.id").lt(minUlid))
        .fetch()
        .intoResultSet()
    }

    val toDelete = mutableListOf<String>()
    var candidates = 0

    while (rs.next()) {
      val queueId: String? = rs.getString("qid")
      val unackedId: String? = rs.getString("uid")

      if (queueId == null && unackedId == null) {
        toDelete.add(rs.getString("mid"))
      }

      candidates++
    }

    var deleted = 0

    toDelete.sorted().chunked(10).forEach { chunk ->
      withRetry(RetryCategory.WRITE) {
        deleted += jooq.deleteFrom(messagesTable)
          .where(idField.`in`(*chunk.toTypedArray()))
          .execute()
      }
    }

    if (deleted > 0) {
      log.debug("Cleaned up $deleted completed messages ($toDelete candidates / " +
        "$candidates messages older than $minTime)")
    }
  }

  private fun ackMessage(fingerprint: String) {
    withRetry(RetryCategory.WRITE) {
      jooq.deleteFrom(unackedTable)
        .where(fingerprintField.eq(fingerprint))
        .execute()
    }

    fire(MessageAcknowledged)
  }

  private fun deleteAll(fingerprint: String) {
    withRetry(RetryCategory.WRITE) {
      jooq.deleteFrom(queueTable)
        .where(fingerprintField.eq(fingerprint))
        .execute()

      jooq.deleteFrom(unackedTable)
        .where(fingerprintField.eq(fingerprint))
        .execute()

      jooq.deleteFrom(messagesTable)
        .where(fingerprintField.eq(fingerprint))
        .execute()
    }
  }

  private fun initTables() {
    withRetry(RetryCategory.WRITE) {
      jooq.execute("CREATE TABLE IF NOT EXISTS $queueTableName LIKE ${queueBase}_template")
      jooq.execute("CREATE TABLE IF NOT EXISTS $unackedTableName LIKE ${unackedBase}_template")
      jooq.execute("CREATE TABLE IF NOT EXISTS $messagesTableName LIKE ${messagesBase}_template")
    }
  }

  private fun handleDeadMessage(message: Message) {
    deadMessageHandlers.forEach {
      it.invoke(this, message)
    }

    fire(MessageDead)
  }

  private fun runSerializationMigration(json: String): String {
    if (serializationMigrator.isPresent) {
      return serializationMigrator.get().migrate(json)
    }
    return json
  }

  private fun atTime(delay: TemporalAmount = Duration.ZERO) =
    clock.instant().plus(delay).toEpochMilli()

  @Suppress("UnstableApiUsage")
  fun Message.hashV2() =
    hashObjectMapper.convertValue(this, MutableMap::class.java)
      .apply { remove("attributes") }
      .let {
        Hashing
          .murmur3_128()
          .hashString("v2:${hashObjectMapper.writeValueAsString(it)}", StandardCharsets.UTF_8)
          .toString()
      }

  private enum class RetryCategory {
    WRITE, READ
  }

  private fun <T> withRetry(category: RetryCategory, action: () -> T): T {
    return if (category == RetryCategory.WRITE) {
      val retry = Retry.of(
        "sqlWrite",
        RetryConfig.custom<T>()
          .maxAttempts(sqlRetryProperties.transactions.maxRetries)
          .waitDuration(
            Duration.ofMillis(
              nextLong(writeRetryBackoffMin, writeRetryBackoffMax)))
          .ignoreExceptions(SQLDialectNotSupportedException::class.java)
          .build()
      )

      Try.ofSupplier(Retry.decorateSupplier(retry, action)).get()
    } else {
      val retry = Retry.of(
        "sqlRead",
        RetryConfig.custom<T>()
          .maxAttempts(sqlRetryProperties.reads.maxRetries)
          .waitDuration(Duration.ofMillis(sqlRetryProperties.reads.backoffMs))
          .ignoreExceptions(SQLDialectNotSupportedException::class.java)
          .build()
      )

      Try.ofSupplier(Retry.decorateSupplier(retry, action)).get()
    }
  }

  private data class LockedMessage(
    val queueId: String,
    val fingerprint: String,
    val scheduledTime: Instant,
    val message: Message,
    val expiry: Long,
    val maxAttempts: Int,
    val ackCallback: () -> Unit
  )
}
