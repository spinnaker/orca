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

import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.model.Task

/**
 * @return the initial stages of the execution.
 */
fun Execution<*>.initialStages() =
  getStages()
    .filter { it.getRequisiteStageRefIds().isEmpty() }

/**
 * @return `true` if all stages are complete, `false` otherwise.
 */
// TODO: doesn't handle failure / early termination
fun Execution<*>.isComplete() =
  getStages().map { it.getStatus() }.all { it.complete }

/**
 * @return the stage's first before stage or `null` if there are none.
 */
fun Stage<out Execution<*>>.firstBeforeStage() =
  getExecution()
    .getStages()
    .firstOrNull {
      it.getParentStageId() == getId() && it.getSyntheticStageOwner() == STAGE_BEFORE
    }

/**
 * @return the stage's first after stage or `null` if there are none.
 */
fun Stage<out Execution<*>>.firstAfterStage() =
  getExecution()
    .getStages()
    .firstOrNull {
      it.getParentStageId() == getId() && it.getSyntheticStageOwner() == STAGE_AFTER
    }

/**
 * @return the stage's first task or `null` if there are none.
 */
fun Stage<out Execution<*>>.firstTask() = getTasks().firstOrNull()

/**
 * @return the stage's parent stage or `null` if the stage is not synthetic.
 */
fun Stage<out Execution<*>>.parent() =
  if (getParentStageId() == null) {
    null
  } else {
    getExecution().getStages().find { it.getId() == getParentStageId() }
  }

/**
 * @return the task that follows [task] or `null` if [task] is the end of the
 * stage.
 */
fun Stage<out Execution<*>>.nextTask(task: Task) =
  if (task.isStageEnd) {
    null
  } else {
    val index = getTasks().indexOf(task)
    getTasks()[index + 1]
  }

/**
 * @return the task with the specified id.
 * @throws IllegalArgumentException if there is no such task.
 */
fun Stage<out Execution<*>>.task(taskId: String) =
  getTasks().find { it.id == taskId } ?: throw IllegalArgumentException("No such task")

/**
 * @return the stage with the specified [refId].
 * @throws IllegalArgumentException if there is no such stage.
 */
fun <T : Execution<T>> Execution<T>.stageByRef(refId: String) =
  stages.find { it.refId == refId } ?: throw IllegalArgumentException("No such stage")

/**
 * @return all upstream stages of this stage.
 */
fun Stage<*>.upstreamStages(): List<Stage<*>> =
  getExecution().getStages().filter { it.getRefId() in getRequisiteStageRefIds() }

/**
 * @return `true` if all upstream stages of this stage were run successfully.
 */
fun Stage<*>.allUpstreamStagesComplete(): Boolean =
  // TODO: this needs to cover FAILED_CONTINUE as well
  upstreamStages().all { it.getStatus() == SUCCEEDED }
