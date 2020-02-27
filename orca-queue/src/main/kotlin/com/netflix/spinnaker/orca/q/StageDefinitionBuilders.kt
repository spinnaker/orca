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

import com.netflix.spinnaker.orca.pipeline.RestrictExecutionDuringTimeWindow
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.newStage
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.TaskNode.TaskDefinition
import com.netflix.spinnaker.orca.pipeline.TaskNode.TaskGraph
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.model.TaskExecutionImpl

/**
 * Build and append the tasks for [stage].
 */
fun StageDefinitionBuilder.buildTasks(stage: StageExecutionImpl) {
  buildTaskGraph(stage)
    .listIterator()
    .forEachWithMetadata { processTaskNode(stage, it) }
}

fun StageDefinitionBuilder.addContextFlags(stage: StageExecutionImpl) {
  if (canManuallySkip()) {
    // Provides a flag for the UI to indicate that the stage can be skipped.
    stage.context["canManuallySkip"] = true
  }
}

private fun processTaskNode(
  stage: StageExecutionImpl,
  element: IteratorElement<TaskNode>,
  isSubGraph: Boolean = false
) {
  element.apply {
    when (value) {
      is TaskDefinition -> {
        val task = TaskExecutionImpl()
        task.id = (stage.tasks.size + 1).toString()
        task.name = value.name
        task.implementingClass = value.implementingClass.name
        if (isSubGraph) {
          task.isLoopStart = isFirst
          task.isLoopEnd = isLast
        } else {
          task.isStageStart = isFirst
          task.isStageEnd = isLast
        }
        stage.tasks.add(task)
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
fun StageDefinitionBuilder.buildBeforeStages(
  stage: StageExecutionImpl,
  callback: (StageExecutionImpl) -> Unit = {}
) {
  val executionWindow = stage.buildExecutionWindow()

  val graph = StageGraphBuilder.beforeStages(stage, executionWindow)
  beforeStages(stage, graph)
  val beforeStages = graph.build().toList()

  stage.execution.apply {
    beforeStages.forEach {
      it.sanitizeContext()
      injectStage(stages.indexOf(stage), it)
      callback.invoke(it)
    }
  }
}

fun StageDefinitionBuilder.buildAfterStages(
  stage: StageExecutionImpl,
  callback: (StageExecutionImpl) -> Unit = {}
) {
  val graph = StageGraphBuilder.afterStages(stage)
  afterStages(stage, graph)
  val afterStages = graph.build().toList()

  stage.appendAfterStages(afterStages, callback)
}

fun StageDefinitionBuilder.buildFailureStages(
  stage: StageExecutionImpl,
  callback: (StageExecutionImpl) -> Unit = {}
) {
  val graph = StageGraphBuilder.afterStages(stage)
  onFailureStages(stage, graph)
  val afterStages = graph.build().toList()

  stage.appendAfterStages(afterStages, callback)
}

fun StageExecutionImpl.appendAfterStages(
  afterStages: Iterable<StageExecutionImpl>,
  callback: (StageExecutionImpl) -> Unit = {}
) {
  val index = execution.stages.indexOf(this) + 1
  afterStages.reversed().forEach {
    it.sanitizeContext()
    execution.injectStage(index, it)
    callback.invoke(it)
  }
}

private typealias SyntheticStages = Map<SyntheticStageOwner, List<StageExecutionImpl>>

private fun StageExecutionImpl.buildExecutionWindow(): StageExecutionImpl? {
  if (context.getOrDefault("restrictExecutionDuringTimeWindow", false) as Boolean) {
    val execution = execution
    val executionWindow = newStage(
      execution,
      RestrictExecutionDuringTimeWindow.TYPE,
      RestrictExecutionDuringTimeWindow.TYPE,
      context.filterKeys { it !in setOf("restrictExecutionDuringTimeWindow", "stageTimeoutMs") },
      this,
      STAGE_BEFORE
    )
    executionWindow.refId = "$refId<0"
    return executionWindow
  } else {
    return null
  }
}

@Suppress("UNCHECKED_CAST")
private fun PipelineExecutionImpl.injectStage(index: Int, stage: StageExecutionImpl) {
  stages.add(index, stage)
}

private fun StageExecutionImpl.sanitizeContext() {
  if (type != RestrictExecutionDuringTimeWindow.TYPE) {
    context.apply {
      remove("restrictExecutionDuringTimeWindow")
      remove("restrictedExecutionWindow")
    }
  }
}
