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

import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.q.event.ExecutionEvent.StageComplete
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock

@Component
open class CompleteStageHandler
@Autowired constructor(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  private val publisher: ApplicationEventPublisher,
  private val clock: Clock
) : MessageHandler<CompleteStage>, QueueProcessor {

  override fun handle(message: CompleteStage) {
    message.withStage { stage ->
      stage.setStatus(message.status)
      stage.setEndTime(clock.millis())
      repository.storeStage(stage)

      if (message.status == SUCCEEDED) {
        stage.startNext()
      } else {
        if (stage.getSyntheticStageOwner() == null) {
          queue.push(CompleteExecution(message, message.status))
        } else {
          queue.push(CompleteStage(message, stage.getParentStageId(), message.status))
        }
      }
    }

    publisher.publishEvent(StageComplete(this, message))
  }

  override val messageType = CompleteStage::class.java

  private fun Stage<*>.startNext() {
    val downstreamStages = downstreamStages()
    if (downstreamStages.isNotEmpty()) {
      downstreamStages.forEach {
        queue.push(StartStage(getExecution().javaClass, getExecution().getId(), getExecution().getApplication(), it.getId()))
      }
    } else if (getSyntheticStageOwner() == STAGE_BEFORE) {
      parent().let { parent ->
        if (parent.allBeforeStagesComplete()) {
          queue.push(StartTask(getExecution().javaClass, getExecution().getId(), getExecution().getApplication(), parent.getId(), parent.getTasks().first().id))
        }
      }
    } else if (getSyntheticStageOwner() == STAGE_AFTER) {
      parent().let { parent ->
        queue.push(CompleteStage(getExecution().javaClass, getExecution().getId(), getExecution().getApplication(), parent.getId(), SUCCEEDED))
      }
    } else {
      queue.push(CompleteExecution(getExecution().javaClass, getExecution().getId(), getExecution().getApplication(), SUCCEEDED))
    }
  }
}
