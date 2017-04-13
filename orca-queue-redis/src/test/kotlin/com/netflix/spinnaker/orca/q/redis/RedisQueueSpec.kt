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

package com.netflix.spinnaker.orca.q.redis

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.orca.q.QueueSpec
import com.netflix.spinnaker.orca.q.QueueSpec.Companion.clock
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith

@RunWith(JUnitPlatform::class)
class RedisQueueSpec : QueueSpec<RedisQueue>(::createQueue, ::triggerRedeliveryCheck, ::shutdownCallback)

// surely this is bad
private var redis: EmbeddedRedis? = null

private fun createQueue(): RedisQueue {
  redis = EmbeddedRedis.embed()
  return RedisQueue("test", redis!!.pool, clock)
}

private fun triggerRedeliveryCheck(queue: RedisQueue) = queue.redeliver()

private fun shutdownCallback() {
  println("shutting down the redis")
  redis?.destroy()
}
