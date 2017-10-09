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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.github.jonpeterson.jackson.module.versioning.JsonSerializeToVersion
import com.netflix.spinnaker.orca.pipeline.model.Orchestration

/**
 * An Intent represents a new, discrete desired state. An Intent can contain
 * other Intents to be updated at the same time. They are responsible for taking
 * user (or system) inputs via their Specification and converting them into one
 * of two outputs:
 *
 * 1. Intents can be output as an IntentPlan, which will construct both a set of
 *    resources that will be changing. An IntentPlan can either be used to return
 *    to the user, or to be applied and optionally abort if expected output does
 *    not match the plan.
 * 2. Intents can output an Orchestration that will apply the desired state
 *    immediately without first performing a plan.
 *
 * TODO rz - override flag?
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
abstract class Intent<S : IntentSpec>
@JsonCreator constructor(
  @JsonSerializeToVersion(defaultToSource = true) val schema: String,
  val kind: String,
  val spec: S
) {

  abstract fun plan(metadata: IntentMetadata): IntentPlan<S>
  abstract fun apply(i: IntentPlan<S>, metadata: IntentMetadata): List<Orchestration>
  abstract fun apply(metadata: IntentMetadata): List<Orchestration>
}

interface IntentSpec

// Current API design: intentspecs can be nested: Allows plans to create checkpoints
// parallel execution graphs
interface IntentPlan<S : IntentSpec>

