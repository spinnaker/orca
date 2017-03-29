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
import com.netflix.spinnaker.orca.q.Message.*
import com.netflix.spinnaker.orca.q.MessageHandler
import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.allBeforeStagesComplete
import com.netflix.spinnaker.orca.q.parent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Clock

@Component
open class StageCompleteHandler
@Autowired constructor(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  val clock: Clock
) : MessageHandler<StageComplete> {

  override fun handle(message: StageComplete) {
    message.withStage { stage ->
      stage.setStatus(message.status)
      stage.setEndTime(clock.millis())
      repository.storeStage(stage)

      if (message.status == SUCCEEDED) {
        stage.startNext()
      } else {
        if (stage.getSyntheticStageOwner() == null) {
          queue.push(ExecutionComplete(message, message.status))
        } else {
          queue.push(StageComplete(message, stage.getParentStageId(), message.status))
        }
      }
    }
  }

  override val messageType
    get() = StageComplete::class.java

  private fun Stage<*>.startNext() {
    val downstreamStages = downstreamStages()
    if (downstreamStages.isNotEmpty()) {
      downstreamStages.forEach {
        queue.push(StageStarting(getExecution().javaClass, getExecution().getId(), it.getId()))
      }
    } else if (getSyntheticStageOwner() == STAGE_BEFORE) {
      parent().let { parent ->
        if (parent.allBeforeStagesComplete()) {
          queue.push(TaskStarting(getExecution().javaClass, getExecution().getId(), parent.getId(), parent.getTasks().first().id))
        }
      }
    } else if (getSyntheticStageOwner() == STAGE_AFTER) {
      parent().let { parent ->
        queue.push(StageComplete(getExecution().javaClass, getExecution().getId(), parent.getId(), SUCCEEDED))
      }
    } else {
      queue.push(ExecutionComplete(getExecution().javaClass, getExecution().getId(), SUCCEEDED))
    }
  }
}
