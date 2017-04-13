/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.q.amazon

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.config.SqsProperties
import com.netflix.spinnaker.orca.q.Message
import com.netflix.spinnaker.orca.q.Queue
import java.time.Duration
import java.util.*

class SqsQueue(
  private val amazonSqs: AmazonSQS,
  sqsProperties: SqsProperties,
  override val ackTimeout: Duration = Duration.ofMinutes(1)
) : Queue {

  private val objectMapper = ObjectMapper().apply {
    registerModule(KotlinModule())
  }

  private val queueUrl = amazonSqs.createQueue(sqsProperties.queueName).queueUrl
  private val messageReceiptHandles: MutableMap<UUID, String> = mutableMapOf()

  override fun poll(): Message? {
    val req = ReceiveMessageRequest(queueUrl)
      .withMaxNumberOfMessages(1)
      .withWaitTimeSeconds(10)
      .withVisibilityTimeout(ackTimeout.seconds.toInt())
      .withAttributeNames("ApproximateFirstReceiveTimestamp", "ApproximateReceiveCount", "SentTimestamp")
    val result = amazonSqs.receiveMessage(req)

    if (result.messages.isEmpty()) {
      return null
    }

    val sqsMessage = result.messages.first()
    val message = objectMapper.readValue(sqsMessage.body, Message::class.java)
    messageReceiptHandles[message.id] = sqsMessage.receiptHandle

    return message
  }

  override fun push(message: Message) {
    amazonSqs.sendMessage(queueUrl, objectMapper.writeValueAsString(message))
  }

  override fun push(message: Message, delay: Duration) {
    amazonSqs.sendMessage(
      SendMessageRequest(queueUrl, objectMapper.writeValueAsString(message))
        .withDelaySeconds(delay.seconds.toInt())
    )
  }

  override fun ack(message: Message) {
    if (messageReceiptHandles.contains(message.id)) {
      amazonSqs.deleteMessage(queueUrl, messageReceiptHandles[message.id])
    }
    messageReceiptHandles.remove(message.id)
  }

}
