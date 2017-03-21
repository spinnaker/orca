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
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.Command.RunTask
import com.netflix.spinnaker.orca.q.Event.ConfigurationError.InvalidTaskType
import com.netflix.spinnaker.orca.q.Event.TaskComplete
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

@Component open class TaskWorker @Autowired constructor(
  override val commandQ: CommandQueue,
  override val eventQ: EventQueue,
  override val repository: ExecutionRepository,
  val tasks: Collection<Task>
) : DiscoveryActivated, QueueProcessor {

  override val log: Logger = getLogger(javaClass)
  override val enabled = AtomicBoolean(false)

  @Scheduled(fixedDelay = 10)
  fun pollOnce() {
    ifEnabled {
      val command = commandQ.poll()
      if (command != null) log.info("Received command $command")
      when (command) {
        null -> log.debug("No commands")
        is RunTask -> command.execute()
      }
    }
  }

  // TODO: handle other states such as cancellation, suspension, etc.
  private fun RunTask.execute() =
    withTask { stage, task ->
      if (stage.getExecution().getStatus().complete) {
        eventQ.push(TaskComplete(executionType, executionId, stageId, taskId, CANCELED))
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
                when (execution) {
                  is Pipeline -> repository.store(execution)
                  is Orchestration -> repository.store(execution)
                }
              }
            }
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

  private fun RunTask.withTask(block: (Stage<*>, Task) -> Unit) =
    withStage { stage ->
      tasks
        .find { taskType.isAssignableFrom(it.javaClass) }
        .let { task ->
          if (task == null) {
            eventQ.push(InvalidTaskType(executionType, executionId, stageId, taskType.name))
          } else {
            block.invoke(stage, task)
          }
        }
    }

  private fun Task.backoffPeriod(): Pair<Long, TimeUnit> =
    when (this) {
      is RetryableTask -> Pair(backoffPeriod, MILLISECONDS)
      else -> Pair(1, SECONDS)
    }

  private fun Queue<Command>.push(command: Command, backoffPeriod: Pair<Long, TimeUnit>) =
    push(command, backoffPeriod.first, backoffPeriod.second)
}

