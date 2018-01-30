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

package com.netflix.spinnaker.orca.dryrun

import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.q.pipeline
import com.netflix.spinnaker.orca.q.stage
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it

object DryRunTaskTest : Spek({

  val subject = DryRunTask()

  describe("running the task") {

    val pipeline = pipeline {
      stage {
        type = "deploy"
        refId = "1"
      }
      stage {
        type = "deploy"
        refId = "2"
      }
      trigger["type"] = "dryrun"
      trigger["outputs"] = mapOf("2" to mapOf("foo" to "bar"))
    }

    given("a stage with no outputs in the trigger") {

      val result = subject.execute(pipeline.stageByRef("1"))

      it("should return success") {
        assertThat(result.status).isEqualTo(SUCCEEDED)
      }

      it("should create no outputs") {
        assertThat(result.outputs).isEmpty()
      }
    }

    given("a stage with outputs overridden in the trigger") {

      val result = subject.execute(pipeline.stageByRef("2"))

      it("should return success") {
        assertThat(result.status).isEqualTo(SUCCEEDED)
      }

      it("should copy outputs from the trigger") {
        assertThat(result.outputs).isEqualTo(mapOf("foo" to "bar"))
      }
    }
  }
})
