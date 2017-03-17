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

import com.netflix.spinnaker.orca.Event.*
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
      if (command != null) {
        execute(command)
      }
    }
  }

  // TODO: handle other states such as cancellation, suspension, etc.
  private fun execute(command: Command) =
    taskFor(command) { task ->
      stageFor(command) { stage ->
        try {
          task.execute(stage).let { result ->
            when (result.status) {
              SUCCEEDED -> eventQ.push(TaskSucceeded(command.executionId, command.stageId, command.taskId))
              RUNNING -> commandQ.push(command, task.backoffPeriod())
              TERMINAL -> eventQ.push(TaskFailed(command.executionId, command.stageId, command.taskId))
              else -> TODO()
            }
          }
        } catch(e: Exception) {
          // TODO: add context
          eventQ.push(TaskFailed(command.executionId, command.stageId, command.taskId))
        }
      }
    }

  private fun taskFor(command: Command, block: (Task) -> Unit) =
    tasks
      .find { command.taskType.isAssignableFrom(it.javaClass) }
      .let { task ->
        if (task == null) {
          eventQ.push(InvalidTaskType(command.executionId, command.stageId, command.taskType.name))
        } else {
          block.invoke(task)
        }
      }

  private fun stageFor(command: Command, block: (Stage<out Execution<*>>) -> Unit) =
    executionFor(command) { execution ->
      execution
        .getStages()
        .find { it.getId() == command.stageId }
        .let { stage ->
          if (stage == null) {
            eventQ.push(InvalidStageId(command.executionId, command.stageId))
          } else {
            block.invoke(stage)
          }
        }
    }

  private fun executionFor(command: Command, block: (Execution<*>) -> Unit) =
    try {
      when (command.executionType) {
        Pipeline::class.java -> block.invoke(repository.retrievePipeline(command.executionId))
        Orchestration::class.java -> block.invoke(repository.retrieveOrchestration(command.executionId))
        else -> throw IllegalArgumentException("Unknown execution type ${command.executionType}")
      }
    } catch(e: ExecutionNotFoundException) {
      eventQ.push(InvalidExecutionId(command.executionId))
    }

  private fun Task.backoffPeriod(): Pair<Long, TimeUnit> =
    when (this) {
      is RetryableTask -> Pair(backoffPeriod, MILLISECONDS)
      else -> Pair(1, SECONDS)
    }

  private fun Queue<Command>.push(command: Command, backoffPeriod: Pair<Long, TimeUnit>) =
    push(command, backoffPeriod.first, backoffPeriod.second)
}

