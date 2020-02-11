package com.netflix.spinnaker.orca.peering

import com.netflix.spectator.api.Id
import com.netflix.spinnaker.orca.pipeline.model.Execution
import org.jooq.Record
import org.jooq.Table
import org.jooq.impl.DSL

internal fun getExecutionTable(executionType: Execution.ExecutionType): Table<Record> {
  return when (executionType) {
    Execution.ExecutionType.PIPELINE -> DSL.table("pipelines")
    Execution.ExecutionType.ORCHESTRATION -> DSL.table("orchestrations")
  }
}

internal fun getStagesTable(executionType: Execution.ExecutionType): Table<Record> {
  return when (executionType) {
    Execution.ExecutionType.PIPELINE -> DSL.table("pipeline_stages")
    Execution.ExecutionType.ORCHESTRATION -> DSL.table("orchestration_stages")
  }
}

internal fun Id.tag(executionType: Execution.ExecutionType): Id {
  return this
    .withTag("executionType", executionType.toString())
}

internal fun Id.tag(executionType: Execution.ExecutionType, state: ExecutionState): Id {
  return this
    .withTag("executionType", executionType.toString())
    .withTag("state", state.toString())
}

internal enum class ExecutionState {
  ACTIVE,
  COMPLETED,
}
