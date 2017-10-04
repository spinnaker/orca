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
package com.netflix.spinnaker.orca.declarative.intents

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonTypeName
import com.github.jonpeterson.jackson.module.versioning.JsonVersionedModel
import com.netflix.spinnaker.orca.declarative.Intent
import com.netflix.spinnaker.orca.declarative.IntentMetadata
import com.netflix.spinnaker.orca.declarative.IntentPlan
import com.netflix.spinnaker.orca.declarative.IntentSpec
import com.netflix.spinnaker.orca.declarative.converter.ApplicationToCurrentConverter
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import java.time.LocalDateTime

/**
 * The ApplicationIntent is responsible for updating all application configuration preferences.
 */
@JsonTypeName("Application")
@JsonVersionedModel(currentVersion = "1", toCurrentConverterClass = ApplicationToCurrentConverter::class, propertyName = "schema")
class ApplicationIntent
@JsonCreator constructor(spec: ApplicationSpec) : Intent<ApplicationSpec>(
  kind = "Application",
  schema = "1",
  spec = spec
) {
  override fun plan(metadata: IntentMetadata): IntentPlan<ApplicationSpec> {
    throw UnsupportedOperationException("not implemented")
  }

  override fun apply(i: IntentPlan<ApplicationSpec>, metadata: IntentMetadata): List<Orchestration> {
    throw UnsupportedOperationException("not implemented")
  }

  // TODO rz - Use case applying multiple / embedded intents.
  override fun apply(metadata: IntentMetadata)
    = listOf(Orchestration(metadata.application).apply {
      name = "Update application"
      description = "Converging on external state intent"
      isLimitConcurrent = true
      isKeepWaitingPipelines = true
      origin = metadata.origin
      stages.add(StageDefinitionBuilder.newStage(
        this,
        "upsertApplication",
        null,
        spec.toUpsertApplicationContext(),
        null,
        null))
    })
}

data class ApplicationSpec(
  val name: String,
  val description: String,
  val type: String,
  val email: String,
  val updateTs: LocalDateTime,
  val createTs: LocalDateTime,
  val repoType: String,
  val repoSlug: String,
  val repoProjectKey: String,
  val owner: String,
  val enableRestartRunningExecutions: Boolean,
  val accounts: Set<String>,
  val appGroup: String,
  val group: String,
  val cloudProviders: Set<String>,
  val requiredGroupMembership: Set<String>,
  val pdApiKey: String,
  val dataSources: ApplicationFeatures,
  val chaosMonkey: ChaosMonkeySpec
) : IntentSpec {
  // TODO rz - This is pretty janky, backwards-compatibility with the rest of orca. Would prefer to use everywhere in time.
  fun toUpsertApplicationContext() = mapOf<String, Any>(
    "name" to name,
    "description" to description,
    "type" to type,
    "email" to email,
    "updateTs" to updateTs,
    "createTs" to createTs,
    "repoType" to repoType,
    "repoSlug" to repoSlug,
    "repoProjectKey" to repoProjectKey,
    "owner" to owner,
    "enableRestartRunningExecutions" to enableRestartRunningExecutions,
    "accounts" to accounts,
    "appGroup" to appGroup,
    "group" to group,
    "cloudProviders" to cloudProviders,
    "requiredGroupMembership" to requiredGroupMembership,
    "dataSources" to dataSources,
    "pdApiKey" to pdApiKey
    // TODO rz - finish
  )
}

data class ApplicationFeatures(
  val enabled: Set<String>,
  val disabled: Set<String>
)
