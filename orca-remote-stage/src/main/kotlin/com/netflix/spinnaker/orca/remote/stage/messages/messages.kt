package com.netflix.spinnaker.orca.remote.stage.messages

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus

interface RemoteStageMessage {
  val stageExecutionId: String
  val pipelineExecutionId: String
}

data class RemoteStageStatusMessage(
  override val stageExecutionId: String,
  override val pipelineExecutionId: String,
  val status: ExecutionStatus,
  val outputs: MutableMap<String, Any>,
  val startTime: Long,
  val endTime: Long,
  val error: String = "none",
  val tasks: MutableList<RemoteStageTaskStatusMessage> =
    mutableListOf(RemoteStageTaskStatusMessage(stageExecutionId, pipelineExecutionId))
) : RemoteStageMessage

//Currently does not have a message handler.  In order to make this useful Deck would also need to
//support this concept of remote tasks.
data class RemoteStageTaskStatusMessage(
  override val stageExecutionId: String,
  override val pipelineExecutionId: String,
  val name: String = "remoteNoOpTask",
  val description: String = "The default remote stage no-op task",
  val status: ExecutionStatus = ExecutionStatus.SKIPPED,
  val outputs: MutableMap<String, Any> = mutableMapOf(),
  val startTime: Long = 0,
  val endTime: Long = 0,
  val error: String = "none"
) : RemoteStageMessage
