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

import com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.q.AbortStage
import com.netflix.spinnaker.orca.q.CompleteExecution
import com.netflix.spinnaker.orca.q.CompleteTask
import com.netflix.spinnaker.orca.q.DummyTask
import com.netflix.spinnaker.orca.q.RunTask
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek

object DeadMessageHandlerTest : SubjectSpek<DeadMessageHandler>({

  val queue: Queue = mock()

  subject(GROUP) {
    DeadMessageHandler()
  }

  fun resetMocks() = reset(queue)

  describe("handling an execution level message") {
    val message = StartExecution(PIPELINE, "1", "spinnaker")

    afterGroup(::resetMocks)

    on("receiving a message") {
      subject.invoke(queue, message)
    }

    it("terminates the execution") {
      verify(queue).push(CompleteExecution(message))
    }
  }

  describe("handling a stage level message") {
    val message = StartStage(PIPELINE, "1", "spinnaker", "1")

    afterGroup(::resetMocks)

    on("receiving a message") {
      subject.invoke(queue, message)
    }

    it("aborts the stage") {
      verify(queue).push(AbortStage(message))
    }
  }

  describe("handling a task level message") {
    val message = RunTask(PIPELINE, "1", "spinnaker", "1", "1", DummyTask::class.java)

    afterGroup(::resetMocks)

    on("receiving a message") {
      subject.invoke(queue, message)
    }

    it("terminates the task") {
      verify(queue).push(CompleteTask(message, TERMINAL))
    }
  }

  describe("handling a message that was previously dead-lettered") {
    val message = CompleteExecution(PIPELINE, "1", "spinnaker").apply {
      setAttribute(DeadMessageAttribute)
    }

    afterGroup(::resetMocks)

    on("receiving a message") {
      subject.invoke(queue, message)
    }

    it("does nothing") {
      verifyZeroInteractions(queue)
    }
  }
})
