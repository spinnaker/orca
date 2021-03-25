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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.*
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.IgnoreStageFailure
import com.netflix.spinnaker.orca.q.pending.PendingExecutionService
import com.netflix.spinnaker.q.Queue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class IgnoreStageFailureHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  override val stageDefinitionBuilderFactory: StageDefinitionBuilderFactory,
  private val pendingExecutionService: PendingExecutionService,
  private val clock: Clock
) : OrcaMessageHandler<IgnoreStageFailure>, StageBuilderAware {

  override val messageType = IgnoreStageFailure::class.java

  private val log: Logger get() = LoggerFactory.getLogger(javaClass)

  override fun handle(message: IgnoreStageFailure) {
    message.withStage { stage ->

      if (!stage.status.isHalt) {
        log.warn("Attempting to ignore the failure of stage $stage which is not halted. Will ignore")
      } else if (stage.execution.shouldQueue()) {
        // this pipeline is already running and has limitConcurrent = true
        stage.execution.pipelineConfigId?.let {
          log.info("Queueing IgnoreStageFailure of {} {} {}", stage.execution.application, stage.execution.name, stage.execution.id)
          pendingExecutionService.enqueue(it, message)
        }
      } else {
        stage.status = FAILED_CONTINUE
        stage.addIgnoreFailureDetails(message.user, message.reason)
        repository.storeStage(stage)

        val topLevelStage = stage.topLevelStage
        if (topLevelStage != stage) {
          topLevelStage.status = RUNNING
          repository.storeStage(topLevelStage)
        }

        val execution = topLevelStage.execution
        stage.execution.updateStatus(RUNNING)
        repository.updateStatus(execution)

        stage.startNext()
      }
    }
  }

  private fun StageExecution.addIgnoreFailureDetails(user: String?, reason: String?) {
    context["ignoreFailureDetails"] = mapOf(
      "by" to (user ?: "anonymous"),
      "reason" to (reason ?: "unspecified"),
      "time" to clock.millis(),
      "previousException" to context.remove("exception")
    )
  }
}
