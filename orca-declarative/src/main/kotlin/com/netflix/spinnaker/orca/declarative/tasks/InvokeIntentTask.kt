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
package com.netflix.spinnaker.orca.declarative.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.declarative.IntentInvocationWrapper
import com.netflix.spinnaker.orca.declarative.IntentLauncher
import com.netflix.spinnaker.orca.declarative.exceptions.DeclarativeException
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
class InvokeIntentTask
@Autowired constructor(
  private val intentLauncher: IntentLauncher,
  @Qualifier("declarativeObjectMapper") private val mapper: ObjectMapper
): Task {

  override fun execute(stage: Stage<out Execution<*>>): TaskResult {
    if (!stage.getContext().containsKey("intent")) {
      throw DeclarativeException("Missing 'intent' context key")
    }

    val wrapper = mapper.convertValue(stage.getContext()["intent"], IntentInvocationWrapper::class.java)

    val executionIds = wrapper.intents
      .map { intentLauncher.launch(it, wrapper) }
      .flatten()
      .map { it.id }

    return TaskResult(ExecutionStatus.SUCCEEDED, mapOf<String, Any>(
      "intentId" to wrapper.metadata.id,
      "executionIds" to executionIds
    ))
  }
}
