package com.netflix.spinnaker.orca.remote.stage.messages

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.remote.messages.RemoteStageMessage
import com.netflix.spinnaker.orca.api.pipeline.remote.messages.RemoteStageStatusMessage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.remote.stage.StageExecutionNotFound
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RemoteStageStatusMessageHandler(
  val executionRepository: ExecutionRepository
) : RemoteStageMessageHandler<RemoteStageStatusMessage> {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun supports(message: Class<RemoteStageMessage>): Boolean {
    return message == RemoteStageStatusMessage::class.java
  }

  override fun handle(message: RemoteStageStatusMessage) {
    log.info("Remote stage execution '{}' status received with '{}'", message.stageExecutionId, message.status.toString())
    val pipelineExecution = executionRepository.retrieve(ExecutionType.PIPELINE, message.pipelineExecutionId)
    val stageExecution = pipelineExecution.stages.find { it.id == message.stageExecutionId }

    if (stageExecution != null) {
      stageExecution.status = message.status
      stageExecution.outputs = message.outputs
      stageExecution.context["remoteTasks"] = message.tasks
      stageExecution.context["remoteStartTime"] = message.startTime
      stageExecution.context["remoteEndTime"] = message.endTime
      stageExecution.context["remoteError"] = message.error

      executionRepository.storeStage(stageExecution)
      log.info("Stage execution '{}' status updated '{}'", stageExecution.id, stageExecution.status)
    } else {
      // Throwing an exception here will bubble up to the PubsubSubscriber which will keep the message
      // in the queue until it is either ACKed and deleted (successfully looks up the stage execution)
      // or the message hits the max retention time in the queue (wherein it will be deleted).
      throw StageExecutionNotFound("Unable to find stage execution ${message.stageExecutionId}")
    }
  }
}
