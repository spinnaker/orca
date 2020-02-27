/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.ext

import com.netflix.spinnaker.orca.api.ExecutionStatus
import com.netflix.spinnaker.orca.api.ExecutionStatus.CANCELED
import com.netflix.spinnaker.orca.api.ExecutionStatus.FAILED_CONTINUE
import com.netflix.spinnaker.orca.api.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.api.ExecutionStatus.SKIPPED
import com.netflix.spinnaker.orca.api.ExecutionStatus.STOPPED
import com.netflix.spinnaker.orca.api.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.api.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.model.TaskExecutionImpl

/**
 * @return the stage's first before stage or `null` if there are none.
 */
fun StageExecutionImpl.firstBeforeStages(): List<StageExecutionImpl> =
  beforeStages().filter { it.isInitial() }

/**
 * @return the stage's first after stage or `null` if there are none.
 */
fun StageExecutionImpl.firstAfterStages(): List<StageExecutionImpl> =
  afterStages().filter { it.isInitial() }

fun StageExecutionImpl.isInitial(): Boolean =
  requisiteStageRefIds.isEmpty()

/**
 * @return the stage's first task or `null` if there are none.
 */
fun StageExecutionImpl.firstTask(): TaskExecutionImpl? = tasks.firstOrNull()

/**
 * @return the stage's parent stage.
 * @throws IllegalStateException if the stage is not synthetic.
 */
fun StageExecutionImpl.parent(): StageExecutionImpl =
  execution
    .stages
    .find { it.id == parentStageId }
    ?: throw IllegalStateException("Not a synthetic stage")

/**
 * @return the task that follows [task] or `null` if [task] is the end of the
 * stage.
 */
fun StageExecutionImpl.nextTask(task: TaskExecutionImpl): TaskExecutionImpl? =
  if (task.isStageEnd) {
    null
  } else {
    val index = tasks.indexOf(task)
    tasks[index + 1]
  }

/**
 * @return all stages directly upstream of this stage.
 */
fun StageExecutionImpl.upstreamStages(): List<StageExecutionImpl> =
  execution.stages.filter { it.refId in requisiteStageRefIds }

/**
 * @return `true` if all upstream stages of this stage were run successfully.
 */
fun StageExecutionImpl.allUpstreamStagesComplete(): Boolean =
  upstreamStages().all { it.status in listOf(SUCCEEDED, FAILED_CONTINUE, SKIPPED) }

fun StageExecutionImpl.anyUpstreamStagesFailed(): Boolean =
  upstreamStages().any { it.status in listOf(TERMINAL, STOPPED, CANCELED) || it.status == NOT_STARTED && it.anyUpstreamStagesFailed() }

fun StageExecutionImpl.syntheticStages(): List<StageExecutionImpl> =
  execution.stages.filter { it.parentStageId == id }

fun StageExecutionImpl.recursiveSyntheticStages(): List<StageExecutionImpl> =
  syntheticStages() + syntheticStages().flatMap {
    it.recursiveSyntheticStages()
  }

fun StageExecutionImpl.beforeStages(): List<StageExecutionImpl> =
  syntheticStages().filter { it.syntheticStageOwner == STAGE_BEFORE }

fun StageExecutionImpl.afterStages(): List<StageExecutionImpl> =
  syntheticStages().filter { it.syntheticStageOwner == STAGE_AFTER }

fun StageExecutionImpl.allBeforeStagesSuccessful(): Boolean =
  beforeStages().all { it.status in listOf(SUCCEEDED, FAILED_CONTINUE, SKIPPED) }

fun StageExecutionImpl.allAfterStagesSuccessful(): Boolean =
  afterStages().all { it.status in listOf(SUCCEEDED, FAILED_CONTINUE, SKIPPED) }

fun StageExecutionImpl.anyBeforeStagesFailed(): Boolean =
  beforeStages().any { it.status in listOf(TERMINAL, STOPPED, CANCELED) }

fun StageExecutionImpl.anyAfterStagesFailed(): Boolean =
  afterStages().any { it.status in listOf(TERMINAL, STOPPED, CANCELED) }

fun StageExecutionImpl.allAfterStagesComplete(): Boolean =
  afterStages().all { it.status.isComplete }

fun StageExecutionImpl.hasTasks(): Boolean =
  tasks.isNotEmpty()

fun StageExecutionImpl.hasAfterStages(): Boolean =
  firstAfterStages().isNotEmpty()

inline fun <reified O> StageExecutionImpl.mapTo(pointer: String): O = mapTo(pointer, O::class.java)

inline fun <reified O> StageExecutionImpl.mapTo(): O = mapTo(O::class.java)

fun StageExecutionImpl.shouldFailPipeline(): Boolean =
  context["failPipeline"] in listOf(null, true)

fun StageExecutionImpl.failureStatus(default: ExecutionStatus = TERMINAL) =
  when {
    continuePipelineOnFailure -> FAILED_CONTINUE
    shouldFailPipeline() -> default
    else -> STOPPED
  }

fun StageExecutionImpl.isManuallySkipped(): Boolean {
  return context["manualSkip"] == true || parent?.isManuallySkipped() == true
}
