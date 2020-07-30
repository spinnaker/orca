package com.netflix.spinnaker.orca.remote.stage.aws

import com.amazonaws.services.sqs.model.Message
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.annotations.VisibleForTesting
import com.netflix.spinnaker.kork.pubsub.aws.NotificationMessage
import com.netflix.spinnaker.kork.pubsub.aws.api.AmazonPubsubMessageHandler
import com.netflix.spinnaker.orca.api.pipeline.remote.messages.RemoteStageMessage
import com.netflix.spinnaker.orca.remote.stage.RemoteStageMessageHandlingException
import com.netflix.spinnaker.orca.remote.stage.messages.RemoteStageMessageHandler

class RemoteStageAmazonMessageHandler(
  private val objectMapper: ObjectMapper,
  private val messageHandlers: Collection<RemoteStageMessageHandler<RemoteStageMessage>>
) : AmazonPubsubMessageHandler {

  override fun handleMessage(message: Message) {
    try {
      val snsMessage = objectMapper.readValue(message.body, NotificationMessage::class.java)
      val remoteStageMessage: RemoteStageMessage = objectMapper.readValue(snsMessage.message, RemoteStageMessage::class.java)
      handleInternal(remoteStageMessage)
    } catch (e: JsonProcessingException) {
      throw RemoteStageMessageHandlingException(e)
    }
  }

  @VisibleForTesting
  fun handleInternal(message: RemoteStageMessage) {
    messageHandlers.forEach { messageHandler ->
      if (messageHandler.supports(message.javaClass)) {
        messageHandler.handle(message)
      }
    }
  }
}
