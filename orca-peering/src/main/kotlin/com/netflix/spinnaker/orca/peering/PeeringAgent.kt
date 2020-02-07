/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.orca.peering

import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.model.Execution
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.min

class PeeringAgent(
  jooq: DSLContext,
  peeredPoolName: String,
  private val peeredId: String,
  private val pollingIntervalMs: Long,
  clusterLock: NotificationClusterLock
) : AbstractPollingNotificationAgent(clusterLock) {

  private val log = LoggerFactory.getLogger(javaClass)
  private val srcDB: SqlDbRawAccess = SqlDbRawAccess(jooq, peeredPoolName)
  private val destDB: SqlDbRawAccess = SqlDbRawAccess(jooq, "default")
  private val mostRecentUpdatedTime: Long = 0

  // Number of SQL rows to copy at a time
  private val chunkSize = 100

  // Number of threads to use for copy purposes
  private val threadCount = 1

  override fun tick() {
    log.info("Hello, is it me you're peering for?")

    srcDB.init()
    destDB.init()

    copyCompletedExecutions(Execution.ExecutionType.PIPELINE)
    copyCompletedExecutions(Execution.ExecutionType.ORCHESTRATION)

    copyRunningExecutions(Execution.ExecutionType.PIPELINE)
    copyRunningExecutions(Execution.ExecutionType.ORCHESTRATION)
  }

  /**
   * Migrate running/active executions of given type
   */
  private fun copyRunningExecutions(executionType: Execution.ExecutionType) {
    log.info("Starting $executionType migration")
    val runningPipelineIds = srcDB.getRunningExecutionIds(executionType, peeredId, 0)

    if (runningPipelineIds.isNotEmpty()) {
      log.info("Found ${runningPipelineIds.size} running $executionType, copying all")
      val migratedCount = migrateInParallel(runningPipelineIds) { chunk -> migrateExecutionChunk(executionType, chunk) }
      log.info("Completed running $executionType peering: copied $migratedCount of ${runningPipelineIds.size}")
    } else {
      log.info("No running $executionType executions to copy for peering")
    }
  }

  /**
   * Migrate completed executions of given type
   */
  private fun copyCompletedExecutions(executionType: Execution.ExecutionType) {
    log.info("Starting $executionType migration")
    val completedPipelineIds = srcDB.getCompletedExecutionIds(executionType, peeredId, 0)
    val migratedPipelineIds = destDB.getCompletedExecutionIds(executionType, peeredId, 0).toHashSet()
    val pipelineIdsToMigrate = completedPipelineIds.filter { id -> !migratedPipelineIds.contains(id) }

    if (pipelineIdsToMigrate.isNotEmpty()) {
      log.info("Found ${completedPipelineIds.size} $executionType candidates with ${migratedPipelineIds.size} already copied for peering, ${pipelineIdsToMigrate.size} still need copying")
      val migratedCount = migrateInParallel(pipelineIdsToMigrate) { chunk -> migrateExecutionChunk(executionType, chunk) }
      log.info("Completed $executionType peering: copied $migratedCount of ${pipelineIdsToMigrate.size}")
    } else {
      log.info("No $executionType executions to copy for peering")
    }
  }

  /**
   * Migrates executions (orchestrations or pipelines) and its stages given IDs of the executions
   */
  private fun migrateExecutionChunk(executionType: Execution.ExecutionType, idsToMigrate: List<String>): Int {
    try {
      val stagesToMigrate = srcDB.getStageIdsForExecutions(executionType, idsToMigrate)

      // Copy all stages
      for (chunk in stagesToMigrate.chunked(chunkSize)) {
        val rows = srcDB.getStages(executionType, chunk)
        destDB.loadRecords(getStagesTableName(executionType).name, rows)
      }

      // Copy all executions
      val rows = srcDB.getExecutions(executionType, idsToMigrate)
      rows.forEach { r -> r.set(DSL.field("partition"), peeredId) }
      destDB.loadRecords(getTableName(executionType).name, rows)

      return idsToMigrate.size
    } catch (e: Exception) {
      log.error("Failed to migrate $executionType chunk (first id: ${idsToMigrate[0]})", e)
    }

    return 0
  }

  /**
   * Run migrations in parallel
   * Chunks the specified IDs and uses the specified action to perform migrations on separate threads
   */
  private fun migrateInParallel(idsToMigrate: List<String>, migrationAction: (List<String>) -> Int): Int {
    val queue = ConcurrentLinkedQueue(idsToMigrate.chunked(chunkSize))

    val startTime = Instant.now()
    val migratedCount = AtomicInteger(0)

    // Only spin up as many threads as there are chunks to migrate (with upper bound of threadCount)
    val effectiveThreadCount = min(threadCount, queue.size)
    log.info("Kicking off migration with (chunk size: $chunkSize, threadCount: $effectiveThreadCount)")
    MutableList(effectiveThreadCount) {
      thread() {
        do {
          val chunkToProcess = queue.poll()
          if (chunkToProcess == null) {
            break
          }

          val migrated = migratedCount.addAndGet(migrationAction(chunkToProcess))

          if (it == 0) {
            // Only dump status logs for one of the threads - it's informational only anyway
            val elapsedTime = Duration.between(startTime, Instant.now()).toMillis()
            val etaMillis = (((idsToMigrate.size.toDouble() / migrated) * elapsedTime) - elapsedTime).toLong()
            log.info("Migrated $migrated of ${idsToMigrate.size}, ETA: ${Duration.ofMillis(etaMillis)}")
          }
        } while (true)
      }
    }.forEach { it.join() }

    return migratedCount.get()
  }

  override fun getPollingInterval() = pollingIntervalMs
  override fun getNotificationType() = "peeringAgent"
}
