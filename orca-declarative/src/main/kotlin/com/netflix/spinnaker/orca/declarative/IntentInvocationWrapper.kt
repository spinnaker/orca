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

import java.util.*

/**
 * IntentRequestWrapper is the expected data wrapper for invoking intents via an orchestration.
 */
data class IntentInvocationWrapper(
  val schema: String,
  val metadata: IntentMetadata,
  val intents: List<Intent<*>>,
  val plan: Boolean = false
)

// The data fields that must be set from the orchestration request that is called
// to start the intent process.
// TODO rz - Add trigger information?
data class IntentMetadata(
  // application defines which application this intent will be applied to, or
  // which application is the ultimate "owner" of the state change, in the case
  // of an Intent crossing application boundaries.
  val application: String,
  val origin: String
) {
  // id is used to track an intent over multiple task invocations.
  val id = UUID.randomUUID().toString()
}
