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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.ExecutionStatus.SKIPPED
import com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DryRunTask : Task {
  private val blacklistKeyPatterns =
    setOf(
      "amiSuffix",
      "kato\\..*",
      "stageDetails"
    )
      .map(String::toRegex)

  override fun execute(stage: Stage<out Execution<*>>): TaskResult =
    stage
      .getExecution()
      .let { execution ->
        when (execution) {
          is Pipeline -> {
            realStage(execution, stage)
              .let { realStage ->
                stage.evaluateStage(realStage)
              }
          }
          else -> {
            log.error("Dry run is only supported for pipelines")
            TaskResult(TERMINAL)
          }
        }
      }

  private fun Stage<out Execution<*>>.evaluateStage(realStage: Stage<Pipeline>): TaskResult {
    val dryRunResult = mutableMapOf<String, Any>()
    var status: ExecutionStatus? = null

    val mismatchedKeys = getContext()
      .filterKeys { key -> blacklistKeyPatterns.none { key.matches(it) } }
      .filter { (key, value) ->
        value != realStage.context[key]
      }

    if (mismatchedKeys.isNotEmpty()) {
      dryRunResult["context"] = mismatchedKeys
        .mapValues { (key, value) ->
          "Expected \"${realStage.context[key]}\" but found \"$value\"."
        }
      status = TERMINAL
    }

    if (realStage.status == SKIPPED) {
      dryRunResult["errors"] = listOf("Expected stage to be skipped.")
      status = TERMINAL
    }

    return TaskResult(
      status ?: realStage.status,
      realStage.context + mismatchedKeys,
      realStage.outputs + if (dryRunResult.isNotEmpty()) mapOf("dryRunResult" to dryRunResult) else emptyMap()
    )
  }

  private fun realStage(execution: Pipeline, stage: Stage<out Execution<*>>): Stage<Pipeline> {
    return execution
      .trigger["lastSuccessfulExecution"]
      .let { realPipeline ->
        when (realPipeline) {
          is Pipeline -> realPipeline
          is Map<*, *> -> mapper.convertValue(realPipeline)
          else -> throw IllegalStateException("No triggering pipeline execution found")
        }
      }
      .stageByRef(stage.getRefId())
  }

  private val mapper = OrcaObjectMapper.newInstance()
  private val log = LoggerFactory.getLogger(javaClass)

  private inline fun <reified T> ObjectMapper.convertValue(fromValue: Any): T =
    convertValue(fromValue, T::class.java)
}
