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
import com.netflix.spinnaker.orca.declarative.converter.ChaosMonkeyToCurrentConverter
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Orchestration

@JsonTypeName("ChaosMonkey")
@JsonVersionedModel(currentVersion = "1", toCurrentConverterClass = ChaosMonkeyToCurrentConverter::class, propertyName = "schema")
class ChaosMonkeyIntent
@JsonCreator constructor(spec: ChaosMonkeySpec) : Intent<ChaosMonkeySpec>(
  kind = "ChaosMonkey",
  schema = "1",
  spec = spec
) {

  override fun plan(metadata: IntentMetadata): IntentPlan<ChaosMonkeySpec> {
    throw UnsupportedOperationException("not implemented")
  }

  override fun apply(i: IntentPlan<ChaosMonkeySpec>, metadata: IntentMetadata): List<Orchestration> {
    throw UnsupportedOperationException("not implemented")
  }

  override fun apply(metadata: IntentMetadata): List<Orchestration>
    = listOf(Orchestration(metadata.application).apply {
        name = "Update Chaos Monkey settings"
        description = "Converging on external state intent"
        isLimitConcurrent = true
        isKeepWaitingPipelines = true
        origin = metadata.origin
        stages.add(StageDefinitionBuilder.newStage(
          this,
          "upsertApplication",
          null,
          mapOf(
            "name" to metadata.application,
            "chaosMonkey" to spec.toUpsertApplicationContext()
          ),
          null,
          null))
      })
}

data class ChaosMonkeySpec(
  val enabled: Boolean,
  val meanTimeBetweenKillsInWorkDays: Int,
  val minTimeBetweenKillsInWorkDays: Int,
  val grouping: String,
  val regionsAreIndependent: Boolean,
  val exceptions: List<ChaosMonkeyExceptionRule>
) : IntentSpec {
  // TODO rz - Should probably look into something like a base toMap() fun
  fun toUpsertApplicationContext() = mapOf<String, Any>(
    "enabled" to enabled,
    "meanTimeBetweenKillsInWorkDays" to meanTimeBetweenKillsInWorkDays,
    "minTimeBetweenKillsInWorkDays" to minTimeBetweenKillsInWorkDays,
    "grouping" to grouping,
    "regionsAreIndependent" to regionsAreIndependent,
    "exceptions" to exceptions.map { it.toUpsertApplicationContext() }
  )
}

data class ChaosMonkeyExceptionRule(
  val region: String,
  val account: String,
  val detail: String,
  val stack: String
) {
  fun toUpsertApplicationContext() = mapOf<String, Any>(
    "region" to region,
    "account" to account,
    "detail" to detail,
    "stack" to stack
  )
}
