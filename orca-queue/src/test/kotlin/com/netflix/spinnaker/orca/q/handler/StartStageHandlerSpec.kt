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

import com.natpryce.hamkrest.allElements
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasElement
import com.natpryce.hamkrest.isEmpty
import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.q.Message.*
import com.netflix.spinnaker.orca.q.event.ExecutionEvent.StageStartedEvent
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
class StartStageHandlerSpec : Spek({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val publisher: ApplicationEventPublisher = mock()
  val clock = fixedClock()

  val handler = StartStageHandler(
    queue,
    repository,
    listOf(
      singleTaskStage,
      multiTaskStage,
      stageWithSyntheticBefore,
      stageWithSyntheticAfter,
      stageWithParallelBranches,
      rollingPushStage
    ),
    publisher,
    clock
  )

  fun resetMocks() = reset(queue, repository, publisher)

  describe("starting a stage") {
    context("with a single initial task") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          type = singleTaskStage.type
        }
      }
      val message = StartStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("updates the stage status") {
        argumentCaptor<Stage<Pipeline>>().apply {
          verify(repository).storeStage(capture())
          assertThat(firstValue.status, equalTo(RUNNING))
          assertThat(firstValue.startTime, equalTo(clock.millis()))
        }
      }

      it("attaches tasks to the stage") {
        argumentCaptor<Stage<Pipeline>>().apply {
          verify(repository).storeStage(capture())
          assertThat(firstValue.tasks.size, equalTo(1))
          firstValue.tasks.first().apply {
            assertThat(id, equalTo("1"))
            assertThat(name, equalTo("dummy"))
            assertThat(implementingClass.name, equalTo(DummyTask::class.java.name))
            assertThat(isStageStart, equalTo(true))
            assertThat(isStageEnd, equalTo(true))
          }
        }
      }

      it("starts the first task") {
        verify(queue).push(StartTask(
          message.executionType,
          message.executionId,
          "foo",
          message.stageId,
          "1"
        ))
      }

      it("publishes an event") {
        argumentCaptor<StageStartedEvent>().apply {
          verify(publisher).publishEvent(capture())
          firstValue.apply {
            executionType shouldBe pipeline.javaClass
            executionId shouldBe pipeline.id
            stageId shouldBe message.stageId
          }
        }
      }
    }

    context("with several linear tasks") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          type = multiTaskStage.type
        }
      }
      val message = StartStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId))
          .thenReturn(pipeline)
      }

      action("the handler receives a message") {
        handler.handle(message)
      }

      afterGroup(::resetMocks)

      it("attaches tasks to the stage") {
        argumentCaptor<Stage<Pipeline>>().apply {
          verify(repository).storeStage(capture())
          firstValue.apply {
            assertThat(tasks.size, equalTo(3))
            tasks[0].apply {
              assertThat(id, equalTo("1"))
              assertThat(name, equalTo("dummy1"))
              assertThat(implementingClass.name, equalTo(DummyTask::class.java.name))
              assertThat(isStageStart, equalTo(true))
              assertThat(isStageEnd, equalTo(false))
            }
            tasks[1].apply {
              assertThat(id, equalTo("2"))
              assertThat(name, equalTo("dummy2"))
              assertThat(implementingClass.name, equalTo(DummyTask::class.java.name))
              assertThat(isStageStart, equalTo(false))
              assertThat(isStageEnd, equalTo(false))
            }
            tasks[2].apply {
              assertThat(id, equalTo("3"))
              assertThat(name, equalTo("dummy3"))
              assertThat(implementingClass.name, equalTo(DummyTask::class.java.name))
              assertThat(isStageStart, equalTo(false))
              assertThat(isStageEnd, equalTo(true))
            }
          }
        }
      }

      it("raises an event to indicate the first task is starting") {
        verify(queue).push(StartTask(
          message.executionType,
          message.executionId,
          "foo",
          message.stageId,
          "1"
        ))
      }
    }

    context("with synthetic stages") {
      context("before the main stage") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            type = stageWithSyntheticBefore.type
          }
        }
        val message = StartStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id)

        beforeGroup {
          whenever(repository.retrievePipeline(message.executionId))
            .thenReturn(pipeline)
        }

        action("the handler receives a message") {
          handler.handle(message)
        }

        afterGroup(::resetMocks)

        it("attaches the synthetic stage to the pipeline") {
          argumentCaptor<Pipeline>().apply {
            verify(repository).store(capture())
            assertThat(firstValue.stages.size, equalTo(3))
            assertThat(firstValue.stages.map { it.id }, equalTo(listOf("${message.stageId}-1-pre1", "${message.stageId}-2-pre2", message.stageId)))
          }
        }

        it("raises an event to indicate the synthetic stage is starting") {
          verify(queue).push(StartStage(
            message.executionType,
            message.executionId,
            "foo",
            pipeline.stages.first().id
          ))
        }
      }

      context("after the main stage") {
        val pipeline = pipeline {
          application = "foo"
          stage {
            type = stageWithSyntheticAfter.type
          }
        }
        val message = StartStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages.first().id)

        beforeGroup {
          whenever(repository.retrievePipeline(message.executionId))
            .thenReturn(pipeline)
        }

        afterGroup(::resetMocks)

        action("the handler receives a message") {
          handler.handle(message)
        }

        it("attaches the synthetic stage to the pipeline") {
          argumentCaptor<Pipeline>().apply {
            verify(repository).store(capture())
            assertThat(firstValue.stages.size, equalTo(3))
            assertThat(firstValue.stages.map { it.id }, equalTo(listOf(message.stageId, "${message.stageId}-1-post1", "${message.stageId}-2-post2")))
          }
        }

        it("raises an event to indicate the first task is starting") {
          verify(queue).push(StartTask(
            message.executionType,
            message.executionId,
            "foo",
            message.stageId,
            "1"
          ))
        }
      }
    }

    context("with other upstream stages that are incomplete") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          status = SUCCEEDED
          type = singleTaskStage.type
        }
        stage {
          refId = "2"
          status = RUNNING
          type = singleTaskStage.type
        }
        stage {
          refId = "3"
          requisiteStageRefIds = setOf("1", "2")
          type = singleTaskStage.type
        }
      }
      val message = StartStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stageByRef("3").id)

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("doesn't build its tasks") {
        assertThat(pipeline.stageByRef("3").tasks, isEmpty)
      }

      it("waits for the other upstream stage to complete") {
        verify(queue, never()).push(isA<StartTask>())
      }
    }
  }

  describe("running a branching stage") {
    context("when the stage starts") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          type = stageWithParallelBranches.type
        }
      }
      val message = StartStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stageByRef("1").id)

      beforeGroup {
        whenever(repository.retrievePipeline(pipeline.id))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("builds tasks for the main branch") {
        val stage = pipeline.stageById(message.stageId)
        assertThat(stage.tasks.map(Task::getName), equalTo(listOf("post-branch")))
      }

      it("builds synthetic stages for each parallel branch") {
        assertThat(pipeline.stages.size, equalTo(4))
        assertThat(
          pipeline.stages.map { it.type },
          allElements(equalTo(stageWithParallelBranches.type))
        )
        // TODO: contexts, etc.
      }

      it("runs the parallel stages") {
        argumentCaptor<StartStage>().apply {
          verify(queue, times(3)).push(capture())
          assertThat(
            allValues.map { pipeline.stageById(it.stageId).parentStageId },
            allElements(equalTo(message.stageId))
          )
        }
      }
    }

    context("when one branch starts") {
      val pipeline = pipeline {
        application = "foo"
        stage {
          refId = "1"
          type = stageWithParallelBranches.type
          stageWithParallelBranches.buildSyntheticStages(this)
          stageWithParallelBranches.buildTasks(this)
        }
      }
      val message = StartStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stages[0].id)

      beforeGroup {
        whenever(repository.retrievePipeline(pipeline.id))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("builds tasks for the branch") {
        val stage = pipeline.stageById(message.stageId)
        assertThat(stage.tasks, !isEmpty)
        assertThat(stage.tasks.map(Task::getName), equalTo(listOf("in-branch")))
      }

      it("does not build more synthetic stages") {
        val stage = pipeline.stageById(message.stageId)
        assertThat(pipeline.stages.map(Stage<Pipeline>::getParentStageId), !hasElement(stage.id))
      }
    }
  }

  describe("running a rolling push stage") {
    val pipeline = pipeline {
      application = "foo"
      stage {
        refId = "1"
        type = rollingPushStage.type
      }
    }

    context("when the stage starts") {
      val message = StartStage(Pipeline::class.java, pipeline.id, "foo", pipeline.stageByRef("1").id)

      beforeGroup {
        whenever(repository.retrievePipeline(pipeline.id))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("builds tasks for the main branch") {
        pipeline.stageById(message.stageId).let { stage ->
          assertThat(stage.tasks.size, equalTo(5))
          assertThat(stage.tasks[0].isLoopStart, equalTo(false))
          assertThat(stage.tasks[1].isLoopStart, equalTo(true))
          assertThat(stage.tasks[2].isLoopStart, equalTo(false))
          assertThat(stage.tasks[3].isLoopStart, equalTo(false))
          assertThat(stage.tasks[4].isLoopStart, equalTo(false))
          assertThat(stage.tasks[0].isLoopEnd, equalTo(false))
          assertThat(stage.tasks[1].isLoopEnd, equalTo(false))
          assertThat(stage.tasks[2].isLoopEnd, equalTo(false))
          assertThat(stage.tasks[3].isLoopEnd, equalTo(true))
          assertThat(stage.tasks[4].isLoopEnd, equalTo(false))
        }
      }

      it("runs the parallel stages") {
        argumentCaptor<StartTask>().apply {
          verify(queue).push(capture())
          assertThat(firstValue.taskId, equalTo("1"))
        }
      }
    }
  }

  describe("invalid commands") {

    val message = StartStage(Pipeline::class.java, "1", "foo", "1")

    describe("no such execution") {
      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId))
          .thenThrow(ExecutionNotFoundException("No Pipeline found for ${message.executionId}"))
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("emits an error event") {
        verify(queue).push(isA<ConfigurationError.InvalidExecutionId>())
      }
    }

    describe("no such stage") {
      val pipeline = pipeline {
        id = message.executionId
        application = "foo"
      }

      beforeGroup {
        whenever(repository.retrievePipeline(message.executionId))
          .thenReturn(pipeline)
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        handler.handle(message)
      }

      it("emits an error event") {
        verify(queue).push(isA<ConfigurationError.InvalidStageId>())
      }
    }
  }
})
