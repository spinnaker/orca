/*
 * Copyright 2019 Netflix, Inc.
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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.ext.failureStatus
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import com.netflix.spinnaker.orca.fixture.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.dsl.it

class StageTest : Spek({
  describe("parent stage context is empty") {
    val pipeline = pipeline {
      application = "test"
      stage {
        refId = "1"
        type = "parent"
        status = ExecutionStatus.SUCCEEDED
        stage {
          type = "child"
          status = ExecutionStatus.TERMINAL
        }
      }
    }

    it("looks up execution options from parent") {
      assertThat(pipeline.stages[1].failureStatus()).isEqualTo(ExecutionStatus.TERMINAL)
    }
  }

  describe("parent stage context is empty but child is set") {
    val pipeline = pipeline {
      application = "test"
      stage {
        refId = "1"
        type = "parent"
        status = ExecutionStatus.SUCCEEDED
        context = mapOf(
          "failPipeline" to false,
          "continuePipeline" to true
        )
        stage {
          type = "child"
          status = ExecutionStatus.TERMINAL
        }
      }
    }

    it("looks up execution options from parent") {
      assertThat(pipeline.stages[1].failureStatus()).isEqualTo(ExecutionStatus.FAILED_CONTINUE)
    }
  }

  describe("parent stage context dictates stop this branch") {
    val pipeline = pipeline {
      application = "test"
      stage {
        refId = "1"
        type = "parent"
        status = ExecutionStatus.SUCCEEDED
        context = mapOf(
          "failPipeline" to false,
          "continuePipeline" to false
        )
        stage {
          type = "child"
          status = ExecutionStatus.TERMINAL
        }
      }
    }

    it("looks up execution options from parent") {
      assertThat(pipeline.stages[1].failureStatus()).isEqualTo(ExecutionStatus.STOPPED)
    }
  }
})
