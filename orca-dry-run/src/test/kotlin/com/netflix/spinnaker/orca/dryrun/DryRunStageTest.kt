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

import com.natpryce.hamkrest.greaterThan
import com.natpryce.hamkrest.should.shouldMatch
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.q.buildSyntheticStages
import com.netflix.spinnaker.orca.q.buildTasks
import com.netflix.spinnaker.orca.q.multiTaskStage
import com.netflix.spinnaker.orca.q.pipeline
import com.netflix.spinnaker.orca.q.singleTaskStage
import com.netflix.spinnaker.orca.q.stage
import com.netflix.spinnaker.orca.q.stageWithParallelBranches
import com.netflix.spinnaker.orca.q.stageWithSyntheticBefore
import com.netflix.spinnaker.orca.q.stageWithSyntheticBeforeAndNoTasks
import com.netflix.spinnaker.orca.q.zeroTaskStage
import com.netflix.spinnaker.spek.shouldEqual
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object DryRunStageTest : Spek({

  fun StageDefinitionBuilder.plan(stage: Stage) {
    stage.type = type
    buildTasks(stage)
    buildSyntheticStages(stage)
  }

  setOf(zeroTaskStage, singleTaskStage, multiTaskStage, stageWithSyntheticBefore, stageWithSyntheticBeforeAndNoTasks).forEach { proxiedStage ->
    describe("building tasks for a ${proxiedStage.type}") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          type = proxiedStage.type
        }
      }

      val subject = DryRunStage(proxiedStage)

      on("planning the stage") {
        subject.plan(pipeline.stageByRef("1"))
      }

      it("constructs a single task") {
        pipeline.stageByRef("1").tasks.let {
          it.size shouldEqual 1
          it.first().implementingClass shouldEqual DryRunTask::class.qualifiedName
        }
      }
    }
  }

  setOf(zeroTaskStage, singleTaskStage, multiTaskStage).forEach { proxiedStage ->
    describe("building synthetic stages for a ${proxiedStage.type}") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          type = proxiedStage.type
        }
      }

      val subject = DryRunStage(proxiedStage)

      on("planning the stage") {
        subject.plan(pipeline.stageByRef("1"))
      }

      it("does not build any synthetic stages") {
        pipeline.stages.size shouldEqual 1
      }
    }
  }

  setOf(stageWithSyntheticBefore, stageWithSyntheticBeforeAndNoTasks).forEach { proxiedStage ->
    describe("building synthetic stages for a ${proxiedStage.type}") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          type = proxiedStage.type
        }
      }

      val subject = DryRunStage(proxiedStage)

      on("planning the stage") {
        subject.plan(pipeline.stageByRef("1"))
      }

      it("builds the usual synthetic stages") {
        pipeline.stages.size shouldMatch greaterThan(1)
      }
    }
  }

  setOf(stageWithParallelBranches).forEach { proxiedStage ->
    describe("building parallel stages for a ${proxiedStage.type}") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          type = proxiedStage.type
        }
      }

      val subject = DryRunStage(proxiedStage)

      on("planning the stage") {
        subject.plan(pipeline.stageByRef("1"))
      }

      it("builds the usual parallel stages") {
        pipeline.stages.size shouldMatch greaterThan(1)
      }
    }
  }
})
