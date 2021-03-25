/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.orca.q.handler

import com.netflix.spinnaker.orca.DefaultStageResolver
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.FAILED_CONTINUE
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.test.pipeline
import com.netflix.spinnaker.orca.api.test.stage
import com.netflix.spinnaker.orca.pipeline.DefaultStageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.q.pending.PendingExecutionService
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.time.fixedClock
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argForWhich
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.atLeast
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import java.time.temporal.ChronoUnit.HOURS
import java.time.temporal.ChronoUnit.MINUTES
import kotlin.collections.contains
import kotlin.collections.filter
import kotlin.collections.first
import kotlin.collections.flatMap
import kotlin.collections.forEach
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mapOf
import kotlin.collections.set
import kotlin.collections.setOf
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek

object IgnoreStageFailureHandlerTest : SubjectSpek<IgnoreStageFailureHandler>({
  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val pendingExecutionService: PendingExecutionService = mock()
  val clock = fixedClock()

  subject(GROUP) {
    IgnoreStageFailureHandler(
      queue,
      repository,
      DefaultStageDefinitionBuilderFactory(
        DefaultStageResolver(
          StageDefinitionBuildersProvider(
            listOf(
              singleTaskStage,
              stageWithSyntheticBefore,
              stageWithNestedSynthetics
            )
          )
        )
      ),
      pendingExecutionService,
      clock
    )
  }

  fun resetMocks() = reset(queue, repository)

  ExecutionStatus
  .values()
  .filter { !it.isHalt }
  .forEach { unhaltedStatus ->
    describe("trying to ignore the failure of a $unhaltedStatus stage") {
      val pipeline = pipeline {
        application = "foo"
        status = RUNNING
        startTime = clock.instant().minus(1, HOURS).toEpochMilli()
        stage {
          refId = "1"
          singleTaskStage.plan(this)
          status = unhaltedStatus
          startTime = clock.instant().minus(1, HOURS).toEpochMilli()
        }
        stage {
          refId = "2"
          requisiteStageRefIds = listOf("1")
          singleTaskStage.plan(this)
          status = NOT_STARTED
        }
      }
      val message = IgnoreStageFailure(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1").id, "aalhamali@coveo.com", null)

      beforeGroup {
        whenever(repository.retrieve(message.executionType, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("does not modify the stage status") {
        verify(repository, never()).store(any())
      }

      it("does not run any stages") {
        verify(queue, never()).push(any<StartStage>())
      }
    }
  }

  describe("ignoring the failure of a failed stage with no downstream stages") {
    val pipeline = pipeline {
      application = "foo"
      id = "1234"
      status = TERMINAL
      startTime = clock.instant().minus(1, HOURS).toEpochMilli()
      endTime = clock.instant().minus(30, MINUTES).toEpochMilli()
      stage {
        refId = "1"
        singleTaskStage.plan(this)
        status = TERMINAL
        startTime = clock.instant().minus(1, HOURS).toEpochMilli()
        endTime = clock.instant().minus(59, MINUTES).toEpochMilli()
      }
    }
    val message = IgnoreStageFailure(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1").id, "aalhamali@coveo.com", null)

    beforeGroup {
      whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      subject.handle(message)
    }

    it("changes the stage's status to FAILED_CONTINUE") {
      verify(repository).storeStage(
        check {
          assertThat(it.id).isEqualTo(message.stageId)
          assertThat(it.status).isEqualTo(FAILED_CONTINUE)
        }
      )
    }

    it("recommences the pipeline") {
      verify(repository).updateStatus(
        check {
          assertThat(it.id).isEqualTo(pipeline.id)
          assertThat(it.status).isEqualTo(RUNNING)
        }
      )
    }

    it("attempts to start the next downstream stage, but finds none, so sends a CompleteExecution message") {
      verify(queue).push(any<CompleteExecution>())
    }
  }

  describe("ignoring the failure of a failed stage with downstream stages") {
    val pipeline = pipeline {
      application = "foo"
      id = "1234"
      status = TERMINAL
      startTime = clock.instant().minus(1, HOURS).toEpochMilli()
      endTime = clock.instant().minus(30, MINUTES).toEpochMilli()
      stage {
        refId = "1"
        singleTaskStage.plan(this)
        status = TERMINAL
        startTime = clock.instant().minus(1, HOURS).toEpochMilli()
        endTime = clock.instant().minus(59, MINUTES).toEpochMilli()
      }
      stage {
        refId = "2"
        requisiteStageRefIds = listOf("1")
        singleTaskStage.plan(this)
        status = NOT_STARTED
      }
      stage {
        refId = "3"
        requisiteStageRefIds = listOf("1")
        singleTaskStage.plan(this)
        status = NOT_STARTED
      }
      stage {
        refId = "4"
        requisiteStageRefIds = listOf("2")
        singleTaskStage.plan(this)
        status = NOT_STARTED
      }
    }
    val message = IgnoreStageFailure(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1").id, "aalhamali@coveo.com", null)

    beforeGroup {
      whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      subject.handle(message)
    }

    it("changes the stage's status to FAILED_CONTINUE") {
      verify(repository).storeStage(
        check {
          assertThat(it.id).isEqualTo(message.stageId)
          assertThat(it.status).isEqualTo(FAILED_CONTINUE)
        }
      )
    }

    it("recommences the pipeline") {
      verify(repository).updateStatus(
        check {
          assertThat(it.id).isEqualTo(pipeline.id)
          assertThat(it.status).isEqualTo(RUNNING)
        }
      )
    }

    it("sends a StartStage message for all downstream stages") {
      argumentCaptor<StartStage>().apply {
        verify(queue, times(2)).push(capture())
        assertThat(allValues.map { it.stageId }.toSet()).isEqualTo(pipeline.stages.filter{"1" in it.requisiteStageRefIds}.map { it.id }.toSet())
      }
    }
  }

  describe("ignoring the failure of a failed in a running pipeline") {
    val pipeline = pipeline {
      application = "foo"
      id = "1234"
      status = RUNNING
      startTime = clock.instant().minus(1, HOURS).toEpochMilli()
      endTime = clock.instant().minus(30, MINUTES).toEpochMilli()
      stage {
        refId = "1"
        singleTaskStage.plan(this)
        status = TERMINAL
        startTime = clock.instant().minus(1, HOURS).toEpochMilli()
        endTime = clock.instant().minus(59, MINUTES).toEpochMilli()
      }
      stage {
        refId = "2"
        requisiteStageRefIds = listOf("1")
        singleTaskStage.plan(this)
        status = NOT_STARTED
      }
      stage {
        refId = "3"
        singleTaskStage.plan(this)
        status = RUNNING
        startTime = clock.instant().minus(1, HOURS).toEpochMilli()
      }
    }
    val message = IgnoreStageFailure(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1").id, "aalhamali@coveo.com", null)

    beforeGroup {
      whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      subject.handle(message)
    }

    it("changes the stage's status to FAILED_CONTINUE") {
      verify(repository).storeStage(
        check {
          assertThat(it.id).isEqualTo(message.stageId)
          assertThat(it.status).isEqualTo(FAILED_CONTINUE)
        }
      )
    }

    it("sends a StartStage message for all downstream stages") {
      verify(queue).push(any<StartStage>())
    }
  }
})
