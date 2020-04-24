/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.api.test

import dev.minutest.ContextBuilder
import dev.minutest.junit.JUnit5Minutests
import org.pf4j.PluginWrapper
import strikt.assertions.isA
import strikt.assertions.isEqualTo


abstract class OrcaApiTck<T : OrcaFixture> : JUnit5Minutests {

  fun ContextBuilder<T>.defaultOrcaPluginTests(pluginId: String) {
    context("an orca integration test environment and relevant plugins") {

      test("the plugin starts with the expected extensions loaded") {
        strikt.api.expect {
          that(spinnakerPluginManager.startedPlugins.size).isEqualTo(1)
          that(spinnakerPluginManager.startedPlugins.find { it.pluginId == pluginId }).isA<PluginWrapper>()
        }
      }
    }
  }
}
