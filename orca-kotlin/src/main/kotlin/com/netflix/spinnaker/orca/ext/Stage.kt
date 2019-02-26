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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.model.Task

/**
 * @return the stage's first before stage or `null` if there are none.
 */
fun Stage.firstBeforeStages(): List<Stage> =
  beforeStages().filter { it.isInitial() }

/**
 * @return the stage's first after stage or `null` if there are none.
 */
fun Stage.firstAfterStages(): List<Stage> =
  afterStages().filter { it.isInitial() }

fun Stage.isInitial(): Boolean =
  requisiteStageRefIds.isEmpty()

/**
 * @return the stage's first task or `null` if there are none.
 */
fun Stage.firstTask(): Task? = tasks.firstOrNull()

/**
 * @return the stage's parent stage.
 * @throws IllegalStateException if the stage is not synthetic.
 */
fun Stage.parent(): Stage =
  execution
    .stages
    .find { it.id == parentStageId }
    ?: throw IllegalStateException("Not a synthetic stage")

/**
 * @return the task that follows [task] or `null` if [task] is the end of the
 * stage.
 */
fun Stage.nextTask(task: Task): Task? =
  if (task.isStageEnd) {
    null
  } else {
    val index = tasks.indexOf(task)
    tasks[index + 1]
  }

/**
 * @return all stages directly upstream of this stage.
 */
fun Stage.upstreamStages(): List<Stage> =
  execution.stages.filter { it.refId in requisiteStageRefIds }

/**
 * @return `true` if all upstream stages of this stage were run successfully.
 */
fun Stage.allUpstreamStagesComplete(): Boolean =
  upstreamStages().all { it.status in listOf(SUCCEEDED, FAILED_CONTINUE, SKIPPED) }

fun Stage.anyUpstreamStagesFailed(): Boolean =
  upstreamStages().any { it.status in listOf(TERMINAL, STOPPED, CANCELED) || it.status == NOT_STARTED && it.anyUpstreamStagesFailed() }

fun Stage.syntheticStages(): List<Stage> =
  execution.stages.filter { it.parentStageId == id }

fun Stage.recursiveSyntheticStages(): List<Stage> =
  syntheticStages() + syntheticStages().flatMap {
    it.recursiveSyntheticStages()
  }

fun Stage.beforeStages(): List<Stage> =
  syntheticStages().filter { it.syntheticStageOwner == STAGE_BEFORE }

fun Stage.afterStages(): List<Stage> =
  syntheticStages().filter { it.syntheticStageOwner == STAGE_AFTER }

fun Stage.allBeforeStagesSuccessful(): Boolean =
  beforeStages().all { it.status in listOf(SUCCEEDED, FAILED_CONTINUE, SKIPPED) }

fun Stage.allAfterStagesSuccessful(): Boolean =
  afterStages().all { it.status in listOf(SUCCEEDED, FAILED_CONTINUE, SKIPPED) }

fun Stage.anyBeforeStagesFailed(): Boolean =
  beforeStages().any { it.status in listOf(TERMINAL, STOPPED, CANCELED) }

fun Stage.anyAfterStagesFailed(): Boolean =
  afterStages().any { it.status in listOf(TERMINAL, STOPPED, CANCELED) }

fun Stage.allAfterStagesComplete(): Boolean =
  afterStages().all { it.status.isComplete }

fun Stage.hasTasks(): Boolean =
  tasks.isNotEmpty()

fun Stage.hasAfterStages(): Boolean =
  firstAfterStages().isNotEmpty()

inline fun <reified O> Stage.mapTo(pointer: String): O = mapTo(pointer, O::class.java)

inline fun <reified O> Stage.mapTo(): O = mapTo(O::class.java)

fun Stage.failureStatus(default: ExecutionStatus = TERMINAL): ExecutionStatus {
  // Look for `continuePipeline` or `failPipeline` attributes on parent stages in the event
  // that they were not explicitly passed down as part of the synthetic stage context.
  // Note: a value in a parent stage overrides non-existent value in synthetic child stage
  fun getFromParent(param: String, defaultIfNotPresent: Boolean): Boolean {
    var stage: Stage? = this

    while (stage != null) {
      if (stage.context[param] != null) {
        return (stage.context[param] == true)
      }

      stage = if (stage.syntheticStageOwner != null) stage.parent() else null
    }

    return defaultIfNotPresent
  }

  var shouldContinueOnFailure = getFromParent("continuePipeline", false)
  var shouldFailPipeline = getFromParent("failPipeline", true)

  return when {
    shouldContinueOnFailure -> FAILED_CONTINUE
    shouldFailPipeline -> default
    else -> STOPPED
  }
}

fun Stage.isManuallySkipped(): Boolean {
  return context["manualSkip"] == true || parent?.isManuallySkipped() == true
}
