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

package com.netflix.spinnaker.orca.q.handler

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.present
import com.natpryce.hamkrest.should.shouldMatch
import com.natpryce.hamkrest.should.shouldNotMatch
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.q.Message.StageRestarting
import com.netflix.spinnaker.orca.q.Message.StageStarting
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import java.time.Clock.fixed
import java.time.Instant.now
import java.time.ZoneId.systemDefault
import java.time.temporal.ChronoUnit.HOURS
import java.time.temporal.ChronoUnit.MINUTES

@RunWith(JUnitPlatform::class)
class StageRestartingHandlerSpec : Spek({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val clock = fixed(now(), systemDefault())

  val handler = StageRestartingHandler(
    queue,
    repository
  )

  fun resetMocks() = reset(queue, repository)

  ExecutionStatus
    .values()
    .filter { !it.complete }
    .forEach { incompleteStatus ->
      describe("trying to restart a $incompleteStatus stage") {
        val pipeline = pipeline {
          application = "foo"
          status = RUNNING
          startTime = clock.instant().minus(1, HOURS).toEpochMilli()
          stage {
            refId = "1"
            singleTaskStage.plan(this)
            status = incompleteStatus
            startTime = clock.instant().minus(1, HOURS).toEpochMilli()
          }
        }
        val message = StageRestarting(Pipeline::class.java, pipeline.id, "foo", pipeline.stageByRef("1").id)

        beforeGroup {
          whenever(repository.retrievePipeline(message.executionId))
            .thenReturn(pipeline)
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          handler.handle(message)
        }

        it("does not modify the stage status") {
          verify(repository, never()).store(any<Pipeline>())
        }

        it("runs the stage") {
          verify(queue, never()).push(any<StageStarting>())
        }

        // TODO: should probably queue some kind of error
      }
    }

  setOf(TERMINAL, SUCCEEDED).forEach { stageStatus ->
    describe("restarting a $stageStatus stage") {
      val pipeline = pipeline {
        application = "foo"
        status = stageStatus
        startTime = clock.instant().minus(1, HOURS).toEpochMilli()
        endTime = clock.instant().minus(30, MINUTES).toEpochMilli()
        stage {
          refId = "1"
          stageWithSyntheticBefore.plan(this)
          status = SUCCEEDED
          startTime = clock.instant().minus(1, HOURS).toEpochMilli()
          endTime = clock.instant().minus(59, MINUTES).toEpochMilli()
        }
        stage {
          refId = "2"
          requisiteStageRefIds = listOf("1")
          stageWithSyntheticBefore.plan(this)
          status = stageStatus
          startTime = clock.instant().minus(59, MINUTES).toEpochMilli()
          endTime = clock.instant().minus(30, MINUTES).toEpochMilli()
        }
      }
      val message = StageRestarting(Pipeline::class.java, pipeline.id, "foo", pipeline.stageByRef("2").id)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("resets the stage's status") {
        argumentCaptor<Pipeline>().apply {
          verify(repository).store(capture())
          firstValue.stageById(message.stageId).apply {
            status shouldBe NOT_STARTED
            startTime shouldMatch absent()
            endTime shouldMatch absent()
          }
        }
      }

      it("removes the stage's tasks") {
        argumentCaptor<Pipeline>().apply {
          verify(repository).store(capture())
          firstValue.stageById(message.stageId).tasks shouldMatch isEmpty
        }
      }

      it("removes the stage's synthetic stages") {
        argumentCaptor<Pipeline>().apply {
          verify(repository).store(capture())
          firstValue.stages.filter { it.parentStageId == message.stageId } shouldMatch isEmpty
        }
      }

      it("does not alter any preceding stages") {
        argumentCaptor<Pipeline>().apply {
          verify(repository).store(capture())
          firstValue.stageByRef("1").apply {
            status shouldBe SUCCEEDED
            startTime shouldMatch present()
            endTime shouldMatch present()
          }
        }
      }

      it("does not affect preceding stages' synthetic stages") {
        argumentCaptor<Pipeline>().apply {
          verify(repository).store(capture())
          firstValue.stages.filter { it.parentStageId == firstValue.stageByRef("1").id } shouldNotMatch isEmpty
        }
      }

      it("marks the execution as running") {
        argumentCaptor<Pipeline>().apply {
          verify(repository).store(capture())
          firstValue.status shouldBe RUNNING
          firstValue.endTime shouldMatch absent()
        }
      }

      it("runs the stage") {
        argumentCaptor<StageStarting>().apply {
          verify(queue).push(capture())
          firstValue.apply {
            executionType shouldBe message.executionType
            executionId shouldBe message.executionId
            application shouldBe message.application
            stageId shouldBe message.stageId
          }
        }
      }
    }
  }

  describe("restarting a SUCCEEDED stage with downstream stages") {
    val pipeline = pipeline {
      application = "foo"
      status = SUCCEEDED
      startTime = clock.instant().minus(1, HOURS).toEpochMilli()
      endTime = clock.instant().minus(30, MINUTES).toEpochMilli()
      stage {
        refId = "1"
        singleTaskStage.plan(this)
        status = SUCCEEDED
        startTime = clock.instant().minus(1, HOURS).toEpochMilli()
        endTime = clock.instant().minus(59, MINUTES).toEpochMilli()
      }
      stage {
        refId = "2"
        requisiteStageRefIds = listOf("1")
        stageWithSyntheticBefore.plan(this)
        status = SUCCEEDED
        startTime = clock.instant().minus(59, MINUTES).toEpochMilli()
        endTime = clock.instant().minus(58, MINUTES).toEpochMilli()
      }
      stage {
        refId = "3"
        requisiteStageRefIds = listOf("2")
        stageWithSyntheticBefore.plan(this)
        status = SUCCEEDED
        startTime = clock.instant().minus(58, MINUTES).toEpochMilli()
        endTime = clock.instant().minus(57, MINUTES).toEpochMilli()
      }
    }
    val message = StageRestarting(Pipeline::class.java, pipeline.id, "foo", pipeline.stageByRef("1").id)

    beforeGroup {
      whenever(repository.retrievePipeline(message.executionId))
        .thenReturn(pipeline)
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      handler.handle(message)
    }

    it("removes downstream stages' tasks") {
      argumentCaptor<Pipeline>().apply {
        verify(repository).store(capture())
        firstValue.stageByRef("2").tasks shouldMatch isEmpty
        firstValue.stageByRef("3").tasks shouldMatch isEmpty
      }
    }

    it("removes downstream stages' synthetic stages") {
      argumentCaptor<Pipeline>().apply {
        verify(repository).store(capture())
        firstValue.stages.filter { it.parentStageId == firstValue.stageByRef("2").id } shouldMatch isEmpty
        firstValue.stages.filter { it.parentStageId == firstValue.stageByRef("3").id } shouldMatch isEmpty
      }
    }
  }

  describe("restarting a SUCCEEDED stage with a downstream join") {
    val pipeline = pipeline {
      application = "foo"
      status = SUCCEEDED
      startTime = clock.instant().minus(1, HOURS).toEpochMilli()
      endTime = clock.instant().minus(30, MINUTES).toEpochMilli()
      stage {
        refId = "1"
        singleTaskStage.plan(this)
        status = SUCCEEDED
        startTime = clock.instant().minus(1, HOURS).toEpochMilli()
        endTime = clock.instant().minus(59, MINUTES).toEpochMilli()
      }
      stage {
        refId = "2"
        stageWithSyntheticBefore.plan(this)
        status = SUCCEEDED
        startTime = clock.instant().minus(59, MINUTES).toEpochMilli()
        endTime = clock.instant().minus(58, MINUTES).toEpochMilli()
      }
      stage {
        refId = "3"
        requisiteStageRefIds = listOf("1", "2")
        stageWithSyntheticBefore.plan(this)
        status = SUCCEEDED
        startTime = clock.instant().minus(59, MINUTES).toEpochMilli()
        endTime = clock.instant().minus(58, MINUTES).toEpochMilli()
      }
      stage {
        refId = "4"
        requisiteStageRefIds = listOf("3")
        stageWithSyntheticBefore.plan(this)
        status = SUCCEEDED
        startTime = clock.instant().minus(58, MINUTES).toEpochMilli()
        endTime = clock.instant().minus(57, MINUTES).toEpochMilli()
      }
    }
    val message = StageRestarting(Pipeline::class.java, pipeline.id, "foo", pipeline.stageByRef("1").id)

    beforeGroup {
      whenever(repository.retrievePipeline(message.executionId))
        .thenReturn(pipeline)
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      handler.handle(message)
    }

    it("removes join stages' tasks") {
      argumentCaptor<Pipeline>().apply {
        verify(repository).store(capture())
        firstValue.stageByRef("3").tasks shouldMatch isEmpty
        firstValue.stageByRef("4").tasks shouldMatch isEmpty
      }
    }

    it("removes join stages' synthetic stages") {
      argumentCaptor<Pipeline>().apply {
        verify(repository).store(capture())
        firstValue.stages.filter { it.parentStageId == firstValue.stageByRef("3").id } shouldMatch isEmpty
        firstValue.stages.filter { it.parentStageId == firstValue.stageByRef("4").id } shouldMatch isEmpty
      }
    }
  }
})

fun <T : Execution<T>> StageDefinitionBuilder.plan(stage: Stage<T>) {
  stage.type = type
  buildTasks(stage)
  buildSyntheticStages(stage)
}

infix fun <T> T.shouldBe(expected: T) {
  this shouldMatch equalTo(expected)
}
