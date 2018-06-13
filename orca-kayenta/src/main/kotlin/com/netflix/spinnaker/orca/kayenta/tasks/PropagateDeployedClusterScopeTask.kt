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

package com.netflix.spinnaker.orca.kayenta.tasks

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.ext.mapTo
import com.netflix.spinnaker.orca.kayenta.model.deployments
import com.netflix.spinnaker.orca.kayenta.model.locations
import com.netflix.spinnaker.orca.kayenta.pipeline.DeployCanaryClustersStage.Companion.DEPLOY_CONTROL_CLUSTER
import com.netflix.spinnaker.orca.kayenta.pipeline.DeployCanaryClustersStage.Companion.DEPLOY_EXPERIMENT_CLUSTER
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component

@Component
class PropagateDeployedClusterScopeTask : Task {
  override fun execute(stage: Stage): TaskResult {
    val deployedCanaryClusters = mutableMapOf<String, String>()
    stage.withFirstChildOf(DEPLOY_CONTROL_CLUSTER) {
      val location = stage.deployments.control.locations.first()
      val serverGroup =
        it.mapTo<DeployServerGroupContext>().deployServerGroups[location]
          ?.first()
          ?: throw IllegalStateException("Could not find control server group.")

      deployedCanaryClusters.putAll(mapOf(
        "controlLocation" to location,
        "controlServerGroup" to serverGroup
      ))
    }

    stage.withFirstChildOf(DEPLOY_EXPERIMENT_CLUSTER) {
      val location = stage.deployments.experiment.locations.first()
      val serverGroup  =
        it.mapTo<DeployServerGroupContext>().deployServerGroups[location]
          ?.first()
          ?: throw IllegalStateException("Could not find experiment server group.")

      deployedCanaryClusters.putAll(mapOf(
        "experimentLocation" to location,
        "experimentServerGroup" to serverGroup
      ))
    }

    return TaskResult(SUCCEEDED, emptyMap<String, Any>(), mapOf(
      "deployedCanaryClusters" to deployedCanaryClusters
    ))
  }
}

private fun Stage.withFirstChildOf(name: String, block: (Stage) -> Unit) {
  execution.stages.find {
    it.name == name
      && it.topLevelStage == topLevelStage // i.e., are we looking in the same canary stage?
  }?.let { stage ->
    val firstChild = execution.stages.find { it.parentStageId == stage.id }
      ?: throw IllegalStateException("Could not find first child of stage $name.")
    block(firstChild)
  } ?: throw IllegalStateException("Could not find stage with name $name.")
}

data class DeployServerGroupContext @JsonCreator constructor(
  @param:JsonProperty("deploy.server.groups") val deployServerGroups: Map<String, List<String>>
)
