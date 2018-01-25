/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.q.migration

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

object FqnTypeInfoSerializationMigratorTest : Spek({

  val migrator = FqnTypeInfoSerializationMigrator()

  describe("a Jackson TypeInfoName serialization migrator") {
    it("should do nothing if a @class property does not exist on a message") {
      val message = mutableMapOf<String, Any?>(
        "kind" to "example",
        "intent" to mutableMapOf(
          "foo" to "bar"
        )
      )

      assertThat(migrator.migrate(message)).isEqualTo(message)
    }

    it("should convert a @class property to a name-property object") {
      val message = mutableMapOf<String, Any?>(
        "@class" to "com.netflix.spinnaker.q.SimpleMessage",
        "intent" to mutableMapOf(
          "foo" to "bar"
        ),
        "attributes" to listOf(
          mutableMapOf(
            "@class" to "com.netflix.spinnaker.q.AttemptsAttribute",
            "value" to 1
          )
        )
      )
      val expected = mutableMapOf<String, Any?>(
        "kind" to "simple",
        "intent" to mutableMapOf(
          "foo" to "bar"
        ),
        "attributes" to listOf(
          mutableMapOf(
            "kind" to "attempts",
            "value" to 1
          )
        )
      )

      assertThat(migrator.migrate(message)).isEqualTo(expected)
    }
  }
})
