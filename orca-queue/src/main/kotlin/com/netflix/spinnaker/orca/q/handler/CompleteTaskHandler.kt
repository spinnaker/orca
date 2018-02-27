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
import com.netflix.spinnaker.orca.events.TaskComplete
import com.netflix.spinnaker.orca.ext.firstAfterStages
import com.netflix.spinnaker.orca.ext.nextTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.q.Queue
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class CompleteTaskHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  override val contextParameterProcessor: ContextParameterProcessor,
  @Qualifier("queueEventPublisher") private val publisher: ApplicationEventPublisher,
  private val clock: Clock
) : OrcaMessageHandler<CompleteTask>, ExpressionAware {

  override fun handle(message: CompleteTask) {
    message.withTask { stage, task ->
      task.status = message.status
      task.endTime = clock.millis()
      val mergedContextStage = stage.withMergedContext()

      if (message.status == REDIRECT) {
        mergedContextStage.handleRedirect()
      } else {
        repository.storeStage(mergedContextStage)

        if (message.status != SUCCEEDED) {
          queue.push(CompleteStage(message))
        } else if (task.isStageEnd) {
          mergedContextStage.firstAfterStages().let { afterStages ->
            if (afterStages.isEmpty()) {
              queue.push(CompleteStage(message))
            } else {
              afterStages.forEach {
                queue.push(StartStage(message, it.id))
              }
            }
          }
        } else {
          mergedContextStage.nextTask(task).let {
            if (it == null) {
              queue.push(NoDownstreamTasks(message))
            } else {
              queue.push(StartTask(message, it.id))
            }
          }
        }

        publisher.publishEvent(TaskComplete(this, mergedContextStage, task))
      }
    }
  }

  override val messageType = CompleteTask::class.java

  private fun Stage.handleRedirect() {
    tasks.let { tasks ->
      val start = tasks.indexOfFirst { it.isLoopStart }
      val end = tasks.indexOfLast { it.isLoopEnd }
      tasks[start..end].forEach {
        it.endTime = null
        it.status = NOT_STARTED
      }
      repository.storeStage(this)
      queue.push(StartTask(execution.type, execution.id, execution.application, id, tasks[start].id))
    }
  }
}
