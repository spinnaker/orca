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

import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.histogram.BucketCounter
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.exceptions.TimeoutException
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.time.toDuration
import com.netflix.spinnaker.orca.time.toInstant
import org.apache.commons.lang.time.DurationFormatUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Duration.ZERO
import java.time.Instant
import java.time.temporal.TemporalAmount
import java.util.concurrent.TimeUnit

@Component
open class RunTaskHandler
@Autowired constructor(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  override val stageNavigator: StageNavigator,
  override val contextParameterProcessor: ContextParameterProcessor,
  private val tasks: Collection<Task>,
  private val clock: Clock,
  private val exceptionHandlers: List<ExceptionHandler>,
  private val registry: Registry
) : MessageHandler<RunTask>, ExpressionAware, AuthenticationAware {

  private val log: Logger = getLogger(javaClass)

  override fun handle(message: RunTask) {
    message.withTask { stage, taskModel, task ->
      val execution = stage.getExecution()
      try {
        if (execution.isCanceled() || execution.getStatus().isComplete) {
          queue.push(CompleteTask(message, CANCELED))
        } else if (execution.getStatus() == PAUSED) {
          queue.push(PauseTask(message))
        } else {
          task.checkForTimeout(stage, taskModel, message)

          stage.withAuth {
            task.execute(stage.withMergedContext()).let { result: TaskResult ->
              // TODO: rather send this data with CompleteTask message
              stage.processTaskOutput(result)
              when (result.status) {
                RUNNING -> {
                  queue.push(message, task.backoffPeriod(taskModel))
                  trackResult(stage, taskModel, result.status)
                }
                SUCCEEDED, REDIRECT, FAILED_CONTINUE -> {
                  queue.push(CompleteTask(message, result.status))
                  trackResult(stage, taskModel, result.status)
                }
                TERMINAL, CANCELED -> {
                  val status = stage.failureStatus(default = result.status)
                  queue.push(CompleteTask(message, status))
                  trackResult(stage, taskModel, status)
                }
                else ->
                  TODO("Unhandled task status ${result.status}")
              }
            }
          }
        }
      } catch (e: Exception) {
        val exceptionDetails = exceptionHandlers.shouldRetry(e, taskModel.name)
        if (exceptionDetails?.shouldRetry ?: false) {
          log.warn("Error running ${message.taskType.simpleName} for ${message.executionType.simpleName}[${message.executionId}]")
          queue.push(message, task.backoffPeriod(taskModel))
          trackResult(stage, taskModel, RUNNING)
        } else if (e is TimeoutException && stage.getContext()["markSuccessfulOnTimeout"] == true) {
          queue.push(CompleteTask(message, SUCCEEDED))
        } else {
          log.error("Error running ${message.taskType.simpleName} for ${message.executionType.simpleName}[${message.executionId}]", e)
          stage.getContext()["exception"] = exceptionDetails
          repository.storeStage(stage)
          queue.push(CompleteTask(message, stage.failureStatus()))
          trackResult(stage, taskModel, stage.failureStatus())
        }
      }
    }
  }

  private fun trackResult(stage: Stage<*>, taskModel: com.netflix.spinnaker.orca.pipeline.model.Task, status: ExecutionStatus) {
    val id = registry.createId("task.invocations")
      .withTag("status", status.toString())
      .withTag("executionType", stage.getExecution().javaClass.simpleName)
      .withTag("isComplete", status.isComplete.toString())
      .withTag("application", stage.getExecution().getApplication())
      .let { id ->
        stage.getContext()["cloudProvider"]?.let {
          id.withTag("cloudProvider", it.toString())
        } ?: id
      }
    registry.counter(id).increment()

    val distributionId = registry.createId("task.invocations.duration").withTags(id.tags())
    BucketCounter
      .get(registry, distributionId, { v -> bucketDuration(v) })
      .record(System.currentTimeMillis() - taskModel.startTime)
  }

  fun bucketDuration(duration: Long): String {
    return if (duration > TimeUnit.MINUTES.toMillis(60)) {
      "gt60m"
    } else if (duration > TimeUnit.MINUTES.toMillis(30)) {
      "gt30m"
    } else if (duration > TimeUnit.MINUTES.toMillis(15)) {
      "gt15m"
    } else if (duration > TimeUnit.MINUTES.toMillis(5)) {
      "gt5m"
    } else {
      "lt5m"
    }
  }

  override val messageType = RunTask::class.java

  private fun RunTask.withTask(block: (Stage<*>, com.netflix.spinnaker.orca.pipeline.model.Task, Task) -> Unit) =
    withTask { stage, taskModel ->
      tasks
        .find { taskType.isAssignableFrom(it.javaClass) }
        .let { task ->
          if (task == null) {
            queue.push(InvalidTaskType(this, taskType.name))
          } else {
            block.invoke(stage, taskModel, task)
          }
        }
    }

  private fun Task.backoffPeriod(taskModel: com.netflix.spinnaker.orca.pipeline.model.Task): TemporalAmount =
    when (this) {
      is RetryableTask -> Duration.ofMillis(
        getDynamicBackoffPeriod(Duration.ofMillis(System.currentTimeMillis() - taskModel.startTime))
      )
      else -> Duration.ofSeconds(1)
    }

  private fun Task.formatTimeout(timeout: Long): String {
    return DurationFormatUtils.formatDurationWords(timeout, true, true)
  }

  private fun Task.checkForTimeout(stage: Stage<*>, taskModel: com.netflix.spinnaker.orca.pipeline.model.Task, message: Message) {
    if (this is RetryableTask) {
      val startTime = taskModel.startTime.toInstant()
      val pausedDuration = stage.getExecution().pausedDurationRelativeTo(startTime)
      val throttleTime = message.getAttribute<TotalThrottleTimeAttribute>()?.totalThrottleTimeMs ?: 0
      val elapsedTime = Duration.between(startTime, clock.instant())
      if (elapsedTime.minus(pausedDuration).minusMillis(throttleTime) > timeoutDuration(stage)) {
        val durationString = formatTimeout(elapsedTime.toMillis())
        val msg = StringBuilder("${javaClass.simpleName} of stage ${stage.getName()} timed out after $durationString. ")
        msg.append("pausedDuration: ${formatTimeout(pausedDuration.toMillis())}, ")
        msg.append("throttleTime: ${formatTimeout(throttleTime)}, ")
        msg.append("elapsedTime: ${formatTimeout(elapsedTime.toMillis())}")

        log.warn(msg.toString())
        throw TimeoutException(msg.toString())
      }
    }
  }

  private fun RetryableTask.timeoutDuration(stage: Stage<*>): Duration
    = stage.getTopLevelTimeout().orElse(timeout).toDuration()


  private fun Execution<*>.pausedDurationRelativeTo(instant: Instant?): Duration {
    val pausedDetails = getPaused()
    if (pausedDetails != null) {
      return if (pausedDetails.pauseTime.toInstant()?.isAfter(instant) ?: false) {
        Duration.ofMillis(pausedDetails.pausedMs)
      } else ZERO
    } else return ZERO
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
}
