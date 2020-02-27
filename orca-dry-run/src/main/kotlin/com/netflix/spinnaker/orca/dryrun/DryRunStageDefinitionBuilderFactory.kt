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

package com.netflix.spinnaker.orca.dryrun

import com.netflix.spinnaker.orca.StageResolver
import com.netflix.spinnaker.orca.pipeline.CheckPreconditionsStage
import com.netflix.spinnaker.orca.pipeline.DefaultStageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl

class DryRunStageDefinitionBuilderFactory(
  stageResolver: StageResolver
) : DefaultStageDefinitionBuilderFactory(stageResolver) {

  override fun builderFor(stage: StageExecutionImpl): StageDefinitionBuilder =
    stage.execution.let { execution ->
      super.builderFor(stage).let {
        if (!execution.trigger.isDryRun || stage.shouldExecuteNormallyInDryRun) {
          it
        } else {
          DryRunStage(it)
        }
      }
    }

  private val StageExecutionImpl.shouldExecuteNormallyInDryRun: Boolean
    get() = isManualJudgment ||
      isPipeline ||
      isExpressionPrecondition ||
      isFindImage ||
      isDetermineTargetServerGroup ||
      isRollbackCluster ||
      isEvalVariables

  private val StageExecutionImpl.isManualJudgment: Boolean
    get() = type == "manualJudgment"

  private val StageExecutionImpl.isPipeline: Boolean
    get() = type == "pipeline"

  private val StageExecutionImpl.isFindImage: Boolean
    get() = type in setOf("findImage", "findImageFromTags")

  private val StageExecutionImpl.isDetermineTargetServerGroup: Boolean
    get() = type == "determineTargetServerGroup"

  private val StageExecutionImpl.isExpressionPrecondition: Boolean
    get() = isPreconditionStage && (isExpressionChild || isExpressionParent)

  private val StageExecutionImpl.isPreconditionStage: Boolean
    get() = type == CheckPreconditionsStage.PIPELINE_CONFIG_TYPE

  private val StageExecutionImpl.isExpressionChild: Boolean
    get() = context["preconditionType"] == "expression"

  @Suppress("UNCHECKED_CAST")
  private val StageExecutionImpl.isExpressionParent: Boolean
    get() = (context["preconditions"] as Iterable<Map<String, Any>>?)?.run {
      all { it["type"] == "expression" }
    } == true

  private val StageExecutionImpl.isRollbackCluster: Boolean
    get() = type == "rollbackCluster"

  private val StageExecutionImpl.isEvalVariables: Boolean
    get() = type == "evaluateVariables"
}
