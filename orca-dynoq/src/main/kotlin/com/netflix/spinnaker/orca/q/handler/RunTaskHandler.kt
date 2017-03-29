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

package com.netflix.spinnaker.orca.q.handler

import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.Message
import com.netflix.spinnaker.orca.q.Message.ConfigurationError.InvalidTaskType
import com.netflix.spinnaker.orca.q.Message.RunTask
import com.netflix.spinnaker.orca.q.MessageHandler
import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.push
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

@Component
open class RunTaskHandler
@Autowired constructor(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  val tasks: Collection<Task>
) : MessageHandler<RunTask> {

  private val log: Logger = getLogger(javaClass)

  override fun handle(message: RunTask) {
    message.withTask { stage, task ->
      if (stage.getExecution().getStatus().complete) {
        queue.push(Message.TaskComplete(message, CANCELED))
      } else {
        try {
          task.execute(stage).let { result ->
            // TODO: rather send this data with TaskComplete message
            stage.processTaskOutput(result)
            when (result.status) {
            // TODO: handle other states such as cancellation, suspension, etc.
              RUNNING ->
                queue.push(message, task.backoffPeriod())
              SUCCEEDED, TERMINAL, REDIRECT ->
                queue.push(Message.TaskComplete(message, result.status))
              else -> TODO()
            }
          }
        } catch(e: Exception) {
          log.error("Error running ${message.taskType.simpleName} for ${message.executionType.simpleName}[${message.executionId}]", e)
          // TODO: add context
          queue.push(Message.TaskComplete(message, TERMINAL))
        }
      }
    }
  }

  override val messageType
    get() = RunTask::class.java

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
      is RetryableTask -> Pair(backoffPeriod, MILLISECONDS)
      else -> Pair(1, SECONDS)
    }

  private fun Stage<*>.processTaskOutput(result: TaskResult) {
    if (result.stageOutputs.isNotEmpty()) {
      getContext().putAll(result.stageOutputs)
      repository.storeStage(this)
    }
    if (result.globalOutputs.isNotEmpty()) {
      getExecution().let { execution ->
        execution.getContext().putAll(result.globalOutputs)
        execution.update() // TODO: optimize to only update context?
      }
    }
  }
}
