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

import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.stereotype.Component

@Component open class TaskWorker(
  val commandQ: CommandQueue,
  val eventQ: EventQueue,
  val repository: ExecutionRepository,
  val tasks: Collection<Task>
) {

  fun start(): Unit {
    val command = commandQ.poll()
    if (command != null) {
      execute(command)
    }
  }

  private fun execute(command: Command) =
    taskFor(command) { task ->
      stageFor(command) { stage ->
        task.execute(stage).let { result ->
          when (result.status) {
            SUCCEEDED -> eventQ.push(Event.TaskSucceeded())
            RUNNING -> commandQ.push(command) // TODO: with delay based on task backoff
            else -> TODO()
          }
        }
      }
    }

  private fun taskFor(command: Command, block: (Task) -> Unit) =
    tasks
      .find { command.taskType.isAssignableFrom(it.javaClass) }
      .let { task ->
        if (task == null) {
          eventQ.push(Event.InvalidTaskType())
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
            eventQ.push(Event.InvalidStageId())
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
      eventQ.push(Event.InvalidExecutionId())
    }

}

