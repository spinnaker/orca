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

import com.netflix.spinnaker.orca.ExecutionStatus.PAUSED
import com.netflix.spinnaker.orca.ExecutionStatus.CANCELED
import com.netflix.spinnaker.orca.events.ExecutionComplete
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class CancelExecutionHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  private val publisher: ApplicationEventPublisher
) : MessageHandler<CancelExecution> {
  override val messageType = CancelExecution::class.java

  override fun handle(message: CancelExecution) {
    message.withExecution { execution ->
      repository.cancel(execution.getId(), message.user, message.reason)

      // Resume any paused stages so that their RunTaskHandler gets executed
      // and handles the `canceled` flag.
      execution
        .getStages()
        .filter { it.getStatus() == PAUSED }
        .forEach { stage ->
          queue.push(ResumeStage(stage))
        }

      // then, make sure those runTask messages get run right away
      queue.push(RescheduleExecution(execution))

      publisher.publishEvent(ExecutionComplete(this, message.executionType, message.executionId, CANCELED))
    }
  }
}
