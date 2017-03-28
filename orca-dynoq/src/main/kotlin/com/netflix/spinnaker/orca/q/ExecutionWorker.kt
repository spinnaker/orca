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
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.discovery.DiscoveryActivated
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner.NoSuchStageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.Message.*
import com.netflix.spinnaker.orca.q.Message.ConfigurationError.InvalidTaskType
import com.netflix.spinnaker.orca.q.Message.ConfigurationError.NoDownstreamTasks
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Component open class ExecutionWorker @Autowired constructor(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  val clock: Clock,
  val stageDefinitionBuilders: Collection<StageDefinitionBuilder>,
  val tasks: Collection<Task>
) : DiscoveryActivated, QueueProcessor {

  override val log: Logger = getLogger(javaClass)
  override val enabled = AtomicBoolean(false)

  @Scheduled(fixedDelay = 10)
  fun pollOnce() =
    ifEnabled {
      val message = queue.poll()
      if (message != null) log.info("Received message $message")
      when (message) {
        null -> log.debug("No events") // TODO: DLQ
        is TaskStarting -> message.withAck(this::handle)
        is TaskComplete -> message.withAck(this::handle)
        is RunTask -> message.withAck(this::handle)
        is StageStarting -> message.withAck(this::handle)
        is StageComplete -> message.withAck(this::handle)
        is ExecutionStarting -> message.withAck(this::handle)
        is ExecutionComplete -> message.withAck(this::handle)
        is ConfigurationError -> message.withAck(this::handle)
        else -> TODO("remaining message types")
      }
    }

  private fun handle(message: ExecutionStarting) =
    message.withExecution { execution ->
      repository.updateStatus(message.executionId, RUNNING)

      execution
        .initialStages()
        .forEach {
          queue.push(StageStarting(message, it.getId()))
        }
    }

  private fun handle(message: ExecutionComplete) =
    repository.updateStatus(message.executionId, message.status)

  private fun handle(message: StageStarting) =
    message.withStage { stage ->
      if (stage.allUpstreamStagesComplete()) {
        stage.builder().let { builder ->
          builder.buildTasks(stage)
          builder.buildSyntheticStages(stage) {
            stage.getExecution().update()
          }
        }

        stage.setStatus(RUNNING)
        stage.setStartTime(clock.millis())
        repository.storeStage(stage)

        stage.firstBeforeStages().let { beforeStages ->
          if (beforeStages.isEmpty()) {
            stage.firstTask().let { task ->
              if (task == null) {
                TODO("do what? Nothing to do, just indicate end of stage?")
              } else {
                queue.push(TaskStarting(message, task.id))
              }
            }
          } else {
            beforeStages.forEach {
              queue.push(StageStarting(message, it.getId()))
            }
          }
        }
      }
    }

  private fun handle(message: StageComplete) =
    message.withStage { stage ->
      stage.setStatus(message.status)
      stage.setEndTime(clock.millis())
      repository.storeStage(stage)

      if (message.status == SUCCEEDED) {
        val downstreamStages = stage.downstreamStages()
        if (downstreamStages.isNotEmpty()) {
          downstreamStages.forEach {
            queue.push(StageStarting(message, it.getId()))
          }
        } else if (stage.getSyntheticStageOwner() == STAGE_BEFORE) {
          // TODO: this is kinda messy
          stage.parent()!!.let { parent ->
            if (parent.allBeforeStagesComplete()) {
              queue.push(TaskStarting(message, parent.getId(), parent.getTasks().first().id))
            }
          }
        } else if (stage.getSyntheticStageOwner() == STAGE_AFTER) {
          // TODO: this is kinda messy
          stage.parent()!!.let { parent ->
            queue.push(StageComplete(message, parent.getId(), SUCCEEDED))
          }
        }
      }

      if (message.status != SUCCEEDED || stage.getExecution().isComplete()) {
        queue.push(ExecutionComplete(message, message.status))
      }
    }

  private fun handle(message: TaskStarting) {
    message.withStage { stage ->
      val task = stage.task(message.taskId)
      task.status = RUNNING
      task.startTime = clock.millis()
      repository.storeStage(stage)

      queue.push(RunTask(message, task.id, task.implementingClass))
    }
  }

  private fun handle(message: TaskComplete) =
    message.withStage { stage ->
      val task = stage.task(message.taskId)
      task.status = message.status
      task.endTime = clock.millis()
      repository.storeStage(stage)

      if (message.status == REDIRECT) {
        stage.getTasks().let { tasks ->
          val start = tasks.indexOfFirst { it.isLoopStart }
          val end = tasks.indexOfLast { it.isLoopEnd }
          tasks[start..end].forEach {
            it.endTime = null
            it.status = NOT_STARTED
          }
          repository.storeStage(stage)
          queue.push(TaskStarting(message, tasks[start].id))
        }
      } else if (message.status != SUCCEEDED) {
        queue.push(StageComplete(message, message.status))
      } else if (task.isStageEnd) {
        stage.firstAfterStages().let { afterStages ->
          if (afterStages.isEmpty()) {
            queue.push(StageComplete(message, message.status))
          } else {
            afterStages.forEach {
              queue.push(StageStarting(message, it.getId()))
            }
          }
        }
      } else {
        stage.nextTask(task).let {
          if (it == null) {
            queue.push(NoDownstreamTasks(message))
          } else {
            queue.push(TaskStarting(message, it.id))
          }
        }
      }
    }

  private fun handle(message: RunTask) =
    message.withTask { stage, task ->
      if (stage.getExecution().getStatus().complete) {
        queue.push(TaskComplete(message, CANCELED))
      } else {
        try {
          task.execute(stage).let { result ->
            // TODO: rather do this back in ExecutionWorker
            if (result.stageOutputs.isNotEmpty()) {
              stage.getContext().putAll(result.stageOutputs)
              repository.storeStage(stage)
            }
            if (result.globalOutputs.isNotEmpty()) {
              stage.getExecution().let { execution ->
                execution.getContext().putAll(result.globalOutputs)
                execution.update() // TODO: optimize to only update context?
              }
            }
            when (result.status) {
            // TODO: handle other states such as cancellation, suspension, etc.
              RUNNING ->
                queue.push(message, task.backoffPeriod())
              SUCCEEDED, TERMINAL, REDIRECT ->
                queue.push(TaskComplete(message, result.status))
              else -> TODO()
            }
          }
        } catch(e: Exception) {
          log.error("Error running ${message.taskType.simpleName} for ${message.executionType.simpleName}[${message.executionId}]", e)
          // TODO: add context
          queue.push(TaskComplete(message, TERMINAL))
        }
      }
    }

  private fun handle(message: ConfigurationError) =
    queue.push(ExecutionComplete(message, TERMINAL))

  private fun RunTask.withTask(block: (Stage<*>, Task) -> Unit) =
    withStage { stage ->
      tasks
        .find { taskType.isAssignableFrom(it.javaClass) }
        .let { task ->
          if (task == null) {
            queue.push(InvalidTaskType(this, taskType.name))
          } else {
            block.invoke(stage, task)
          }
        }
    }

  private fun Task.backoffPeriod(): Pair<Long, TimeUnit> =
    when (this) {
      is RetryableTask -> Pair(backoffPeriod, TimeUnit.MILLISECONDS)
      else -> Pair(1, TimeUnit.SECONDS)
    }

  private fun Stage<*>.builder(): StageDefinitionBuilder =
    stageDefinitionBuilders.find { it.type == getType() }
      ?: throw NoSuchStageDefinitionBuilder(getType())
}
