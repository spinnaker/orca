/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.q

import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository

/**
 * Implementations handle a single message type from the queue.
 */
interface MessageHandler<M : Message> : (Message) -> Unit {

  val messageType: Class<M>
  val queue: Queue
  val repository: ExecutionRepository

  override fun invoke(message: Message): Unit =
    when (message.javaClass) {
      messageType -> {
        @Suppress("UNCHECKED_CAST")
        handle(message as M)
      }
      else ->
        throw IllegalArgumentException("Unsupported message type ${message.javaClass.simpleName}")
    }

  fun handle(message: M): Unit

  fun StageLevel.withStage(block: (Stage<*>) -> Unit) =
    withExecution { execution ->
      execution
        .getStages()
        .find { it.getId() == stageId }
        .let { stage ->
          if (stage == null) {
            queue.push(InvalidStageId(this))
          } else {
            block.invoke(stage)
          }
        }
    }

  fun ExecutionLevel.withExecution(block: (Execution<*>) -> Unit) =
    try {
      val execution = when (executionType) {
        Pipeline::class.java ->
          repository.retrievePipeline(executionId)
        Orchestration::class.java ->
          repository.retrieveOrchestration(executionId)
        else ->
          throw IllegalArgumentException("Unknown execution type $executionType")
      }
      block.invoke(execution)
    } catch(e: ExecutionNotFoundException) {
      queue.push(InvalidExecutionId(this))
    }

  fun Execution<*>.update() {
    when (this) {
      is Pipeline -> repository.store(this)
      is Orchestration -> repository.store(this)
    }
  }
}
