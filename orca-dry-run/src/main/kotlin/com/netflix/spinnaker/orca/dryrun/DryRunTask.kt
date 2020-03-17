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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.dryrun.stub.OutputStub
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DryRunTask(
  private val outputStubs: List<OutputStub>
) : Task {

  override fun execute(stage: StageExecution): TaskResult =
    stage.generateOutputs().let { outputs ->
      stage.execution.also { execution ->
        log.info("Dry run of ${execution.application} ${execution.name} ${execution.id} stage ${stage.type} ${stage.refId} outputting $outputs")
      }
      TaskResult.builder(SUCCEEDED).outputs(outputs).build()
    }

  private fun StageExecution.generateOutputs(): Map<String, Any> =
    outputStubs.find { it.supports(this) }?.outputs(this) ?: emptyMap()

  private val log = LoggerFactory.getLogger(javaClass)
}
