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
package com.netflix.spinnaker.orca.sql.telemetry

import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.histogram.PercentileTimer
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.DelegatingExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import com.netflix.spinnaker.orca.sql.pipeline.persistence.ActiveExecutionsReport
import com.netflix.spinnaker.orca.sql.pipeline.persistence.ExecutionStatisticsRepository
import com.netflix.spinnaker.orca.sql.pipeline.persistence.SqlExecutionRepository
import rx.Observable
import java.lang.System.currentTimeMillis
import java.util.concurrent.TimeUnit.MILLISECONDS

class SqlInstrumentedExecutionRepository(
  private val executionRepository: SqlExecutionRepository,
  private val registry: Registry
) : DelegatingExecutionRepository<SqlExecutionRepository>, ExecutionStatisticsRepository {

  private fun invocationId(method: String) =
    registry.createId("sql.executionRepository.$method.invocations")

  private fun timingId(method: String) =
    registry.createId("sql.executionRepository.$method.timing")

  private fun <T> withMetrics(method: String, fn: () -> T): T {
    val start = currentTimeMillis()

    try {
      val result = fn()
      registry.counter(invocationId(method).withTag("result", "success")).increment()
      recordTiming(timingId(method).withTag("result", "success"), start)
      return result
    } catch (e: Exception) {
      registry.counter(invocationId(method).withTag("result", "failure")).increment()
      recordTiming(timingId(method).withTag("result", "failure"), start)
      throw e
    }
  }

  private fun recordTiming(id: Id, startTimeMs: Long) {
    PercentileTimer.get(registry, id).record(currentTimeMillis() - startTimeMs, MILLISECONDS)
  }

  override fun getDelegate() = executionRepository

  override fun store(execution: Execution) {
    withMetrics("store") {
      executionRepository.store(execution)
    }
  }

  override fun storeStage(stage: Stage) {
    withMetrics("storeStage") {
      executionRepository.storeStage(stage)
    }
  }

  override fun updateStageContext(stage: Stage) {
    withMetrics("updateStageContext") {
      executionRepository.updateStageContext(stage)
    }
  }

  override fun removeStage(execution: Execution, stageId: String) {
    withMetrics("removeStage") {
      executionRepository.removeStage(execution, stageId)
    }
  }

  override fun addStage(stage: Stage) {
    withMetrics("addStage") {
      executionRepository.addStage(stage)
    }
  }

  override fun cancel(type: Execution.ExecutionType, id: String) {
    withMetrics("cancel2") {
      executionRepository.cancel(type, id)
    }
  }

  override fun cancel(type: Execution.ExecutionType, id: String, user: String?, reason: String?) {
    withMetrics("cancel4") {
      executionRepository.cancel(type, id, user, reason)
    }
  }

  override fun pause(type: Execution.ExecutionType, id: String, user: String?) {
    withMetrics("pause") {
      executionRepository.pause(type, id, user)
    }
  }

  override fun resume(type: Execution.ExecutionType, id: String, user: String?) {
    withMetrics("resume3") {
      executionRepository.resume(type, id, user)
    }
  }

  override fun resume(type: Execution.ExecutionType, id: String, user: String?, ignoreCurrentStatus: Boolean) {
    withMetrics("resume4") {
      executionRepository.resume(type, id, user, ignoreCurrentStatus)
    }
  }

  override fun isCanceled(type: Execution.ExecutionType, id: String): Boolean {
    return withMetrics("isCanceled") {
      executionRepository.isCanceled(type, id)
    }
  }

  override fun updateStatus(type: Execution.ExecutionType, id: String, status: ExecutionStatus) {
    withMetrics("updateStatus") {
      executionRepository.updateStatus(type, id, status)
    }
  }

  override fun delete(type: Execution.ExecutionType, id: String) {
    withMetrics("delete") {
      executionRepository.delete(type, id)
    }
  }

  override fun retrieve(type: Execution.ExecutionType, id: String): Execution {
    return withMetrics("retrieve2") {
      executionRepository.retrieve(type, id)
    }
  }

  override fun retrieve(type: Execution.ExecutionType): Observable<Execution> {
    return withMetrics("retrieve1") {
      executionRepository.retrieve(type)
    }
  }

  override fun retrieve(type: Execution.ExecutionType, criteria: ExecutionCriteria): Observable<Execution> {
    return withMetrics("retrieve3") {
      executionRepository.retrieve(type, criteria)
    }
  }

  override fun retrieveBufferedExecutions(): MutableList<Execution> {
    return withMetrics("retrieveBufferedExecutions") {
      executionRepository.retrieveBufferedExecutions()
    }
  }

  override fun retrieveOrchestrationsForApplication(
    application: String,
    criteria: ExecutionCriteria
  ): Observable<Execution> {
    return withMetrics("retrieveOrchestrationsForApplication") {
      executionRepository.retrieveOrchestrationsForApplication(application, criteria)
    }
  }

  override fun retrieveOrchestrationsForApplication(
    application: String,
    criteria: ExecutionCriteria,
    sorter: ExecutionComparator?
  ): MutableList<Execution> {
    return withMetrics("retrieveOrchestrationsForApplication3") {
      executionRepository.retrieveOrchestrationsForApplication(application, criteria, sorter)
    }
  }

  override fun retrievePipelinesForApplication(application: String): Observable<Execution> {
    return withMetrics("retrievePipelinesForApplication") {
      executionRepository.retrievePipelinesForApplication(application)
    }
  }

  override fun retrievePipelinesForPipelineConfigId(
    pipelineConfigId: String,
    criteria: ExecutionCriteria
  ): Observable<Execution> {
    return withMetrics("retrievePipelinesForPipelineConfigId") {
      executionRepository.retrievePipelinesForPipelineConfigId(pipelineConfigId, criteria)
    }
  }

  override fun retrieveByCorrelationId(executionType: Execution.ExecutionType, correlationId: String): Execution {
    return withMetrics("retrieveByCorrelationId") {
      executionRepository.retrieveByCorrelationId(executionType, correlationId)
    }
  }

  override fun retrieveOrchestrationForCorrelationId(correlationId: String): Execution {
    return withMetrics("retrieveOrchestrationForCorrelationId") {
      executionRepository.retrieveOrchestrationForCorrelationId(correlationId)
    }
  }

  override fun retrievePipelineForCorrelationId(correlationId: String): Execution {
    return withMetrics("retrievePipelineForCorrelationId") {
      executionRepository.retrievePipelineForCorrelationId(correlationId)
    }
  }

  override fun retrieveAllApplicationNames(type: Execution.ExecutionType?): List<String> {
    return withMetrics("retrieveAllApplicationNames1") {
      executionRepository.retrieveAllApplicationNames(type)
    }
  }

  override fun retrieveAllApplicationNames(type: Execution.ExecutionType?, minExecutions: Int): List<String> {
    return withMetrics("retrieveAllApplicationNames2") {
      executionRepository.retrieveAllApplicationNames(type, minExecutions)
    }
  }

  override fun countActiveExecutions(): ActiveExecutionsReport {
    return withMetrics("countActiveExecutions") {
      executionRepository.countActiveExecutions()
    }
  }

  override fun retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
    pipelineConfigIds: MutableList<String>,
    buildTimeStartBoundary: Long,
    buildTimeEndBoundary: Long,
    executionCriteria: ExecutionCriteria
  ): List<Execution> {
    return withMetrics("retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary") {
      executionRepository.retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
        pipelineConfigIds,
        buildTimeStartBoundary,
        buildTimeEndBoundary,
        executionCriteria)
    }
  }

  override fun retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
    pipelineConfigIds: List<String>,
    buildTimeStartBoundary: Long,
    buildTimeEndBoundary: Long,
    executionCriteria: ExecutionCriteria
  ): List<Execution> {
    return withMetrics("retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary") {
      executionRepository.retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
        pipelineConfigIds,
        buildTimeStartBoundary,
        buildTimeEndBoundary,
        executionCriteria)
    }
  }

  override fun hasExecution(type: Execution.ExecutionType, id: String): Boolean {
    return withMetrics("hasExecution") {
      executionRepository.hasExecution(type, id)
    }
  }

  override fun retrieveAllExecutionIds(type: Execution.ExecutionType): MutableList<String> {
    return withMetrics("retrieveAllExecutionIds") {
      executionRepository.retrieveAllExecutionIds(type)
    }
  }
}
