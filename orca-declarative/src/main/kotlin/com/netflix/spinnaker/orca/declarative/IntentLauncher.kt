/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.declarative

import com.netflix.spinnaker.orca.pipeline.OrchestrationLauncher
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class IntentLauncher
@Autowired constructor(
  private val orchestrationLauncher: OrchestrationLauncher,
  private val clock: Clock
) {

  private val log = LoggerFactory.getLogger(javaClass)

  fun launch(intent: Intent<*>, request: IntentInvocationWrapper): List<Orchestration> {
    if (request.plan) {
      // TODO rz - do
      throw UnsupportedOperationException("planning is not supported")
    }

    return mutableListOf<Orchestration>().apply {
      intent.apply(request.metadata).forEach {
        log.info("Intent launching orchestration (intent: ${request.metadata.id}, orchestration: ${it.id})")

        configureOrchestration(it, request.metadata.origin)
        add(orchestrationLauncher.persistAndStart(it))
      }
    }
  }

  private fun configureOrchestration(orchestration: Orchestration, o: String) {
    orchestration.apply {
      executionEngine = Execution.ExecutionEngine.v3
      buildTime = clock.millis()
      authentication = Execution.AuthenticationDetails.build().orElse(Execution.AuthenticationDetails())
      origin = o
    }
  }
}
