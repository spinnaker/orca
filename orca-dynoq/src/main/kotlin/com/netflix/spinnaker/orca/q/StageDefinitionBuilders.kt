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

import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode.TaskDefinition
import com.netflix.spinnaker.orca.pipeline.model.*
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE

/**
 * Build and append the tasks for [stage].
 */
fun StageDefinitionBuilder.buildTasks(stage: Stage<*>) =
  buildTaskGraph(stage)
    .listIterator()
    .forEachWithMetadata { (taskNode, index, isFirst, isLast) ->
      when (taskNode) {
        is TaskDefinition -> {
          val task = DefaultTask()
          task.id = (index + 1).toString()
          task.name = taskNode.name
          task.implementingClass = taskNode.implementingClass
          task.stageStart = isFirst
          task.stageEnd = isLast
          stage.getTasks().add(task)
        }
        else -> TODO("loops, etc.")
      }
    }

/**
 * Build the synthetic stages for [stage] and inject them into the execution.
 *
 * @param callback invoked if the execution is mutated so calling code can
 * persist the updated execution.
 */
fun StageDefinitionBuilder.buildSyntheticStages(
  stage: Stage<out Execution<*>>,
  callback: () -> Unit = {}
): Unit {
  val syntheticStages = syntheticStages(stage)

  val beforeStages = syntheticStages[STAGE_BEFORE].orEmpty()
  beforeStages.forEachIndexed { i, it ->
    it.setRefId("${stage.getRefId()}<${i + 1}")
    if (i > 0) {
      it.setRequisiteStageRefIds(listOf("${stage.getRefId()}<$i"))
    }
    stage.getExecution().apply {
      addStage(getStages().indexOf(stage), it)
    }
  }

  val afterStages = syntheticStages[STAGE_AFTER].orEmpty()
  afterStages.forEachIndexed { i, it ->
    it.setRefId("${stage.getRefId()}>${i + 1}")
    if (i > 0) {
      it.setRequisiteStageRefIds(listOf("${stage.getRefId()}>$i"))
    }
  }
  stage.getExecution().apply {
    val index = getStages().indexOf(stage) + 1
    afterStages.reversed().forEach {
      addStage(index, it)
    }
  }

  if (syntheticStages.isNotEmpty()) {
    callback.invoke()
  }
}

@Suppress("UNCHECKED_CAST")
private fun StageDefinitionBuilder.syntheticStages(
  stage: Stage<out Execution<*>>
): Map<SyntheticStageOwner, List<Stage<*>>> {
  val execution = stage.getExecution()
  return when (execution) {
    is Pipeline -> aroundStages(stage as Stage<Pipeline>)
    is Orchestration -> aroundStages(stage as Stage<Orchestration>)
    else -> throw IllegalStateException()
  }
    .groupBy { it.getSyntheticStageOwner() }
}

@Suppress("UNCHECKED_CAST")
private fun Execution<*>.addStage(index: Int, it: Stage<*>) {
  when (this) {
    is Pipeline -> stages.add(index, it as Stage<Pipeline>)
    is Orchestration -> stages.add(index, it as Stage<Orchestration>)
  }
}
