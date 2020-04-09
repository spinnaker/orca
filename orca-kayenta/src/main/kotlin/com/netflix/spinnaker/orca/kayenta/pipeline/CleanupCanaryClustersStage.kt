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

package com.netflix.spinnaker.orca.kayenta.pipeline

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.api.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.DisableClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ShrinkClusterStage
import com.netflix.spinnaker.orca.kayenta.ServerGroupSpec
import com.netflix.spinnaker.orca.kayenta.cluster
import com.netflix.spinnaker.orca.kayenta.deployments
import com.netflix.spinnaker.orca.kayenta.regions
import com.netflix.spinnaker.orca.pipeline.WaitStage
import org.springframework.stereotype.Component

@Component
class CleanupCanaryClustersStage : StageDefinitionBuilder {
  companion object {
    @JvmStatic
    val STAGE_TYPE = "cleanupCanaryClusters"
  }

  override fun beforeStages(parent: StageExecution, graph: StageGraphBuilder) {
    val deployments = parent.deployments

    val disableStages = deployments.serverGroupPairs.flatMap { pair ->
      listOf(
        graph.add {
          it.type = DisableClusterStage.STAGE_TYPE
          it.name = "Disable control cluster ${pair.control.cluster}"
          it.context.putAll(pair.control.toContext)
          it.context["remainingEnabledServerGroups"] = 0
          // Even if disabling fails, move on to shrink below which will have another go at destroying the server group
          it.continuePipelineOnFailure = true
        },
        graph.add {
          it.type = DisableClusterStage.STAGE_TYPE
          it.name = "Disable experiment cluster ${pair.experiment.cluster}"
          it.context.putAll(pair.experiment.toContext)
          it.context["remainingEnabledServerGroups"] = 0
          // Even if disabling fails, move on to shrink below which will have another go at destroying the server group
          it.continuePipelineOnFailure = true
        }
      )
    }

    // wait to allow time for manual inspection
    val waitStage = graph.add {
      it.type = WaitStage.STAGE_TYPE
      it.name = "Wait before cleanup"
      it.context["waitTime"] = deployments.delayBeforeCleanup.seconds
    }

    disableStages.forEach {
      // Continue the stages even if disabling one of the clusters fails - subsequent stages will delete them
      // but we need to make sure they run
      it.allowSiblingStagesToContinueOnFailure = true
      graph.connect(it, waitStage)
    }

    deployments.serverGroupPairs.forEach { pair ->
      // destroy control cluster
      graph.connect(waitStage) {
        it.type = ShrinkClusterStage.STAGE_TYPE
        it.name = "Cleanup control cluster ${pair.control.cluster}"
        it.context.putAll(pair.control.toContext)
        it.context["allowDeleteActive"] = true
        it.context["shrinkToSize"] = 0
        it.continuePipelineOnFailure = true
      }

      // destroy experiment cluster
      graph.connect(waitStage) {
        it.type = ShrinkClusterStage.STAGE_TYPE
        it.name = "Cleanup experiment cluster ${pair.experiment.cluster}"
        it.context.putAll(pair.experiment.toContext)
        it.context["allowDeleteActive"] = true
        it.context["shrinkToSize"] = 0
        it.continuePipelineOnFailure = true
      }
    }
  }

  private val ServerGroupSpec.toContext
    get() = mapOf(
      "cloudProvider" to cloudProvider,
      "credentials" to account,
      "cluster" to cluster,
      "moniker" to moniker,
      "regions" to regions
    )
}
