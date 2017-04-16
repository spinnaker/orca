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
import com.netflix.spinnaker.orca.q.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.temporal.TemporalAmount

@Component
open class RunTaskHandler
@Autowired constructor(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  val tasks: Collection<Task>
) : MessageHandler<RunTask>, QueueProcessor {

  private val log: Logger = getLogger(javaClass)

  override fun handle(message: RunTask) {
    message.withTask { stage, task ->
      val execution = stage.getExecution()
      if (execution.isCanceled() || execution.getStatus().complete) {
        queue.push(CompleteTask(message, CANCELED))
      } else {
        try {
          task.execute(stage).let { result ->
            // TODO: rather send this data with CompleteTask message
            stage.processTaskOutput(result)
            when (result.status) {
              RUNNING ->
                queue.push(message, task.backoffPeriod())
              SUCCEEDED, REDIRECT ->
                queue.push(CompleteTask(message, result.status))
              TERMINAL ->
                if (!stage.shouldFailPipeline()) {
                  queue.push(CompleteTask(message, STOPPED))
                } else if (stage.shouldContinueOnFailure()) {
                  queue.push(CompleteTask(message, FAILED_CONTINUE))
                } else {
                  queue.push(CompleteTask(message, result.status))
                }
              else ->
                TODO("handle other states such as cancellation, suspension, etc.")
            }
          }
        } catch(e: Exception) {
          log.error("Error running ${message.taskType.simpleName} for ${message.executionType.simpleName}[${message.executionId}]", e)
          // TODO: add context
          queue.push(CompleteTask(message, TERMINAL))
        }
      }
    }
  }

  override val messageType = RunTask::class.java

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

  private fun Task.backoffPeriod(): TemporalAmount =
    when (this) {
      is RetryableTask -> Duration.ofMillis(backoffPeriod)
      else -> Duration.ofSeconds(1)
    }

  private fun Stage<*>.processTaskOutput(result: TaskResult) {
    if (result.stageOutputs.isNotEmpty()) {
      getContext().putAll(result.stageOutputs)
      repository.storeStage(this)
    }
    if (result.globalOutputs.isNotEmpty()) {
      repository.storeExecutionContext(
        getExecution().getId(),
        result.globalOutputs
      )
    }
  }

  private fun Stage<*>.shouldFailPipeline() =
    getContext()["failPipeline"] in listOf(null, true)

  private fun Stage<*>.shouldContinueOnFailure() =
    getContext()["continuePipeline"] == true
}
