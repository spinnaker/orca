/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.q.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.tasks.PreconditionTask
import com.netflix.spinnaker.orca.q.*
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.lifecycle.CachingMode
import org.jetbrains.spek.subject.SubjectSpek
import redis.clients.jedis.Jedis
import redis.clients.util.Pool

object RedisDeadLetterRedriveCommandTest : SubjectSpek<RedisDeadLetterRedriveCommand>({

  val queue: Queue = mock()
  val redis: Jedis = mock()
  val redisPool: Pool<Jedis> = mock()
  val executionRepository: ExecutionRepository = mock()

  val mapper = ObjectMapper().apply {
    registerModule(KotlinModule())
  }

  subject(CachingMode.GROUP) {
    whenever(redisPool.resource).thenReturn(redis)
    RedisDeadLetterRedriveCommand("dlq", queue, redisPool, executionRepository)
  }

  fun resetMocks() = reset(queue, redis, executionRepository)

  describe("redriving dead letter queue messages") {
    afterGroup(::resetMocks)

    val messages = setOf<Message>(
      StartTask(Pipeline::class.java, "1", "orca", "stageId", "taskId"),
      CompleteTask(Pipeline::class.java, "2", "orca", "stageId", "taskId", ExecutionStatus.SUCCEEDED),
      PauseTask(Pipeline::class.java, "3", "orca", "stageId", "taskId"),
      RunTask(Pipeline::class.java, "4", "orca", "stageId", "taskId", PreconditionTask::class.java)
    )

    whenever(redis.zrangeByScore(eq("dlq.messages"), any<String>(), any())).thenReturn(messages.map { mapper.writeValueAsString(it) }.toMutableSet())
    whenever(executionRepository.retrievePipeline(any())).thenAnswer {
      Pipeline("orca", it.getArgument(0)).let {
        val s = Stage<Pipeline>(it, "wait", mutableMapOf())
        s.id = "stageId"
        s.status = ExecutionStatus.TERMINAL
        it.stages.add(s)
        it
      }
    }

    action("command is run") {
      subject.redrive(null, null)
    }

    it("updates execution and stage statuses") {
      val numMessages = messages.filter { it !is RunTask }.count()
      verify(executionRepository, times(numMessages)).storeStage(any())
      verify(executionRepository, times(numMessages)).updateStatus(any(), eq(ExecutionStatus.RUNNING))
    }

    it("puts message back onto the queue") {
      messages.filter { it !is RunTask }.forEach { verify(queue).push(it) }
    }
  }
})
