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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.events.StageComplete
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.time.fixedClock
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import org.springframework.context.ApplicationEventPublisher

@RunWith(JUnitPlatform::class)
class CompleteStageHandlerSpec : Spek({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val publisher: ApplicationEventPublisher = mock()
  val clock = fixedClock()

  val handler = CompleteStageHandler(queue, repository, publisher, clock)

  fun resetMocks() = reset(queue, repository, publisher)

  describe("when a stage completes successfully") {
    context("it is the last stage") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          type = singleTaskStage.type
        }
      }
      val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, SUCCEEDED)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("updates the stage state") {
        argumentCaptor<Stage<Pipeline>>().apply {
          verify(repository).storeStage(capture())
          assertThat(firstValue.status, equalTo(SUCCEEDED))
          assertThat(firstValue.endTime, equalTo(clock.millis()))
        }
      }

      it("completes the execution") {
        verify(queue).push(CompleteExecution(
          message.executionType,
          message.executionId,
          "foo",
          SUCCEEDED
        ))
      }

      it("does not emit any commands") {
        verify(queue, never()).push(any<RunTask>())
      }

      it("publishes an event") {
        argumentCaptor<StageComplete>().apply {
          verify(publisher).publishEvent(capture())
          firstValue.apply {
            executionType shouldBe pipeline.javaClass
            executionId shouldBe pipeline.id
            stageId shouldBe message.stageId
            status shouldBe SUCCEEDED
          }
        }
      }
    }

    context("there is a single downstream stage") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          type = singleTaskStage.type
        }
        stage {
          refId = "2"
          requisiteStageRefIds = setOf("1")
          type = singleTaskStage.type
        }
      }
      val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, SUCCEEDED)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("updates the stage state") {
        argumentCaptor<Stage<Pipeline>>().apply {
          verify(repository).storeStage(capture())
          assertThat(firstValue.status, equalTo(SUCCEEDED))
          assertThat(firstValue.endTime, equalTo(clock.millis()))
        }
      }

      it("runs the next stage") {
        verify(queue).push(StartStage(
          message.executionType,
          message.executionId,
          "foo",
          pipeline.stages.last().id
        ))
      }

      it("does not run any tasks") {
        verify(queue, never()).push(any<RunTask>())
      }
    }

    context("there are multiple downstream stages") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          type = singleTaskStage.type
        }
        stage {
          refId = "2"
          requisiteStageRefIds = setOf("1")
          type = singleTaskStage.type
        }
        stage {
          refId = "3"
          requisiteStageRefIds = setOf("1")
          type = singleTaskStage.type
        }
      }
      val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, SUCCEEDED)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("runs the next stages") {
        argumentCaptor<StartStage>().apply {
          verify(queue, times(2)).push(capture())
          assertThat(
            allValues.map { it.stageId }.toSet(),
            equalTo(pipeline.stages[1..2].map { it.id }.toSet())
          )
        }
      }
    }
  }

  setOf(TERMINAL, CANCELED).forEach { status ->
    describe("when a stage fails with $status status") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          type = singleTaskStage.type
        }
        stage {
          refId = "2"
          requisiteStageRefIds = listOf("1")
          type = singleTaskStage.type
        }
      }
      val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, status)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("updates the stage state") {
        argumentCaptor<Stage<Pipeline>>().apply {
          verify(repository).storeStage(capture())
          assertThat(firstValue.status, equalTo(status))
          assertThat(firstValue.endTime, equalTo(clock.millis()))
        }
      }

      it("does not run any downstream stages") {
        verify(queue, never()).push(isA<StartStage>())
      }

      it("fails the execution") {
        verify(queue).push(CompleteExecution(
          message.executionType,
          message.executionId,
          "foo",
          status
        ))
      }

      it("publishes an event") {
        argumentCaptor<StageComplete>().apply {
          verify(publisher).publishEvent(capture())
          firstValue.let {
            it.executionType shouldBe pipeline.javaClass
            it.executionId shouldBe pipeline.id
            it.stageId shouldBe message.stageId
            it.status shouldBe status
          }
        }
      }
    }
  }

  describe("synthetic stages") {
    context("when a synthetic stage completes successfully") {
      context("before the main stage") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            type = stageWithSyntheticBefore.type
            stageWithSyntheticBefore.buildSyntheticStages(this)
            stageWithSyntheticBefore.buildTasks(this)
          }
        }

        context("there are more before stages") {
          val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id, SUCCEEDED)
          beforeGroup {
            whenever(repository.retrievePipeline(pipeline.id))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            handler.handle(message)
          }

          it("runs the next synthetic stage") {
            verify(queue).push(StartStage(
              message.executionType,
              message.executionId,
              "foo",
              pipeline.stages[1].id
            ))
          }
        }

        context("it is the last before stage") {
          val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages[1].id, SUCCEEDED)
          beforeGroup {
            whenever(repository.retrievePipeline(pipeline.id))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            handler.handle(message)
          }

          it("runs the next synthetic stage") {
            verify(queue).push(StartTask(
              message.executionType,
              message.executionId,
              "foo",
              pipeline.stages[2].id,
              "1"
            ))
          }
        }
      }

      context("after the main stage") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            type = stageWithSyntheticAfter.type
            stageWithSyntheticAfter.buildSyntheticStages(this)
            stageWithSyntheticAfter.buildTasks(this)
          }
        }

        context("there are more after stages") {
          val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages[1].id, SUCCEEDED)
          beforeGroup {
            whenever(repository.retrievePipeline(pipeline.id))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            handler.handle(message)
          }

          it("runs the next synthetic stage") {
            verify(queue).push(StartStage(
              message.executionType,
              message.executionId,
              "foo",
              pipeline.stages.last().id
            ))
          }
        }

        context("it is the last after stage") {
          val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.last().id, SUCCEEDED)
          beforeGroup {
            whenever(repository.retrievePipeline(pipeline.id))
              .thenReturn(pipeline)
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            handler.handle(message)
          }

          it("signals the completion of the parent stage") {
            verify(queue).push(CompleteStage(
              message.executionType,
              message.executionId,
              "foo",
              pipeline.stages.first().id,
              SUCCEEDED
            ))
          }
        }
      }
    }

    context("when a synthetic stage fails") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          type = stageWithSyntheticBefore.type
          stageWithSyntheticBefore.buildSyntheticStages(this)
        }
      }
      val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stageByRef("1<1").id, TERMINAL)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId))
          .thenReturn(pipeline)
      }

      action("the handler receives a message") {
        handler.handle(message)
      }

      afterGroup(::resetMocks)

      it("rolls the failure up to the parent stage") {
        argumentCaptor<CompleteStage>().apply {
          verify(queue).push(capture())
          assertThat(firstValue.stageId, equalTo(pipeline.stageByRef("1").id))
          assertThat(firstValue.status, equalTo(message.status))
        }
      }
    }
  }

  describe("branching stages") {
    context("when one branch completes") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          type = stageWithParallelBranches.type
          stageWithParallelBranches.buildSyntheticStages(this)
          stageWithParallelBranches.buildTasks(this)
        }
      }
      val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages[0].id, SUCCEEDED)

      beforeGroup {
        whenever(repository.retrievePipeline(pipeline.id))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("waits for other branches to finish") {
        verify(queue, never()).push(any())
      }
    }

    context("when all branches are complete") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          type = stageWithParallelBranches.type
          stageWithParallelBranches.buildSyntheticStages(this)
          stageWithParallelBranches.buildTasks(this)
        }
      }
      val message = CompleteStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages[0].id, SUCCEEDED)

      beforeGroup {
        pipeline.stages.forEach {
          if (it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE && it.id != message.stageId) {
            it.status = SUCCEEDED
          }
        }

        whenever(repository.retrievePipeline(pipeline.id))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("runs any post-branch tasks") {
        verify(queue).push(isA<StartTask>())
      }
    }
  }
})
