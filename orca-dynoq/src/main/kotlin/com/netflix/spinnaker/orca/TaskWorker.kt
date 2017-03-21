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

import com.netflix.spinnaker.orca.Command.RunTask
import com.netflix.spinnaker.orca.Event.ConfigurationError.*
import com.netflix.spinnaker.orca.Event.TaskComplete
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.discovery.DiscoveryActivated
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

@Component open class TaskWorker(
  val commandQ: CommandQueue,
  val eventQ: EventQueue,
  val repository: ExecutionRepository,
  val tasks: Collection<Task>
) : DiscoveryActivated() {

  @Scheduled(fixedDelay = 10)
  fun pollOnce() {
    ifEnabled {
      val command = commandQ.poll()
      when(command) {
        null -> log.debug("No commands")
        is RunTask -> command.execute()
      }
    }
  }

  // TODO: handle other states such as cancellation, suspension, etc.
  private fun RunTask.execute() =
    withTask { task ->
      withStage { stage ->
        try {
          task.execute(stage).let { result ->
            when (result.status) {
              RUNNING ->
                commandQ.push(this, task.backoffPeriod())
              SUCCEEDED, TERMINAL ->
                eventQ.push(TaskComplete(executionType, executionId, stageId, taskId, result.status))
              else -> TODO()
            }
          }
        } catch(e: Exception) {
          // TODO: add context
          eventQ.push(TaskComplete(executionType, executionId, stageId, taskId, TERMINAL))
        }
      }
    }

  private fun RunTask.withTask(block: (Task) -> Unit) =
    tasks
      .find { taskType.isAssignableFrom(it.javaClass) }
      .let { task ->
        if (task == null) {
          eventQ.push(InvalidTaskType(executionType, executionId, stageId, taskType.name))
        } else {
          block.invoke(task)
        }
      }

  private fun Command.withStage(block: (Stage<out Execution<*>>) -> Unit) =
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

  private fun Command.withExecution(block: (Execution<*>) -> Unit) =
    try {
      when (executionType) {
        Pipeline::class.java -> block.invoke(repository.retrievePipeline(executionId))
        Orchestration::class.java -> block.invoke(repository.retrieveOrchestration(executionId))
        else -> throw IllegalArgumentException("Unknown execution type $executionType")
      }
    } catch(e: ExecutionNotFoundException) {
      eventQ.push(InvalidExecutionId(executionType, executionId))
    }

  private fun Task.backoffPeriod(): Pair<Long, TimeUnit> =
    when (this) {
      is RetryableTask -> Pair(backoffPeriod, MILLISECONDS)
      else -> Pair(1, SECONDS)
    }

  private fun Queue<Command>.push(command: Command, backoffPeriod: Pair<Long, TimeUnit>) =
    push(command, backoffPeriod.first, backoffPeriod.second)
}

