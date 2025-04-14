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

import com.netflix.spinnaker.orca.TaskResolver
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.PAUSED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.api.test.pipeline
import com.netflix.spinnaker.orca.api.test.stage
import com.netflix.spinnaker.orca.api.test.task
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.tasks.NoOpTask
import com.netflix.spinnaker.orca.q.ResumeTask
import com.netflix.spinnaker.orca.q.RunTask
import com.netflix.spinnaker.orca.q.TasksProvider
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek

object ResumeTaskHandlerTest : SubjectSpek<ResumeTaskHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val taskResolver = TaskResolver(TasksProvider(emptyList()))

  subject(GROUP) {
    ResumeTaskHandler(queue, repository, taskResolver)
  }

  fun resetMocks() = reset(queue, repository)

  describe("resuming a paused execution") {
    val pipeline = pipeline {
      application = "spinnaker"
      status = RUNNING
      stage {
        refId = "1"
        status = RUNNING
        task {
          id = "1"
          status = PAUSED
        }
      }
    }
    val message = ResumeTask(pipeline.type, pipeline.id, pipeline.application, pipeline.stages.first().id, "1")

    beforeGroup {
      whenever(repository.retrieve(eq(PIPELINE), eq(pipeline.id), any())) doReturn pipeline
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      subject.handle(message)
    }

    it("sets the stage status to running") {
      verify(repository).storeStage(
        check {
          assertThat(it.id).isEqualTo(message.stageId)
          assertThat(it.tasks.first().status).isEqualTo(RUNNING)
        }
      )
    }

    it("resumes all paused tasks") {
      verify(queue).push(RunTask(message, NoOpTask::class.java))
      verifyNoMoreInteractions(queue)
    }
  }
})
