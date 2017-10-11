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
package com.netflix.spinnaker.orca.q.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.q.admin.DeadLetterRedriveCommand
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.util.Pool

/**
 * Offers the ability to redrive a range of dead-lettered messages back onto the queue.
 *
 * We currently do not support re-driving RunTask messages, for fear of non-idempotent operations;
 * although this should be trimmed down over time.
 */
class RedisDeadLetterRedriveCommand(
  deadLetterQueueName: String,
  private val queue: Queue,
  private val pool: Pool<Jedis>,
  private val executionRepository: ExecutionRepository
) : DeadLetterRedriveCommand {

  private val log = LoggerFactory.getLogger(javaClass)

  private val dlqKey = "$deadLetterQueueName.messages"

  private val mapper = ObjectMapper().apply {
    registerModule(KotlinModule())
  }

  override fun redrive(after: Long?, before: Long?) {
    pool.resource.use { redis ->
      val redrivenMessages = getMessages(redis, after, before)

      log.warn("Redriving ${redrivenMessages.size} dead lettered messages between ${after?:"-inf"} and ${before?:"+inf"}")
      redrivenMessages
        .map { mapper.readValue(it, Message::class.java) }
        .filter {
          it is ExecutionLevel && !isBlacklistedRunTaskType(it)
        }
        .forEach { message ->
          if (message !is ExecutionLevel) {
            // Kotlin inference fun
            return@forEach
          }

          when(message.executionType) {
            Pipeline::class.java -> executionRepository.retrievePipeline(message.executionId)
            Orchestration::class.java -> executionRepository.retrieveOrchestration(message.executionId)
            else -> {
              log.error("Unknown execution type '${message.executionType}' on message: $message")
              null
            }
          }?.let { execution: Execution<out Execution<*>> ->
            if (message is StageLevel) {
              execution.stageById(message.stageId).let { stage ->
                stage.getTasks()
                  .filter { stage.getStatus().isFailure }
                  .forEach { it.status = ExecutionStatus.NOT_STARTED }
                log.info("Changing stage ${stage.getId()} status from ${stage.getStatus()} to NOT_STARTED")
                stage.setStatus(ExecutionStatus.NOT_STARTED)
                executionRepository.storeStage(stage)
              }
            }
          }

          log.info("Changing execution ${message.executionId} status to RUNNING")
          executionRepository.updateStatus(message.executionId, ExecutionStatus.RUNNING)

          log.warn("Redriving message: $message")
          queue.push(message)
        }
    }
  }

  private fun getMessages(redis: Jedis, after: Long?, before: Long?)
    = redis.zrangeByScore(dlqKey, after?.toDouble()?.toString() ?: "-inf", before?.toDouble()?.toString() ?: "+inf")

  private fun isBlacklistedRunTaskType(message: Message): Boolean {
    if (message !is RunTask) {
      return false
    }
    // TODO rz - check message.taskType.isAssignableFrom(...)
    return true
  }
}

