package com.netflix.spinnaker.orca.peering

import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.sql.routing.withPool
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory

/**
 * Provides raw access to various tables in the orca SQL
 */
class SqlDbRawAccess(
  private val jooq: DSLContext,
  private val poolName: String
) {

  private val log = LoggerFactory.getLogger(this.javaClass)
  private val _completedStatuses = ExecutionStatus.COMPLETED.map { it.toString() }
  private var maxPacketSize: Long = 0

  fun init() {
    if (maxPacketSize == 0L) {
      if (jooq.dialect() != SQLDialect.MYSQL) {
        throw UnsupportedOperationException("Peering only supported on MySQL right now")
      }

      maxPacketSize = jooq
        .resultQuery("SHOW VARIABLES WHERE Variable_name='max_allowed_packet'")
        .fetchOne(DSL.field("Value"), Long::class.java) / 2
    }
  }
  /**
   *  Returns a list of execution IDs for completed executions
   */
  fun getCompletedExecutionIds(executionType: Execution.ExecutionType, partitionName: String, builtAfter: Long): List<String> {
    return withPool(poolName) {
      jooq
        .select(DSL.field("id"))
        .from(getTableName(executionType))
        .where(DSL.field("status").`in`(*_completedStatuses.toTypedArray())
          .and(DSL.field("build_time").ge(builtAfter))
          .and(DSL.field("`partition`").eq(partitionName).or(DSL.field("`partition`").isNull)))
        .fetch(DSL.field("id"), String::class.java)
    }
  }

  /**
   *  Returns a list of execution IDs for completed executions
   */
  fun getRunningExecutionIds(executionType: Execution.ExecutionType, partitionName: String, builtAfter: Long): List<String> {
    return withPool(poolName) {
      jooq
        .select(DSL.field("id"))
        .from(getTableName(executionType))
        .where(DSL.field("status").notIn(*_completedStatuses.toTypedArray())
          .and(DSL.field("build_time").ge(builtAfter))
          .and(DSL.field("`partition`").eq(partitionName).or(DSL.field("`partition`").isNull)))
        .fetch(DSL.field("id"), String::class.java)
    }
  }

//  /**
//   * Returns a list of all execution IDs in the DB
//   */
//  fun getAllExecutionIds(executionType: Execution.ExecutionType, partitionName: String, updatedAfter: Long): List<ExecutionDiffKey> {
//    return withPool(poolName) {
//      jooq
//        .select(DSL.field("id"), DSL.field("updated_at"), DSL.field("status"))
//        .from(getTableName(executionType))
//        .where(DSL.field("partition").eq(partitionName)
//          .and(DSL.field("updated_at").gt(updatedAfter))
//        )
//        .fetchInto(ExecutionDiffKey::class.java)
//    }
//  }

  /**
   * Returns a list of stage IDs that belong to the given executions
   */
  fun getStageIdsForExecutions(executionType: Execution.ExecutionType, executionIds: List<String>): List<String> {
    return withPool(poolName) {
      jooq
        .select(DSL.field("id"))
        .from(getStagesTableName(executionType))
        .where(DSL.field("execution_id").`in`(*executionIds.toTypedArray()))
        .fetch(DSL.field("id"), String::class.java)
    }
  }

  /**
   * Returns (a list of) full execution DB records with given execution IDs
   */
  fun getExecutions(executionType: Execution.ExecutionType, ids: List<String>): org.jooq.Result<Record> {
    return withPool(poolName) {
      jooq.select(DSL.asterisk())
        .from(getTableName(executionType))
        .where(DSL.field("id").`in`(*ids.toTypedArray()))
        .fetch()
    }
  }

  /**
   * Returns (a list of) full stage DB records with given stage IDs
   */
  fun getStages(executionType: Execution.ExecutionType, stageIds: List<String>): org.jooq.Result<Record> {
    return withPool(poolName) {
      jooq.select(DSL.asterisk())
        .from(getStagesTableName(executionType))
        .where(DSL.field("id").`in`(*stageIds.toTypedArray()))
        .fetch()
    }
  }

  /* TODO(mvulfson): Deal with OCA later
  /**
   * Get combined id (full concatenated primary key) of OCA cache statuses that belong to executions that have been completed
   */
  fun getCompletedOcaCacheStatusPrimaryIds(): List<String> {
    return withPool(poolName) {
      jooq
        .select(concat(DSL.field("execution_id"), DSL.`val`("-"), DSL.field("cache_id")).`as`("combinedid"))
        .from(getOcaStatusTableName())
        .join(getTableName(Execution.ExecutionType.PIPELINE).`as`("p"))
        .on("execution_id = p.id")
        .where(DSL.field("p.status").`in`(*_completedStatuses.toTypedArray()))
        .fetch(DSL.field("combinedid"), String::class.java)
    }
  }

  /**
   * Get combined id (full concatenated primary key) of all OCA cache statuses
   */
  fun getAllOcaCacheStatusPrimaryIds(): List<String> {
    return withPool(poolName) {
      jooq
        .select(concat(DSL.field("execution_id"), DSL.`val`("-"), DSL.field("cache_id")).`as`("combinedid"))
        .from(getOcaStatusTableName())
        .fetch(DSL.field("combinedid"), String::class.java)
    }
  }

  /**
   * Get (a list of) full oca cache status records that have the specified primary key
   */
  fun getOcaCacheStatusesForIds(ids: List<String>): org.jooq.Result<Record> {
    return withPool(poolName) {
      jooq
        .select(DSL.asterisk())
        .from(getOcaStatusTableName())
        .where(concat(DSL.field("execution_id"), DSL.`val`("-"), DSL.field("cache_id")).`in`(*ids.toTypedArray()))
        .fetch()
    }
  }

  /**
   * Get (a list of) full oca cache uuid records that have the specified primary key
   */
  fun getOcaCacheUuidIdsForPrimaryKeys(ids: List<String>): org.jooq.Result<Record> {
    return withPool(poolName) {
      jooq
        .select(DSL.asterisk())
        .from(getOcaCacheUuidTableName())
        .where(concat(DSL.field("env"), DSL.`val`("-"), DSL.field("cache_id")).`in`(*ids.toTypedArray()))
        .fetch()
    }
  }

  /**
   * Get a list of all oca cache uuid primary keys
   */
  fun getAllOcaCacheUuidsPrimaryIds(): List<String> {
    return withPool(poolName) {
      jooq
        .select(concat(DSL.field("env"), DSL.`val`("-"), DSL.field("cache_id")).`as`("combinedid"))
        .from(getOcaCacheUuidTableName())
        .fetch(DSL.field("combinedid"), String::class.java)
    }
  }
  */

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
        it to DSL.field("VALUES({0})", it.dataType, it)
      }.toMap()

      var cumulativeSize = 0
      var batchQuery = jooq
        .insertInto(DSL.table(tableName))
        .columns(allFields)

      records.forEach { it ->
        val values = it.intoList()
        val totalRecordSize = values.sumBy { value -> value?.toString()?.length ?: 0 }

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
            .insertInto(DSL.table(tableName))
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
}
