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

import com.google.common.base.CaseFormat
import com.netflix.spinnaker.orca.TaskResolver
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.SkippableTask
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SKIPPED
import com.netflix.spinnaker.orca.api.pipeline.models.TaskExecution
import com.netflix.spinnaker.orca.events.TaskComplete
import com.netflix.spinnaker.orca.events.TaskStarted
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.q.CompleteTask
import com.netflix.spinnaker.orca.q.RunTask
import com.netflix.spinnaker.orca.q.StartTask
import com.netflix.spinnaker.q.Queue
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class StartTaskHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  override val contextParameterProcessor: ContextParameterProcessor,
  override val stageDefinitionBuilderFactory: StageDefinitionBuilderFactory,
  @Qualifier("queueEventPublisher") private val publisher: ApplicationEventPublisher,
  private val taskResolver: TaskResolver,
  private val clock: Clock,
  private val environment: Environment
) : OrcaMessageHandler<StartTask>, ExpressionAware {

  override fun handle(message: StartTask) {
    message.withTask { stage, task ->
      if (isTaskSkippable(task) && !isTaskEnabled(task)) {
        log.debug("Skipping task.type=${task.type} because ${getTaskToggle(task)}=false")
        task.status = SKIPPED
        val mergedContextStage = stage.withMergedContext()
        repository.storeStage(mergedContextStage)

        queue.push(CompleteTask(message, SKIPPED))
        publisher.publishEvent(TaskComplete(this, mergedContextStage, task))
      } else {
        task.status = RUNNING
        task.startTime = clock.millis()
        val mergedContextStage = stage.withMergedContext()
        repository.storeStage(mergedContextStage)

        queue.push(RunTask(message, task.id, task.type))
        publisher.publishEvent(TaskStarted(this, mergedContextStage, task))
      }
    }
  }

  fun getTaskToggle(task: TaskExecution): String {
    val justTheClassName = task.implementingClass.substring(task.implementingClass.lastIndexOf('.') + 1)
    val snakifiedClassName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, justTheClassName)
    return "tasks.$snakifiedClassName.enabled"
  }

  fun isTaskSkippable(task: TaskExecution): Boolean =
    SkippableTask::class.java.isAssignableFrom(task.type)

  fun isTaskEnabled(task: TaskExecution): Boolean =
    environment.getProperty(getTaskToggle(task), Boolean::class.java, true)

  override val messageType = StartTask::class.java

  @Suppress("UNCHECKED_CAST")
  private val TaskExecution.type
    get() = taskResolver.getTaskClass(implementingClass)
}
