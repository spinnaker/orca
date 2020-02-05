package com.netflix.spinnaker.orca.peering

import com.netflix.spinnaker.orca.pipeline.model.Execution
import org.jooq.Record
import org.jooq.Table
import org.jooq.impl.DSL

/**
 * Convert an execution type to its jooq table object.
 */
internal fun getTableName(executionType: Execution.ExecutionType): Table<Record> {
  return when (executionType) {
    Execution.ExecutionType.PIPELINE -> DSL.table("pipelines")
    Execution.ExecutionType.ORCHESTRATION -> DSL.table("orchestrations")
  }
}

/**
 * Convert an execution type to its jooq stages table object.
 */
internal fun getStagesTableName(executionType: Execution.ExecutionType): Table<Record> {
  return when (executionType) {
    Execution.ExecutionType.PIPELINE -> DSL.table("pipeline_stages")
    Execution.ExecutionType.ORCHESTRATION -> DSL.table("orchestration_stages")
  }
}

internal fun getOcaStatusTableName() = "oca_cache_status"

internal fun getOcaCacheUuidTableName() = "oca_cache_uuids"
