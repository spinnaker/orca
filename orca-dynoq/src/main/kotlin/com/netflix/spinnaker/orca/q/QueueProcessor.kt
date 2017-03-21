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
import com.netflix.spinnaker.orca.q.Event.ConfigurationError.InvalidExecutionId
import com.netflix.spinnaker.orca.q.Event.ConfigurationError.InvalidStageId
import com.netflix.spinnaker.orca.q.Event.ExecutionLevel
import com.netflix.spinnaker.orca.q.Event.StageLevel

/**
 * Some common functionality shared by [ExecutionWorker] and [TaskWorker].
 */
internal interface QueueProcessor {

  val commandQ: CommandQueue
  val eventQ: EventQueue
  val repository: ExecutionRepository

  fun StageLevel.withStage(block: (Stage<out Execution<*>>) -> Unit) =
    withExecution { execution ->
      execution
        .getStages()
        .find { it.getId() == stageId }
        .let { stage ->
          if (stage == null) {
            eventQ.push(InvalidStageId(executionType, executionId, stageId))
          } else {
            block.invoke(stage)
          }
        }
    }

  fun ExecutionLevel.withExecution(block: (Execution<*>) -> Unit) =
    try {
      when (executionType) {
        Pipeline::class.java ->
          block.invoke(repository.retrievePipeline(executionId))
        Orchestration::class.java ->
          block.invoke(repository.retrieveOrchestration(executionId))
        else ->
          throw IllegalArgumentException("Unknown execution type $executionType")
      }
    } catch(e: ExecutionNotFoundException) {
      eventQ.push(InvalidExecutionId(executionType, executionId))
    }
}
