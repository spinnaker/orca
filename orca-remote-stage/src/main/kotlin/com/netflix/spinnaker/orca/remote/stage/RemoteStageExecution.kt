package com.netflix.spinnaker.orca.remote.stage

import com.netflix.spinnaker.kork.annotations.Alpha

@Alpha
data class RemoteStageExecution(
  val type: String,
  val id: String,
  val pipelineExecutionId: String,
  val context: MutableMap<String, Any>,
  val upstreamStageOutputs: MutableMap<String, Any>,
  val callback: Callback
)
