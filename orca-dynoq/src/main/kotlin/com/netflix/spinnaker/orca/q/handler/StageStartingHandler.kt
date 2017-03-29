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

import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner.NoSuchStageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.q.Message.StageStarting
import com.netflix.spinnaker.orca.q.Message.TaskStarting
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Clock

@Component
open class StageStartingHandler @Autowired constructor(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  val stageDefinitionBuilders: Collection<StageDefinitionBuilder>,
  val clock: Clock
) : MessageHandler<StageStarting> {

  override fun handle(message: StageStarting) {
    message.withStage { stage ->
      if (stage.allUpstreamStagesComplete()) {
        stage.plan()

        stage.setStatus(RUNNING)
        stage.setStartTime(clock.millis())
        repository.storeStage(stage)

        stage.start()
      }
    }
  }

  override val messageType
    get() = StageStarting::class.java

  private fun Stage<*>.plan() {
    builder().let { builder ->
      builder.buildTasks(this)
      builder.buildSyntheticStages(this) {
        getExecution().update()
      }
    }
  }

  private fun Stage<*>.start() {
    firstBeforeStages().let { beforeStages ->
      if (beforeStages.isEmpty()) {
        firstTask().let { task ->
          if (task == null) {
            TODO("do what? Nothing to do, just indicate end of stage?")
          } else {
            queue.push(TaskStarting(getExecution().javaClass, getExecution().getId(), getId(), task.id))
          }
        }
      } else {
        beforeStages.forEach {
          queue.push(StageStarting(getExecution().javaClass, getExecution().getId(), it.getId()))
        }
      }
    }
  }

  private fun Stage<*>.builder(): StageDefinitionBuilder =
    stageDefinitionBuilders.find { it.type == getType() }
      ?: throw NoSuchStageDefinitionBuilder(getType())
}
