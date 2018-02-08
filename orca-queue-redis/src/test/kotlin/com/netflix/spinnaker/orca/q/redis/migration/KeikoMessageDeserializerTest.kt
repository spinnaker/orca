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
package com.netflix.spinnaker.orca.q.redis.migration

import com.fasterxml.jackson.core.JsonParser
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.q.AttemptsAttribute
import com.netflix.spinnaker.orca.q.DummyTask
import com.netflix.spinnaker.orca.q.RunTask
import com.netflix.spinnaker.spek.shouldEqual
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.lifecycle.CachingMode
import org.jetbrains.spek.subject.SubjectSpek

object KeikoMessageDeserializerTest : SubjectSpek<KeikoMessageDeserializer>({

  val jsonParser: JsonParser = mock()

  subject(CachingMode.GROUP) {
    KeikoMessageDeserializer()
  }

  describe("handling a keiko message") {
    val message = mapOf(
      "kind" to "runTask",
      "executionType" to "PIPELINE",
      "executionId" to "abcd",
      "application" to "spindemo",
      "stageId" to "abcd",
      "taskId" to "1",
      "taskType" to "com.netflix.spinnaker.orca.q.DummyTask",
      "attributes" to listOf(
        mapOf(
          "kind" to "attempts",
          "attempts" to 39
        )
      )
    )

    val jsonParser: JsonParser = mock()
    whenever(jsonParser.readValueAs(any<Class<*>>())).thenReturn(message)
    whenever(jsonParser.getValueAsString()).thenReturn(subject.mapper.writeValueAsString(message))

    val result = subject.deserialize(jsonParser, mock())

    result shouldEqual RunTask(
      executionType = PIPELINE,
      executionId = "abcd",
      application = "spindemo",
      stageId = "abcd",
      taskId = "1",
      taskType = DummyTask::class.java
    ).apply {
      setAttribute(AttemptsAttribute(39))
    }
  }

  describe("handling the happy path") {
    val message = mapOf(
      "@class" to ".RunTask",
      "executionType" to "PIPELINE",
      "executionId" to "abcd",
      "application" to "spindemo",
      "stageId" to "abcd",
      "taskId" to "1",
      "taskType" to "com.netflix.spinnaker.orca.q.DummyTask",
      "attributes" to listOf(
        mapOf(
          "@class" to ".AttemptsAttribute",
          "attempts" to 39
        )
      )
    )

    val jsonParser: JsonParser = mock()
    whenever(jsonParser.readValueAs(any<Class<*>>())).thenReturn(message)
    whenever(jsonParser.getValueAsString()).thenReturn(subject.mapper.writeValueAsString(message))

    val result = subject.deserialize(jsonParser, mock())

    result shouldEqual RunTask(
      executionType = PIPELINE,
      executionId = "abcd",
      application = "spindemo",
      stageId = "abcd",
      taskId = "1",
      taskType = DummyTask::class.java
    ).apply {
      setAttribute(AttemptsAttribute(39))
    }

  }
})
