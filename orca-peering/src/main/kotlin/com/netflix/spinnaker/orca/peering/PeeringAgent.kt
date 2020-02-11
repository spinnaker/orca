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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.model.Execution
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

/**
 *
 */
class PeeringAgent(
  /**
   * ID of our peer
   */
  private val peeredId: String,

  /**
   * Interval in ms at which this agent runs
   */
  private val pollingIntervalMs: Long,

  /**
   * Executor service to use for scheduling parallel copying
   */
  private val executor: ExecutorService,

  /**
   * Number of parallel threads to use for copying
   * (it's expected that the executor is configured with this many as well)
   */
  private val threadCount: Int,

  /**
   * Chunk size to use during copy (this translates to how many IDs we mutate in a single DB query)
   */
  private val chunkSize: Int,

  /**
   * Maximum allowed clock drift when performing comparison of which executions need to be copied
   * For example: it's possible we take a snapshot of the src db and the latest updated_at is 1000
   * because not all orca instances are fully clock synchronized another instance might mutate an execution after we take
   * the snapshot but its clock might read 998
   */
  private val clockDriftMs: Long,

  /**
   * Source (our peers) database access layer
   */
  private val srcDB: SqlDbRawAccess,

  /**
   * Destination (our own) database access layer
   */
  private val destDB: SqlDbRawAccess,

  /**
   * Used to dynamically turn off either all of peering or peering of a specific host
   */
  private val dynamicConfigService: DynamicConfigService,
  private val registry: Registry,
  clusterLock: NotificationClusterLock
) : AbstractPollingNotificationAgent(clusterLock) {

  private val log = LoggerFactory.getLogger(javaClass)
  private var completedPipelinesMostRecentUpdatedTime = 0L
  private var completedOrchestrationsMostRecentUpdatedTime = 0L

  private val peeringLagTimerId = registry.createId("pollers.peering.lag").withTag("peerId", peeredId)
  private val peeringNumPeeredId = registry.createId("pollers.peering.numProcessed").withTag("peerId", peeredId)
  private val peeringNumStagesDeletedId = registry.createId("pollers.peering.numStagesDeleted").withTag("peerId", peeredId)
  private val peeringNumErrorsId = registry.createId("pollers.peering.numErrors").withTag("peerId", peeredId)

  override fun tick() {
    if (dynamicConfigService.isEnabled("pollers.peering", true) &&
      dynamicConfigService.isEnabled("pollers.peering.$peeredId", true)) {
      copyExecutions(Execution.ExecutionType.PIPELINE)
      copyExecutions(Execution.ExecutionType.ORCHESTRATION)
    }
  }

  private fun copyExecutions(executionType: Execution.ExecutionType) {
    val mostRecentUpdatedTime = when (executionType) {
      Execution.ExecutionType.ORCHESTRATION -> completedOrchestrationsMostRecentUpdatedTime
      Execution.ExecutionType.PIPELINE -> completedPipelinesMostRecentUpdatedTime
    }
    val isFirstRun = mostRecentUpdatedTime == 0L

    // On first copy of completed executions, there is no point in copying active executions
    // because they will be woefully out of date (since the first bulk copy will likely take 20+ minutes)
    if (isFirstRun) {
      copyCompletedExecutions(executionType)
    } else {
      registry
        .timer(peeringLagTimerId.tag(executionType))
        .record {
          copyCompletedExecutions(executionType)
          copyActiveExecutions(executionType)
        }
    }
  }

  /**
   * Migrate running/active executions of given type
   */
  private fun copyActiveExecutions(executionType: Execution.ExecutionType) {
    log.debug("Starting active $executionType copy for peering")

    val activePipelineIds = srcDB.getActiveExecutionIds(executionType, peeredId)

    if (activePipelineIds.isNotEmpty()) {
      log.debug("Found ${activePipelineIds.size} active $executionType, copying all")
      val migrationResult = migrateInParallel(activePipelineIds) { chunk -> migrateExecutionChunk(executionType, chunk) }
      log.debug("Completed active $executionType peering: copied ${migrationResult.count} of ${activePipelineIds.size}")

      registry
        .counter(peeringNumPeeredId.tag(executionType, ExecutionState.ACTIVE))
        .increment(migrationResult.count.toLong())
    } else {
      log.debug("No active $executionType executions to copy for peering")
    }
  }

  /**
   * Migrate completed executions of given type
   */
  private fun copyCompletedExecutions(executionType: Execution.ExecutionType) {
    val updatedAfter = when (executionType) {
      Execution.ExecutionType.ORCHESTRATION -> completedOrchestrationsMostRecentUpdatedTime
      Execution.ExecutionType.PIPELINE -> completedPipelinesMostRecentUpdatedTime
    }
    log.debug("Starting completed $executionType copy for peering with $executionType updatedAfter=$updatedAfter")

    val completedPipelineKeys = srcDB.getCompletedExecutionIds(executionType, peeredId, updatedAfter)
    val migratedPipelineKeys = destDB.getCompletedExecutionIds(executionType, peeredId, updatedAfter)
      .map { it.id to it }
      .toMap()
    val pipelineIdsToMigrate = completedPipelineKeys
      .filter { key -> migratedPipelineKeys[key.id]?.updated_at ?: 0 < key.updated_at }
      .map { it.id }
      .toList()

    if (pipelineIdsToMigrate.isNotEmpty()) {
      log.debug("Found ${migratedPipelineKeys.size} $executionType candidates with ${migratedPipelineKeys.size} already copied for peering, ${pipelineIdsToMigrate.size} still need copying")
      val migrationResult = migrateInParallel(pipelineIdsToMigrate) { chunk -> migrateExecutionChunk(executionType, chunk) }
      log.debug("Completed completed $executionType peering: copied ${migrationResult.count} of ${pipelineIdsToMigrate.size} with latest updatedAt=${migrationResult.latestUpdatedAt}")

      if (executionType == Execution.ExecutionType.ORCHESTRATION) {
        completedOrchestrationsMostRecentUpdatedTime = migrationResult.latestUpdatedAt - clockDriftMs
      } else {
        completedPipelinesMostRecentUpdatedTime = migrationResult.latestUpdatedAt - clockDriftMs
      }

      registry
        .counter(peeringNumPeeredId.tag(executionType, ExecutionState.COMPLETED))
        .increment(migrationResult.count.toLong())
    } else {
      log.debug("No completed $executionType executions to copy for peering")
    }
  }

  /**
   * Migrates executions (orchestrations or pipelines) and its stages given IDs of the executions
   */
  private fun migrateExecutionChunk(executionType: Execution.ExecutionType, idsToMigrate: List<String>): MigrationChunkResult {
    var latestUpdatedAt = 0L
    try {
      // Step 1: Copy all stages
      val stagesToMigrate = srcDB.getStageIdsForExecutions(executionType, idsToMigrate)

      // It is possible that the source stage list has mutated. Normally, this is only possible when an execution
      // is restarted (e.g. restarting a deploy stage will delete all its synthetic stages and start over).
      // We delete all stages that are no longer in our peer first, then we update/copy all other stages
      val stagesPresent = destDB.getStageIdsForExecutions(executionType, idsToMigrate)
      val stagesToMigrateHash = stagesToMigrate.toHashSet()
      val stagesToDelete = stagesPresent.filter { !stagesToMigrateHash.contains(it) }
      if (stagesToDelete.any()) {
        destDB.deleteStages(executionType, stagesToDelete)
        registry
          .counter(peeringNumStagesDeletedId.tag(executionType))
          .increment(stagesToDelete.size.toLong())
      }

      for (chunk in stagesToMigrate.chunked(chunkSize)) {
        val rows = srcDB.getStages(executionType, chunk)
        destDB.loadRecords(getStagesTable(executionType).name, rows)
      }

      // Step 2: Copy all executions
      val rows = srcDB.getExecutions(executionType, idsToMigrate)
      rows.forEach { r -> r.set(DSL.field("partition"), peeredId)
        latestUpdatedAt = max(latestUpdatedAt, r.get("updated_at", Long::class.java))
      }
      destDB.loadRecords(getExecutionTable(executionType).name, rows)

      return MigrationChunkResult(latestUpdatedAt, idsToMigrate.size)
    } catch (e: Exception) {
      log.error("Failed to peer $executionType chunk (first id: ${idsToMigrate[0]})", e)

      registry
        .counter(peeringNumErrorsId.tag(executionType))
        .increment()
    }

    return MigrationChunkResult(0, 0)
  }

  /**
   * Run migrations in parallel
   * Chunks the specified IDs and uses the specified action to perform migrations on separate threads
   */
  private fun migrateInParallel(idsToMigrate: List<String>, migrationAction: (List<String>) -> MigrationChunkResult): MigrationChunkResult {
    val queue = ConcurrentLinkedQueue(idsToMigrate.chunked(chunkSize))

    val startTime = Instant.now()
    val migratedCount = AtomicInteger(0)

    // Only spin up as many threads as there are chunks to migrate (with upper bound of threadCount)
    val effectiveThreadCount = min(threadCount, queue.size)
    log.info("Kicking off migration with (chunk size: $chunkSize, threadCount: $effectiveThreadCount)")

    val futures = MutableList<Future<Long>>(effectiveThreadCount) { threadIndex ->
      executor.submit(Callable<Long> {
        var latestUpdatedAt: Long = 0

        do {
          val chunkToProcess = queue.poll() ?: break

          val result = migrationAction(chunkToProcess)
          val migrated = migratedCount.addAndGet(result.count)
          latestUpdatedAt = max(latestUpdatedAt, result.latestUpdatedAt)

          if (threadIndex == 0) {
            // Only dump status logs for one of the threads - it's informational only anyway
            val elapsedTime = Duration.between(startTime, Instant.now()).toMillis()
            val etaMillis = (((idsToMigrate.size.toDouble() / migrated) * elapsedTime) - elapsedTime).toLong()
            log.info("Migrated $migrated of ${idsToMigrate.size}, ETA: ${Duration.ofMillis(etaMillis)}")
          }
        } while (true)

        return@Callable latestUpdatedAt
      })
    }

    var latestUpdatedAt: Long = 0
    for (future in futures) {
      latestUpdatedAt = max(latestUpdatedAt, future.get())
    }

    return MigrationChunkResult(latestUpdatedAt, migratedCount.get())
  }

  override fun getPollingInterval() = pollingIntervalMs
  override fun getNotificationType(): String = this.javaClass.simpleName

  data class MigrationChunkResult(
    val latestUpdatedAt: Long,
    val count: Int
  )
}
