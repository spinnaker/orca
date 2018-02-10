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

package com.netflix.spinnaker.orca.pipeline.model

import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact
import com.netflix.spinnaker.orca.pipeline.model.support.TriggerDeserializer

@JsonDeserialize(using = TriggerDeserializer::class)
data class Trigger
@JvmOverloads constructor(
  val type: String,
  @get:JsonUnwrapped val payload: TriggerPayload? = null,
  val correlationId: String? = null,
  val user: String? = "[anonymous]",
  val parameters: Map<String, Any> = mutableMapOf(),
  val artifacts: List<Artifact> = mutableListOf(),
  val notifications: List<Map<String, Any>> = mutableListOf(),
  var isRebake: Boolean = false,
  var isDryRun: Boolean = false,
  var isStrategy: Boolean = false
) {
  var resolvedExpectedArtifacts: List<ExpectedArtifact> = mutableListOf()

  inline fun <reified T : TriggerPayload> hasPayload(): Boolean = payload is T
}

