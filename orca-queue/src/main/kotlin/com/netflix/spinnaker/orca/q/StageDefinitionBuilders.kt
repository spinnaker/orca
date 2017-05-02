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
import com.netflix.spinnaker.orca.pipeline.RestrictExecutionDuringTimeWindow
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.newStage
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.TaskNode.TaskDefinition
import com.netflix.spinnaker.orca.pipeline.TaskNode.TaskGraph
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
    .forEachWithMetadata { processTaskNode(stage, it) }
}

private fun processTaskNode(
  stage: Stage<*>,
  element: IteratorElement<TaskNode>,
  isSubGraph: Boolean = false
) {
  element.apply {
    when (value) {
      is TaskDefinition -> {
        val task = Task()
        task.id = (stage.getTasks().size + 1).toString()
        task.name = value.name
        task.implementingClass = value.implementingClass.name
        if (isSubGraph) {
          task.isLoopStart = isFirst
          task.isLoopEnd = isLast
        } else {
          task.isStageStart = isFirst
          task.isStageEnd = isLast
        }
        stage.getTasks().add(task)
      }
      is TaskGraph -> {
        value
          .listIterator()
          .forEachWithMetadata {
            processTaskNode(stage, it, isSubGraph = true)
          }
      }
    }
  }
}

/**
 * Build the synthetic stages for [stage] and inject them into the execution.
 */
fun StageDefinitionBuilder.buildSyntheticStages(
  stage: Stage<out Execution<*>>,
  callback: (Stage<*>) -> Unit = {}
): Unit {
  syntheticStages(stage).apply {
    buildBeforeStages(stage, callback)
    buildAfterStages(stage, callback)
  }
  buildParallelStages(stage, callback)
}

@Suppress("UNCHECKED_CAST")
private fun BranchingStageDefinitionBuilder.parallelContexts(stage: Stage<*>) =
  when (stage.getExecution()) {
    is Pipeline -> parallelContexts(stage as Stage<Pipeline>)
    is Orchestration -> parallelContexts(stage as Stage<Orchestration>)
    else -> throw IllegalStateException()
  }

@Suppress("UNCHECKED_CAST")
private fun BranchingStageDefinitionBuilder.parallelStageName(stage: Stage<*>, hasParallelStages: Boolean) =
  when (stage.getExecution()) {
    is Pipeline -> parallelStageName(stage as Stage<Pipeline>, hasParallelStages)
    is Orchestration -> parallelStageName(stage as Stage<Orchestration>, hasParallelStages)
    else -> throw IllegalStateException()
  }

private typealias SyntheticStages = Map<SyntheticStageOwner, List<Stage<*>>>

@Suppress("UNCHECKED_CAST")
private fun StageDefinitionBuilder.syntheticStages(stage: Stage<out Execution<*>>) =
  when (stage.getExecution()) {
    is Pipeline -> aroundStages(stage as Stage<Pipeline>)
    is Orchestration -> aroundStages(stage as Stage<Orchestration>)
    else -> throw IllegalStateException()
  }
    .groupBy { it.getSyntheticStageOwner() }

private fun SyntheticStages.buildBeforeStages(stage: Stage<out Execution<*>>, callback: (Stage<*>) -> Unit) {
  val executionWindow = stage.buildExecutionWindow()
  val beforeStages = if (executionWindow == null) {
    this[STAGE_BEFORE].orEmpty()
  } else {
    listOf(executionWindow) + this[STAGE_BEFORE].orEmpty()
  }
  beforeStages.forEachIndexed { i, it ->
    it.setRefId("${stage.getRefId()}<${i + 1}")
    if (i > 0) {
      it.setRequisiteStageRefIds(setOf("${stage.getRefId()}<$i"))
    } else {
      it.setRequisiteStageRefIds(emptySet())
    }
    stage.getExecution().apply {
      injectStage(getStages().indexOf(stage), it)
      callback.invoke(it)
    }
  }
}

private fun SyntheticStages.buildAfterStages(stage: Stage<out Execution<*>>, callback: (Stage<*>) -> Unit) {
  val afterStages = this[STAGE_AFTER].orEmpty()
  afterStages.forEachIndexed { i, it ->
    it.setRefId("${stage.getRefId()}>${i + 1}")
    if (i > 0) {
      it.setRequisiteStageRefIds(setOf("${stage.getRefId()}>$i"))
    } else {
      it.setRequisiteStageRefIds(emptySet())
    }
  }
  stage.getExecution().apply {
    val index = getStages().indexOf(stage) + 1
    afterStages.reversed().forEach {
      injectStage(index, it)
      callback.invoke(it)
    }
  }
}

private fun StageDefinitionBuilder.buildParallelStages(stage: Stage<out Execution<*>>, callback: (Stage<*>) -> Unit) {
  if (this is BranchingStageDefinitionBuilder && stage.getParentStageId() == null) {
    val parallelContexts = parallelContexts(stage)
    parallelContexts
      .map { context ->
      val execution = stage.getExecution()
        val stageType = context.getOrDefault("type", stage.getType()).toString()
        val stageName = context.getOrDefault("name", stage.getName()).toString()
        @Suppress("UNCHECKED_CAST")
        when (execution) {
          is Pipeline -> newStage(execution, stageType, stageName, context, stage as Stage<Pipeline>, STAGE_BEFORE)
          is Orchestration -> newStage(execution, stageType, stageName, context, stage as Stage<Orchestration>, STAGE_BEFORE)
          else -> throw IllegalStateException()
        }
    }
      .forEachIndexed { i, it ->
        // TODO: this is insane backwards nonsense, it doesn't need the child stage in any impl so we could determine this when building the stage in the first place
        it.setType(getChildStageType(it))
        it.setRefId("${stage.getRefId()}=${i + 1}")
        it.setRequisiteStageRefIds(emptySet())
        stage.getExecution().apply {
          injectStage(getStages().indexOf(stage), it)
          callback.invoke(it)
        }
      }
    stage.setName(parallelStageName(stage, parallelContexts.size > 1))
    stage.setInitializationStage(true)
  }
}

private fun Stage<out Execution<*>>.buildExecutionWindow(): Stage<*>? {
  if (getContext().getOrDefault("restrictExecutionDuringTimeWindow", false) as Boolean) {
    val execution = getExecution()
    val executionWindow = when (execution) {
      is Pipeline -> newStage(
        execution,
        RestrictExecutionDuringTimeWindow.TYPE,
        RestrictExecutionDuringTimeWindow.TYPE, // TODO: base on stage.name?
        getContext(),
        this as Stage<Pipeline>,
        STAGE_BEFORE
      )
      is Orchestration -> newStage(
        execution,
        RestrictExecutionDuringTimeWindow.TYPE,
        RestrictExecutionDuringTimeWindow.TYPE, // TODO: base on stage.name?
        getContext(),
        this as Stage<Orchestration>,
        STAGE_BEFORE
      )
      else -> throw IllegalStateException()
    }
    executionWindow.setRefId("${getRefId()}<0")
    return executionWindow
  } else {
    return null
  }
}

@Suppress("UNCHECKED_CAST")
private fun Execution<*>.injectStage(index: Int, it: Stage<*>) {
  when (this) {
    is Pipeline -> stages.add(index, it as Stage<Pipeline>)
    is Orchestration -> stages.add(index, it as Stage<Orchestration>)
  }
}
