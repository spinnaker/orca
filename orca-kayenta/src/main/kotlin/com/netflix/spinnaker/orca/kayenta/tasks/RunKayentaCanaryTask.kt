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

import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.ext.mapTo
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.kayenta.CanaryExecutionRequest
import com.netflix.spinnaker.orca.kayenta.CanaryScopes
import com.netflix.spinnaker.orca.kayenta.KayentaService
import com.netflix.spinnaker.orca.kayenta.model.RunCanaryContext
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component
import java.util.Collections.singletonMap

@Component
class RunKayentaCanaryTask(
  private val kayentaService: KayentaService
) : Task {
  override fun execute(stage: Stage): TaskResult {
    val context = stage.mapTo<RunCanaryContext>()

    val scopes = stage.deployedCanaryClusters?.let { deployedClusters ->
      // Assume for now that if we deployed canary clusters that
      // we want to update all the scopes.
      context.scopes.mapValues { it.value.from(deployedClusters) }
    } ?: context.scopes

    val canaryPipelineExecutionId = kayentaService.create(
      context.canaryConfigId,
      stage.execution.application,
      stage.execution.id,
      context.metricsAccountName,
      context.storageAccountName /* configurationAccountName */, // TODO(duftler): Propagate configurationAccountName properly.
      context.storageAccountName,
      CanaryExecutionRequest(scopes, context.scoreThresholds)
    )["canaryExecutionId"] as String

    return TaskResult(SUCCEEDED, singletonMap("canaryPipelineExecutionId", canaryPipelineExecutionId))
  }
}

private val Stage.deployedCanaryClusters: DeployedCanaryClusters?
  get() = if (context["deployedCanaryClusters"] != null) {
    OrcaObjectMapper.newInstance()
      .convertValue(context["deployedCanaryClusters"], DeployedCanaryClusters::class.java)
  } else {
    null
  }

private fun CanaryScopes.from(clusters: DeployedCanaryClusters): CanaryScopes {
  return copy(
    controlScope = controlScope.copy(
      scope = clusters.controlServerGroup,
      location = clusters.controlLocation
    ),
    experimentScope = experimentScope.copy(
      scope = clusters.experimentServerGroup,
      location = clusters.experimentLocation
    )
  )
}

data class DeployedCanaryClusters(
  val controlServerGroup: String,
  val controlLocation: String,
  val experimentServerGroup: String,
  val experimentLocation: String
)
