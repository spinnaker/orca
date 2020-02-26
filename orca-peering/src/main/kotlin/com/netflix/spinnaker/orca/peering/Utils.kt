package com.netflix.spinnaker.orca.peering

import com.netflix.spinnaker.orca.pipeline.model.PipelineExecution
import org.jooq.Record
import org.jooq.Table
import org.jooq.impl.DSL

internal fun getExecutionTable(executionType: PipelineExecution.ExecutionType): Table<Record> {
  return when (executionType) {
    PipelineExecution.ExecutionType.PIPELINE -> DSL.table("pipelines")
    PipelineExecution.ExecutionType.ORCHESTRATION -> DSL.table("orchestrations")
  }
}

internal fun getStagesTable(executionType: PipelineExecution.ExecutionType): Table<Record> {
  return when (executionType) {
    PipelineExecution.ExecutionType.PIPELINE -> DSL.table("pipeline_stages")
    PipelineExecution.ExecutionType.ORCHESTRATION -> DSL.table("orchestration_stages")
  }
}
