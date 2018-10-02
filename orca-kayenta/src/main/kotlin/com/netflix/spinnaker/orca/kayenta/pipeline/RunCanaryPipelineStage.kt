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

import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.ext.withTask
import com.netflix.spinnaker.orca.kayenta.KayentaService
import com.netflix.spinnaker.orca.kayenta.tasks.MonitorKayentaCanaryTask
import com.netflix.spinnaker.orca.kayenta.tasks.RunKayentaCanaryTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.Collections.emptyMap

@Component
class RunCanaryPipelineStage(
  private val kayentaService: KayentaService
) : StageDefinitionBuilder, CancellableStage {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun taskGraph(stage: Stage, builder: TaskNode.Builder) {
    builder
      .withTask<RunKayentaCanaryTask>("runCanary")
      .withTask<MonitorKayentaCanaryTask>("monitorCanary")
  }

  override fun getType(): String {
    return STAGE_TYPE
  }

  override fun cancel(stage: Stage): CancellableStage.Result {
    val context = stage.context
    val canaryPipelineExecutionId = context["canaryPipelineExecutionId"] as String?

    if (canaryPipelineExecutionId != null) {
      log.info(
        "Cancelling stage ({}, {}, canaryPipelineExecutionId: {}, context: {})",
        kv("executionId", stage.execution.id),
        kv("stageId", stage.id),
        canaryPipelineExecutionId,
        stage.context
      )
      try {
        kayentaService.cancelPipelineExecution(canaryPipelineExecutionId, "")
      } catch (e: Exception) {
        log.error(
          "Failed to cancel stage ({}, {})",
          kv("executionId", stage.execution.id),
          kv("stageId", stage.id),
          e
        )
      }
    } else {
      log.info(
        "Not cancelling stage ({}, {}, context: {}) since no canary pipeline execution id exists",
        kv("executionId", stage.execution.id),
        kv("stageId", stage.id),
        stage.context
      )
    }

    return CancellableStage.Result(stage, emptyMap<Any, Any>())
  }

  companion object {
    @JvmStatic
    val STAGE_TYPE = "runCanary"

    @JvmStatic
    val STAGE_NAME_PREFIX = "Run Canary #"
  }
}
