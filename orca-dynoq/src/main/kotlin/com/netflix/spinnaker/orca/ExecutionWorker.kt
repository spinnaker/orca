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

package com.netflix.spinnaker.orca

import com.netflix.spinnaker.orca.Event.ConfigurationError.InvalidExecutionId
import com.netflix.spinnaker.orca.Event.ConfigurationError.InvalidStageId
import com.netflix.spinnaker.orca.Event.StageStarting
import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.discovery.DiscoveryActivated
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner.NoSuchStageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.*
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

@Component open class ExecutionWorker(
  val commandQ: CommandQueue,
  val eventQ: EventQueue,
  val repository: ExecutionRepository,
  val clock: Clock,
  val stageDefinitionBuilders: Collection<StageDefinitionBuilder>
) : DiscoveryActivated() {

  @Scheduled(fixedDelay = 10)
  fun pollOnce() {
    ifEnabled {
      val event = eventQ.poll()
      when (event) {
        null -> log.debug("No events")
        is StageStarting -> event.handle()
      }
    }
  }

  private fun StageStarting.handle() {
    withStage { stage ->
      val graph = stage.builder().buildTaskGraph(stage)
      val first: TaskNode = graph.first()
      when (first) {
        is TaskNode.TaskDefinition -> {
          stage.setStatus(RUNNING)
          stage.setStartTime(clock.millis())
          val task = DefaultTask()
          task.id = "1"
          task.name = first.name
          task.implementingClass = first.implementingClass
          task.stageStart = true
          task.stageEnd = true
          stage.getTasks().add(task)
          repository.storeStage(stage)
          commandQ.push(Command.RunTask(executionType, executionId, stageId, "1", first.implementingClass))
        }
      }
    }
  }

  private fun StageStarting.withStage(block: (Stage<*>) -> Unit) =
    withExecution { execution ->
      execution
        .getStages()
        .find { it.getId() == stageId }
        .let { stage ->
          if (stage == null) {
            eventQ.push(InvalidStageId(executionId, stageId))
          } else {
            block.invoke(stage)
          }
        }
    }

  private fun StageStarting.withExecution(block: (Execution<*>) -> Unit) =
    try {
      when (executionType) {
        Pipeline::class.java -> block.invoke(repository.retrievePipeline(executionId))
        Orchestration::class.java -> block.invoke(repository.retrieveOrchestration(executionId))
        else -> throw IllegalArgumentException("Unknown execution type $executionType")
      }
    } catch(e: ExecutionNotFoundException) {
      eventQ.push(InvalidExecutionId(executionId))
    }

  private fun Stage<*>.builder(): StageDefinitionBuilder =
    stageDefinitionBuilders.find { it.type == getType() }
      ?: throw NoSuchStageDefinitionBuilder(getType())
}
