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

package com.netflix.spinnaker.orca.q

import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.discovery.DiscoveryActivated
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner.NoSuchStageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.Command.RunTask
import com.netflix.spinnaker.orca.q.Event.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

@Component open class ExecutionWorker @Autowired constructor(
  override val commandQ: CommandQueue,
  override val eventQ: EventQueue,
  override val repository: ExecutionRepository,
  val clock: Clock,
  val stageDefinitionBuilders: Collection<StageDefinitionBuilder>
) : DiscoveryActivated, QueueProcessor {

  override val log: Logger = getLogger(javaClass)
  override val enabled = AtomicBoolean(false)

  @Scheduled(fixedDelay = 10)
  fun pollOnce() =
    ifEnabled {
      val event = eventQ.poll()
      if (event != null) log.info("Received event $event")
      when (event) {
        null -> log.debug("No events") // TODO: DLQ
        is TaskStarting -> event.withAck(this::handle)
        is TaskComplete -> event.withAck(this::handle)
        is StageStarting -> event.withAck(this::handle)
        is StageComplete -> event.withAck(this::handle)
        is ExecutionStarting -> event.withAck(this::handle)
        is ExecutionComplete -> event.withAck(this::handle)
        is ConfigurationError -> event.withAck(this::handle)
        else -> TODO("remaining message types")
      }
    }

  private fun handle(event: ExecutionStarting) =
    event.withExecution { execution ->
      repository.updateStatus(event.executionId, RUNNING)

      execution
        .initialStages()
        .forEach {
          eventQ.push(StageStarting(event, it.getId()))
        }
    }

  private fun handle(event: ExecutionComplete) =
    repository.updateStatus(event.executionId, event.status)

  private fun handle(event: StageStarting) =
    event.withStage { stage ->
      stage.builder().let { builder ->
        builder.buildTasks(stage)
        builder.buildSyntheticStages(stage) {
          stage.getExecution().update()
        }
      }

      stage.setStatus(RUNNING)
      stage.setStartTime(clock.millis())
      repository.storeStage(stage)

      stage.firstBeforeStage().let { beforeStage ->
        if (beforeStage == null) {
          stage.firstTask().let { task ->
            if (task == null) {
              TODO("do what? Nothing to do, just indicate end of stage?")
            } else {
              eventQ.push(TaskStarting(event, task.id))
            }
          }
        } else {
          eventQ.push(StageStarting(event, beforeStage.getId()))
        }
      }
    }

  private fun handle(event: StageComplete) =
    event.withStage { stage ->
      stage.setStatus(event.status)
      stage.setEndTime(clock.millis())
      repository.storeStage(stage)

      if (event.status == SUCCEEDED) {
        val downstreamStages = stage.downstreamStages()
        if (downstreamStages.isNotEmpty()) {
          downstreamStages.forEach {
            // TODO: only if other upstreams are complete
            eventQ.push(StageStarting(event, it.getId()))
          }
        } else if (stage.getSyntheticStageOwner() == STAGE_BEFORE) {
          // TODO: this is kinda messy
          stage.parent()!!.let { parent ->
            eventQ.push(TaskStarting(event, parent.getId(), parent.getTasks().first().id))
          }
        } else if (stage.getSyntheticStageOwner() == STAGE_AFTER) {
          // TODO: this is kinda messy
          stage.parent()!!.let { parent ->
            eventQ.push(StageComplete(event, parent.getId(), SUCCEEDED))
          }
        }
      }

      if (event.status != SUCCEEDED || stage.getExecution().isComplete()) {
        eventQ.push(ExecutionComplete(event, event.status))
      }
    }

  private fun handle(event: TaskStarting) {
    event.withStage { stage ->
      val task = stage.task(event.taskId)
      task.status = RUNNING
      task.startTime = clock.millis()
      repository.storeStage(stage)

      commandQ.push(RunTask(event, task.id, task.implementingClass))
    }
  }

  private fun handle(event: TaskComplete) =
    event.withStage { stage ->
      val task = stage.task(event.taskId)
      task.status = event.status
      task.endTime = clock.millis()
      repository.storeStage(stage)

      if (event.status != SUCCEEDED) {
        eventQ.push(StageComplete(event, event.status))
      } else if (!task.isStageEnd) {
        stage.nextTask(task)!!.let {
          eventQ.push(TaskStarting(event, it.id))
        }
      } else {
        val afterStage = stage.firstAfterStage()
        if (afterStage == null) {
          eventQ.push(StageComplete(event, event.status))
        } else {
          eventQ.push(StageStarting(event, afterStage.getId()))
        }
      }
    }

  private fun handle(event: ConfigurationError) =
    eventQ.push(ExecutionComplete(event, TERMINAL))

  private fun Stage<*>.builder(): StageDefinitionBuilder =
    stageDefinitionBuilders.find { it.type == getType() }
      ?: throw NoSuchStageDefinitionBuilder(getType())

  private fun <T : Event> T.withAck(handler: (T) -> Unit) {
    handler(this)
    eventQ.ack(this)
  }
}
