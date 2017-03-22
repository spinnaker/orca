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

import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.discovery.DiscoveryActivated
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner.NoSuchStageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode.TaskDefinition
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.Event.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

@Component open class ExecutionWorker @Autowired constructor(
  override val commandQ: CommandQueue,
  override val eventQ: EventQueue,
  override val repository: ExecutionRepository,
  val clock: Clock,
  val stageDefinitionBuilders: Collection<StageDefinitionBuilder>
) : DiscoveryActivated, QueueProcessor {

  override val log: Logger = getLogger(javaClass)
  override val enabled = AtomicBoolean(false)

  @Scheduled(fixedDelay = 10)
  fun pollOnce() =
    ifEnabled {
      val event = eventQ.poll()
      if (event != null) log.info("Received event $event")
      when (event) {
        null -> log.debug("No events")
        is TaskStarting -> withAck(event) { handle() }
        is TaskComplete -> withAck(event) { handle() }
        is StageStarting -> withAck(event) { handle() }
        is StageComplete -> withAck(event) { handle() }
        is ExecutionStarting -> withAck(event) { handle() }
        is ExecutionComplete -> withAck(event) { handle() }
        is ConfigurationError -> withAck(event) { handle() }
        else -> TODO("remaining message types")
      }
    }

  private fun ExecutionStarting.handle() =
    withExecution { execution ->
      repository.updateStatus(executionId, RUNNING)

      execution
        .initialStages()
        .forEach {
          eventQ.push(StageStarting(
            executionType,
            executionId,
            it.getId()
          ))
        }
    }

  private fun ExecutionComplete.handle() =
    repository.updateStatus(executionId, status)

  private fun StageStarting.handle() =
    withStage { stage ->
      stage.builder().buildTasks(stage)
      stage.setStatus(RUNNING)
      stage.setStartTime(clock.millis())

      repository.storeStage(stage)

      stage.getTasks().firstOrNull().let { task ->
        if (task != null) {
          eventQ.push(TaskStarting(
            executionType,
            executionId,
            stageId,
            task.id
          ))
        } else {
          TODO("else what? Nothing to do, just indicate end of stage?")
        }
      }
    }

  private fun StageComplete.handle() =
    withStage { stage ->
      stage.setStatus(status)
      stage.setEndTime(clock.millis())
      repository.storeStage(stage)

      if (status == SUCCEEDED) {
        stage.downstreamStages().forEach {
          eventQ.push(StageStarting(
            executionType,
            executionId,
            it.getId()
          ))
        }
      }

      if (status != SUCCEEDED || stage.getExecution().isComplete()) {
        eventQ.push(ExecutionComplete(
          executionType,
          executionId,
          status
        ))
      }
    }

  private fun TaskStarting.handle() {
    withStage { stage ->
      stage
        .getTasks()
        .find { it.id == taskId }
        ?.let { task ->
          task.status = RUNNING
          task.startTime = clock.millis()
          commandQ.push(Command.RunTask(
            executionType,
            executionId,
            stageId,
            task.id,
            task.implementingClass
          ))
        }
      repository.storeStage(stage)
    }
  }

  private fun TaskComplete.handle() =
    withStage { stage ->
      val task = stage.getTasks().find { it.id == taskId }!!
      task.status = status
      task.endTime = clock.millis()
      repository.storeStage(stage)

      if (status != SUCCEEDED || task.isStageEnd) {
        eventQ.push(StageComplete(
          executionType,
          executionId,
          stageId,
          status
        ))
      } else {
        val index = stage.getTasks().indexOf(task)
        val nextTask = stage.getTasks()[index + 1]
        eventQ.push(TaskStarting(
          executionType,
          executionId,
          stageId,
          nextTask.id
        ))
      }
    }

  private fun ConfigurationError.handle() =
    eventQ.push(ExecutionComplete(
      executionType,
      executionId,
      TERMINAL
    ))

  private fun Execution<*>.initialStages() =
    getStages()
      .filter { it.getRequisiteStageRefIds() == null || it.getRequisiteStageRefIds().isEmpty() }

  // TODO: doesn't handle failure / early termination
  private fun Execution<*>.isComplete() =
    getStages().map { it.getStatus() }.all { it.complete }

  private fun Stage<*>.builder(): StageDefinitionBuilder =
    stageDefinitionBuilders.find { it.type == getType() }
      ?: throw NoSuchStageDefinitionBuilder(getType())

  fun <T : Event> withAck(message: T, handler: T.() -> Unit) {
    message.handler()
    eventQ.ack(message)
  }
}

internal fun StageDefinitionBuilder.buildTasks(stage: Stage<*>) =
  buildTaskGraph(stage)
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
          stage.getTasks().add(task)
        }
        else -> TODO("loops, etc.")
      }
    }
