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
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.q.Message

internal val keikoToOrcaTypes = mapOf<String, String>(
  "startTask" to ".StartTask",
  "completeTask" to ".CompleteTask",
  "pauseTask" to ".PauseTask",
  "resumeTask" to ".ResumeTask",
  "runTask" to ".RunTask",
  "startStage" to ".StartStage",
  "continueParentStage" to ".ContinueParentStage",
  "completeStage" to ".CompleteStage",
  "skipStage" to ".SkipStage",
  "abortStage" to ".AbortStage",
  "pauseStage" to ".PauseStage",
  "restartStage" to ".RestartStage",
  "resumeStage" to ".ResumeStage",
  "cancelStage" to ".CancelStage",
  "startExecution" to ".StartExecution",
  "rescheduleExecution" to ".RescheduleExecution",
  "completeExecution" to ".CompleteExecution",
  "resumeExecution" to ".ResumeExecution",
  "cancelExecution" to ".CancelExecution",
  "invalidExecutionId" to ".InvalidExecutionId",
  "invalidStageId" to ".InvalidStageId",
  "invalidTaskId" to ".InvalidTaskId",
  "invalidTaskType" to ".InvalidTaskType",
  "noDownstreamTasks" to ".NoDownstreamTasks",
  "totalThrottleTime" to ".TotalThrottleTimeAttribute",
  "deadMessage" to ".handler.DeadMessageAttribute",
  "maxAttempts" to ".MaxAttemptsAttribute",
  "attempts" to ".AttemptsAttribute"
)

class KeikoMessageDeserializer : JsonDeserializer<Message>() {

  val mapper = ObjectMapper()
    .registerModule(KotlinModule())
    .disable(FAIL_ON_UNKNOWN_PROPERTIES)
    .registerModule(
      SimpleModule()
      .addSerializer(ExecutionTypeSerializer())
      .addDeserializer(Execution.ExecutionType::class.java, ExecutionTypeDeserializer())
    )

  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Message {
    val m = p.readValueAs(MutableMap::class.java) as MutableMap<String, Any?>

    val kind = m["kind"] ?: return mapper.readValue(p.valueAsString, Message::class.java)

    if (!keikoToOrcaTypes.containsKey(kind)) {
      throw IllegalStateException("Unknown kind $kind")
    }

    replaceKind(m)
    if (m.containsKey("attributes")) {
      (m["attributes"] as List<MutableMap<String, Any?>>).forEach { replaceKind(it) }
    }

    return mapper.convertValue(m)
  }

  private fun replaceKind(m: MutableMap<String, Any?>) {
    if (m.containsKey("kind")) {
      m["@class"] = keikoToOrcaTypes[m["kind"]]
      m["@c"] = keikoToOrcaTypes[m["kind"]]
      m.remove("kind")
    }
  }
}
