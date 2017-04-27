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
  buildParallelStages(stage)
  stage.buildExecutionWindow()

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
      it.setRequisiteStageRefIds(setOf("${stage.getRefId()}<$i"))
    } else {
      it.setRequisiteStageRefIds(emptySet())
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
      it.setRequisiteStageRefIds(setOf("${stage.getRefId()}>$i"))
    } else {
      it.setRequisiteStageRefIds(emptySet())
    }
  }
  stage.getExecution().apply {
    val index = getStages().indexOf(stage) + 1
    afterStages.reversed().forEach {
      addStage(index, it)
    }
  }
}

private fun StageDefinitionBuilder.buildParallelStages(stage: Stage<out Execution<*>>) {
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
        // TODO: this is insane backwards nonsense, it doesn't need the child stage in any impl so we could determine this when building the stage in the first place
        it.setType(getChildStageType(it))
        it.setRefId("${stage.getRefId()}=${i + 1}")
        it.setRequisiteStageRefIds(emptySet())
        stage.getExecution().apply {
          addStage(getStages().indexOf(stage), it)
        }
      }
  }
}

private fun Stage<out Execution<*>>.buildExecutionWindow() {
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
    // inject before the first before stage of this stage, or this stage itself
    val injectBefore = execution.getStages().first { it.getParentStageId() == getId() || it.getId() == getId() }
    if (injectBefore.getSyntheticStageOwner() != null) {
      injectBefore.setRequisiteStageRefIds(setOf(executionWindow.getRefId()))
    }
    execution.addStage(execution.getStages().indexOf(injectBefore), executionWindow)
  }
}

@Suppress("UNCHECKED_CAST")
private fun Execution<*>.addStage(index: Int, it: Stage<*>) {
  when (this) {
    is Pipeline -> stages.add(index, it as Stage<Pipeline>)
    is Orchestration -> stages.add(index, it as Stage<Orchestration>)
  }
}
