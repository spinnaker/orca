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

import com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.MessageHandler
import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.RestartStage
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Clock

@Component
open class RestartStageHandler
@Autowired constructor(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  override val stageDefinitionBuilders: Collection<StageDefinitionBuilder>,
  private val clock: Clock
) : MessageHandler<RestartStage>, StageBuilderAware {

  override val messageType = RestartStage::class.java

  override fun handle(message: RestartStage) {
    message.withStage { stage ->
      if (stage.getStatus().complete) {
        stage.addRestartDetails()
        stage.reset()
        repository.updateStatus(stage.getExecution().getId(), RUNNING)
        queue.push(StartStage(message))
      }
    }
  }

  private fun Stage<*>.addRestartDetails() {
    val restartDetails = mutableMapOf(
      "restartedBy" to AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"),
      "restartTime" to clock.millis()
    )
    if (getContext().containsKey("exception")) {
      restartDetails["previousException"] = getContext().remove("exception")
    }
    getContext()["restartDetails"] = restartDetails
  }

  private fun Stage<*>.reset() {
    setStatus(NOT_STARTED)
    setStartTime(null)
    setEndTime(null)
    setTasks(emptyList())
    builder().prepareStageForRestart(repository, this, stageDefinitionBuilders)
    repository.storeStage(this)

    getExecution()
      .getStages()
      .filter { it.getParentStageId() == getId() }
      .forEach {
        repository.removeStage(getExecution(), it.getId())
      }

    downstreamStages().forEach { it.reset() }
  }
}
