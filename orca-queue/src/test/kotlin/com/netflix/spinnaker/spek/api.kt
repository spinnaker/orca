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

package com.netflix.spinnaker.spek

import org.jetbrains.spek.api.dsl.Pending
import org.jetbrains.spek.api.dsl.SpecBody

/**
 * Creates a [group][SpecBody.group].
 */
fun SpecBody.and(description: String, body: SpecBody.() -> Unit) {
  group("and $description", body = body)
}

fun SpecBody.xand(description: String, reason: String? = null, body: SpecBody.() -> Unit) {
  group("and $description", Pending.Yes(reason), body = body)
}

/**
 * Creates a [group][SpecBody.group].
 */
fun SpecBody.but(description: String, body: SpecBody.() -> Unit) {
  group("but $description", body = body)
}

fun SpecBody.but(description: String, reason: String? = null, body: SpecBody.() -> Unit) {
  group("but $description", Pending.Yes(reason), body = body)
}
