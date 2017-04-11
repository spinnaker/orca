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
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.Message.StageRestarting
import com.netflix.spinnaker.orca.q.Message.StageStarting
import com.netflix.spinnaker.orca.q.MessageHandler
import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.QueueProcessor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
open class StageRestartingHandler
@Autowired constructor(
  override val queue: Queue,
  override val repository: ExecutionRepository
) : MessageHandler<StageRestarting>, QueueProcessor {

  override val messageType = StageRestarting::class.java

  override fun handle(message: StageRestarting) {
    message.withStage { stage ->
      if (stage.getStatus().complete) {

        stage.reset()

        stage.getExecution().let { execution ->
          execution.reset()
          execution.update()
        }

        queue.push(StageStarting(message))
      }
    }
  }

  private fun Stage<*>.reset() {
    setStatus(NOT_STARTED)
    setStartTime(null)
    setEndTime(null)
    setTasks(emptyList())

    getExecution().getStages().removeAll { it.getParentStageId() == getId() }

    downstreamStages().forEach { it.reset() }
  }

  private fun Execution<*>.reset() {
    setStatus(RUNNING)
    setEndTime(null)
  }
}
