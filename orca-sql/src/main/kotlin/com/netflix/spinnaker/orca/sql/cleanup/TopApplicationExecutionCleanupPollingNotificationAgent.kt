/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.sql.cleanup

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import java.util.concurrent.atomic.AtomicInteger
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component

@Component
@ConditionalOnExpression("\${pollers.top-application-execution-cleanup.enabled:false} && \${execution-repository.sql.enabled:false}")
class TopApplicationExecutionCleanupPollingNotificationAgent(
  clusterLock: NotificationClusterLock,
  private val jooq: DSLContext,
  registry: Registry,
  private val executionRepository: ExecutionRepository,
  @Value("\${pollers.top-application-execution-cleanup.interval-ms:3600000}") private val pollingIntervalMs: Long,
  @Value("\${pollers.top-application-execution-cleanup.threshold:2000}") private val threshold: Int,
  @Value("\${pollers.top-application-execution-cleanup.chunk-size:1}") private val chunkSize: Int,
  @Value("\${sql.partition-name:#{null}}") private val partitionName: String?
) : AbstractCleanupPollingAgent(
  clusterLock,
  pollingIntervalMs,
  registry) {

  override fun performCleanup() {
    var queryBuilder = jooq
      .select(DSL.field("application"), DSL.count(DSL.field("id")).`as`("count"))
      .from(DSL.table("orchestrations"))
      .where(DSL.noCondition())

    if (partitionName != null) {
      queryBuilder = queryBuilder
        .and(
          DSL.field("`partition`").eq(partitionName))
    }

    val applicationsWithOldOrchestrations = queryBuilder
      .groupBy(DSL.field("application"))
      .having(DSL.count(DSL.field("id")).gt(threshold))
      .fetch(DSL.field("application"), String::class.java)

    applicationsWithOldOrchestrations
      .filter { !it.isNullOrEmpty() }
      .forEach { application ->
        try {
          val startTime = System.currentTimeMillis()

          log.debug("Cleaning up old orchestrations for $application")
          val deletedOrchestrationCount = performCleanup(application)
          log.debug(
            "Cleaned up {} old orchestrations for {} in {}ms",
            deletedOrchestrationCount,
            application,
            System.currentTimeMillis() - startTime
          )
        } catch (e: Exception) {
          log.error("Failed to cleanup old orchestrations for $application", e)
          errorsCounter.increment()
        }
      }
  }

  /**
   * An application can have at most [threshold] completed orchestrations.
   */
  private fun performCleanup(application: String): Int {
    val deletedExecutionCount = AtomicInteger()

    val executionsToRemove = jooq
      .select(DSL.field("id"))
      .from(DSL.table("orchestrations"))
      .where(
        DSL.field("application").eq(application)
          .and(DSL.field("status").`in`(*completedStatuses.toTypedArray()))
      )
      .orderBy(DSL.field("build_time").desc())
      .limit(threshold, Int.MAX_VALUE)
      .fetch(DSL.field("id"), String::class.java)

    log.debug("Found {} old orchestrations for {}", executionsToRemove.size, application)

    executionsToRemove.chunked(chunkSize).forEach { ids ->
      deletedExecutionCount.addAndGet(ids.size)

      executionRepository.delete(ExecutionType.ORCHESTRATION, ids)
      registry.counter(deletedId.withTag("application", application)).add(ids.size.toDouble())
    }

    return deletedExecutionCount.toInt()
  }
}
