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

import com.netflix.spinnaker.orca.Command.RunStage
import com.netflix.spinnaker.orca.Command.RunTask
import com.netflix.spinnaker.orca.Event.*
import com.netflix.spinnaker.orca.Event.ConfigurationError.InvalidExecutionId
import com.netflix.spinnaker.orca.Event.ConfigurationError.InvalidStageId
import com.netflix.spinnaker.orca.Event.TaskResult.TaskSucceeded
import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.discovery.DiscoveryActivated
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner.NoSuchStageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode.TaskDefinition
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
  fun pollOnce() =
    ifEnabled {
      val event = eventQ.poll()
      when (event) {
        null -> log.debug("No events")
        is StageStarting -> event.handle()
        is TaskSucceeded -> event.handle()
        is StageComplete -> event.handle()
        else -> TODO("remaining message types")
      }
    }

  private fun StageStarting.handle() =
    withStage { stage ->
      stage.buildTasks(stage.builder())
      stage.setStatus(RUNNING)
      stage.setStartTime(clock.millis())

      repository.storeStage(stage)

      stage.getTasks().firstOrNull().let { task ->
        if (task != null) {
          commandQ.push(RunTask(
            executionType,
            executionId,
            stageId,
            task.id,
            task.implementingClass
          ))
        } else {
          TODO("else what? Nothing to do, just indicate end of stage?")
        }
      }
    }

  private fun StageComplete.handle() =
    withStage { stage ->
      stage.setStatus(SUCCEEDED)
      stage.setEndTime(clock.millis())
      repository.storeStage(stage)

      stage.downstreamStages().forEach {
        commandQ.push(RunStage(
          executionType,
          executionId,
          it.getId()
        ))
      }

      if (stage.getExecution().allStagesComplete()) {
        eventQ.push(ExecutionComplete(
          executionType,
          executionId
        ))
      }
    }

  private fun TaskSucceeded.handle() =
    withStage { stage ->
      val task = stage.getTasks().find { it.id == taskId }!!
      task.apply {
        status = SUCCEEDED
        endTime = clock.millis()
      }
      repository.storeStage(stage)

      if (task.isStageEnd) {
        eventQ.push(StageComplete(
          executionType,
          executionId,
          stageId
        ))
      } else {
        val index = stage.getTasks().indexOf(task)
        val nextTask = stage.getTasks()[index + 1]
        commandQ.push(RunTask(
          executionType,
          executionId,
          stageId,
          nextTask.id,
          nextTask.implementingClass
        ))
      }
    }

  private fun Execution<*>.allStagesComplete() =
    getStages().map { it.getStatus() }.all { it.complete }

  private fun StageLevel.withStage(block: (Stage<*>) -> Unit) =
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

  private fun ExecutionLevel.withExecution(block: (Execution<*>) -> Unit) =
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

  private fun Stage<*>.builder(): StageDefinitionBuilder =
    stageDefinitionBuilders.find { it.type == getType() }
      ?: throw NoSuchStageDefinitionBuilder(getType())
}

internal fun Stage<*>.buildTasks(builder: StageDefinitionBuilder) =
  builder
    .buildTaskGraph(this)
    .listIterator()
    .forEachWithMetadata { (taskNode, index, isFirst, isLast) ->
      when (taskNode) {
        is TaskDefinition -> {
          val task = DefaultTask()
          task.id = (index + 1).toString()
          task.name = taskNode.name
          task.implementingClass = taskNode.implementingClass
          task.stageStart = isFirst
          task.stageEnd = isLast
          getTasks().add(task)
        }
        else -> TODO("loops, etc.")
      }
    }
