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

import com.netflix.spinnaker.orca.pipeline.BranchingStageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.newStage
import com.netflix.spinnaker.orca.pipeline.TaskNode.TaskDefinition
import com.netflix.spinnaker.orca.pipeline.model.*
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE

/**
 * Build and append the tasks for [stage].
 */
fun StageDefinitionBuilder.buildTasks(stage: Stage<*>) {
  val taskGraph =
    if (this is BranchingStageDefinitionBuilder && stage.getParentStageId() == null) {
      buildPostGraph(stage)
    } else {
      buildTaskGraph(stage)
    }
  taskGraph
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
}

/**
 * Build the synthetic stages for [stage] and inject them into the execution.
 *
 * @param callback invoked if the execution is mutated so calling code can
 * persist the updated execution.
 */
@Suppress("UNCHECKED_CAST")
fun StageDefinitionBuilder.buildSyntheticStages(
  stage: Stage<out Execution<*>>,
  callback: () -> Unit = {}
): Unit {
  val stageCount = stage.getExecution().getStages().size

  syntheticStages(stage).apply {
    buildBeforeStages(stage)
    buildAfterStages(stage)
  }

  if (this is BranchingStageDefinitionBuilder && stage.getParentStageId() == null) {
    stage.setInitializationStage(true)
    eachParallelContext(stage) { context ->
      val execution = stage.getExecution()
      when (execution) {
        is Pipeline -> newStage(execution, stage.getType(), stage.getName(), context, stage as Stage<Pipeline>, STAGE_BEFORE)
        is Orchestration -> newStage(execution, stage.getType(), stage.getName(), context, stage as Stage<Orchestration>, STAGE_BEFORE)
        else -> throw IllegalStateException()
      }
    }
      .forEachIndexed { i, it ->
        it.setRefId("${stage.getRefId()}=${i + 1}")
        stage.getExecution().apply {
          addStage(getStages().indexOf(stage), it)
        }
      }
  }

  if (stage.getExecution().getStages().size > stageCount) {
    callback.invoke()
  }
}

@Suppress("UNCHECKED_CAST")
private fun BranchingStageDefinitionBuilder.eachParallelContext(stage: Stage<*>, block: (Map<String, Any>) -> Stage<*>) =
  when (stage.getExecution()) {
    is Pipeline -> parallelContexts(stage as Stage<Pipeline>)
    is Orchestration -> parallelContexts(stage as Stage<Orchestration>)
    else -> throw IllegalStateException()
  }
    .map(block)

private typealias SyntheticStages = Map<SyntheticStageOwner, List<Stage<*>>>

@Suppress("UNCHECKED_CAST")
private fun StageDefinitionBuilder.syntheticStages(stage: Stage<out Execution<*>>) =
  when (stage.getExecution()) {
    is Pipeline -> aroundStages(stage as Stage<Pipeline>)
    is Orchestration -> aroundStages(stage as Stage<Orchestration>)
    else -> throw IllegalStateException()
  }
    .groupBy { it.getSyntheticStageOwner() }

private fun SyntheticStages.buildBeforeStages(stage: Stage<out Execution<*>>) {
  val beforeStages = this[STAGE_BEFORE].orEmpty()
  beforeStages.forEachIndexed { i, it ->
    it.setRefId("${stage.getRefId()}<${i + 1}")
    if (i > 0) {
      it.setRequisiteStageRefIds(listOf("${stage.getRefId()}<$i"))
    }
    stage.getExecution().apply {
      addStage(getStages().indexOf(stage), it)
    }
  }
}

private fun SyntheticStages.buildAfterStages(stage: Stage<out Execution<*>>) {
  val afterStages = this[STAGE_AFTER].orEmpty()
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
}

@Suppress("UNCHECKED_CAST")
private fun Execution<*>.addStage(index: Int, it: Stage<*>) {
  when (this) {
    is Pipeline -> stages.add(index, it as Stage<Pipeline>)
    is Orchestration -> stages.add(index, it as Stage<Orchestration>)
  }
}
