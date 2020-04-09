/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.keel.pipeline

import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder.Aliases
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.keel.task.ImportDeliveryConfigTask
import kotlin.reflect.KClass
import org.springframework.stereotype.Component

/**
 * Pipeline stage that retrieves a Managed Delivery config manifest from source control via igor, then publishes it to keel.
 * Generally this will be added to a single-stage pipeline with a git trigger to support GitOps flows.
 */
@Component
@Aliases("publishDeliveryConfig")
class ImportDeliveryConfigStage : StageDefinitionBuilder {
  override fun taskGraph(stage: StageExecution, builder: TaskNode.Builder) {
    builder.withTask("importDeliveryConfig", ImportDeliveryConfigTask::class)
  }

  private fun TaskNode.Builder.withTask(name: String, type: KClass<out Task>) =
    withTask(name, type.java)
}
