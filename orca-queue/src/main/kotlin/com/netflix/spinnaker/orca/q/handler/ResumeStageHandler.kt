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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.PAUSED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.ResumeStage
import com.netflix.spinnaker.orca.q.ResumeTask
import com.netflix.spinnaker.q.Queue
import org.springframework.stereotype.Component

@Component
class ResumeStageHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository
) : OrcaMessageHandler<ResumeStage> {

  override val messageType = ResumeStage::class.java

  override fun handle(message: ResumeStage) {
    message.withStage { stage ->
      stage.status = RUNNING
      repository.storeStage(stage)

      stage
        .tasks
        .filter { it.status == PAUSED }
        .forEach { queue.push(ResumeTask(message, it.id)) }
    }
  }
}
