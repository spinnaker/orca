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
import com.netflix.spinnaker.orca.events.StageComplete
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.q.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class CompleteStageHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  private val publisher: ApplicationEventPublisher,
  private val clock: Clock,
  override val contextParameterProcessor: ContextParameterProcessor,
  override val stageDefinitionBuilderFactory: StageDefinitionBuilderFactory
) : MessageHandler<CompleteStage>, StageBuilderAware, ExpressionAware {

  override fun handle(message: CompleteStage) {
    message.withStage { stage ->
      if (stage.status in setOf(RUNNING, NOT_STARTED)) {
        val status = stage.determineStatus()
        if (status.isComplete && !status.isHalt) {
          // check to see if this stage has any unplanned synthetic after stages
          var afterStages = stage.firstAfterStages()
          if (afterStages.isEmpty()) {
            stage.planAfterStages()

            afterStages = stage.firstAfterStages()
            if (afterStages.isNotEmpty()) {
              afterStages.forEach {
                queue.push(StartStage(message, it.id))
              }

              return@withStage
            }
          }
        }

        stage.status = status
        stage.endTime = clock.millis()
        stage.includeExpressionEvaluationSummary()
        repository.storeStage(stage)

        if (status in listOf(SUCCEEDED, FAILED_CONTINUE)) {
          stage.startNext()
        } else {
          queue.push(CancelStage(message))
          if (stage.syntheticStageOwner == null) {
            log.debug("Stage has no synthetic owner, completing execution (original message: $message)")
            queue.push(CompleteExecution(message))
          } else {
            queue.push(message.copy(stageId = stage.parentStageId!!))
          }
        }

        publisher.publishEvent(StageComplete(this, stage))
      }
    }
  }

  override val messageType = CompleteStage::class.java

  /**
   * Plan any outstanding synthetic after stages.
   */
  private fun Stage.planAfterStages() {
    var hasPlannedStages = false

    builder().let { builder ->
      builder.buildAfterStages(this, builder.afterStages(this)) { it: Stage ->
        repository.addStage(it)
        hasPlannedStages = true
      }
    }

    if (hasPlannedStages) {
      this.execution = repository.retrieve(this.execution.type, this.execution.id)
    }
  }
}
