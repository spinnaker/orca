package com.netflix.spinnaker.orca.peering

import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.sql.routing.withPool
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory

/**
 * Provides raw access to various tables in the orca SQL
 */
class SqlDbRawAccess(
  private val jooq: DSLContext,
  private val poolName: String
) {

  private val log = LoggerFactory.getLogger(this.javaClass)
  private val completedStatuses = ExecutionStatus.COMPLETED.map { it.toString() }
  private var maxPacketSize: Long = 0

  init {
    if (jooq.dialect() != SQLDialect.MYSQL) {
      throw UnsupportedOperationException("Peering only supported on MySQL right now")
    }

    maxPacketSize = withPool(poolName) {
      jooq
        .resultQuery("SHOW VARIABLES WHERE Variable_name='max_allowed_packet'")
        .fetchOne(field("Value"), Long::class.java)
    }

    log.info("Initialized SqlDbRawAccess with pool=$poolName and maxPacketSize=$maxPacketSize")

    // Hack: we don't count the full SQL statement length, so subtract a reasonable buffer for the boiler plate statement
    maxPacketSize -= 8192
  }

  /**
   *  Returns a list of execution IDs and their update_at times for completed executions
   */
  fun getCompletedExecutionIds(executionType: Execution.ExecutionType, partitionName: String, updatedAfter: Long): List<ExecutionDiffKey> {
    return withPool(poolName) {
      jooq
        .select(field("id"), field("updated_at"))
        .from(getExecutionTable(executionType))
        .where(field("status").`in`(*completedStatuses.toTypedArray())
          .and(field("updated_at").gt(updatedAfter))
          .and(field("`partition`").eq(partitionName).or(field("`partition`").isNull)))
        .fetchInto(ExecutionDiffKey::class.java)
    }
  }

  /**
   *  Returns a list of execution IDs for active (not completed) executions
   */
  fun getActiveExecutionIds(executionType: Execution.ExecutionType, partitionName: String): List<String> {
    return withPool(poolName) {
      jooq
        .select(field("id"))
        .from(getExecutionTable(executionType))
        .where(field("status").notIn(*completedStatuses.toTypedArray())
          .and(field("`partition`").eq(partitionName).or(field("`partition`").isNull)))
        .fetch(field("id"), String::class.java)
    }
  }

  /**
   * Returns a list of stage IDs that belong to the given executions
   */
  fun getStageIdsForExecutions(executionType: Execution.ExecutionType, executionIds: List<String>): List<String> {
    return withPool(poolName) {
      jooq
        .select(field("id"))
        .from(getStagesTable(executionType))
        .where(field("execution_id").`in`(*executionIds.toTypedArray()))
        .fetch(field("id"), String::class.java)
    }
  }

  /**
   * Returns (a list of) full execution DB records with given execution IDs
   */
  fun getExecutions(executionType: Execution.ExecutionType, ids: List<String>): org.jooq.Result<Record> {
    return withPool(poolName) {
      jooq.select(DSL.asterisk())
        .from(getExecutionTable(executionType))
        .where(field("id").`in`(*ids.toTypedArray()))
        .fetch()
    }
  }

  /**
   * Returns (a list of) full stage DB records with given stage IDs
   */
  fun getStages(executionType: Execution.ExecutionType, stageIds: List<String>): org.jooq.Result<Record> {
    return withPool(poolName) {
      jooq.select(DSL.asterisk())
        .from(getStagesTable(executionType))
        .where(field("id").`in`(*stageIds.toTypedArray()))
        .fetch()
    }
  }

  // TODO(mvulfson): Need an extension point to deal with cases such as OCA

  /**
   * Load given records into the specified table using jooq loader api
   */
  fun loadRecords(tableName: String, records: org.jooq.Result<Record>): Int {
    if (records.isEmpty()) {
      return 0
    }

    val allFields = records[0].fields().toList()
    var persisted = 0

    withPool(poolName) {
      val updateSet = allFields.map {
        it to field("VALUES({0})", it.dataType, it)
      }.toMap()

      var cumulativeSize = 0
      var batchQuery = jooq
        .insertInto(table(tableName))
        .columns(allFields)

      records.forEach { it ->
        val values = it.intoList()
        val totalRecordSize = (3 * values.size) + values.sumBy { value -> (value?.toString()?.length ?: 4) }

        if (cumulativeSize + totalRecordSize > maxPacketSize) {
          if (cumulativeSize == 0) {
            throw SystemException("Can't persist a single row for table $tableName due to maxPacketSize restriction. Row size = $totalRecordSize")
          }

          // Dump it to the DB
          batchQuery
            .onDuplicateKeyUpdate()
            .set(updateSet)
            .execute()

          batchQuery = jooq
            .insertInto(table(tableName))
            .columns(allFields)
            .values(values)
          cumulativeSize = 0
        } else {
          batchQuery = batchQuery
            .values(values)
        }

        persisted++
        cumulativeSize += totalRecordSize
      }

      if (cumulativeSize > 0) {
        // Dump the last bit to the DB
        batchQuery
          .onDuplicateKeyUpdate()
          .set(updateSet)
          .execute()
      }
    }

    return persisted
  }

  fun deleteStages(executionType: Execution.ExecutionType, stageIdsToDelete: List<String>) {
    withPool(poolName) {
      for (chunk in stageIdsToDelete.chunked(100)) {
        jooq
          .deleteFrom(getStagesTable(executionType))
          .where(field("id").`in`(*chunk.toTypedArray()))
          .execute()
      }
    }
  }

  data class ExecutionDiffKey(
    val id: String,
    val updated_at: Long
  )
}
