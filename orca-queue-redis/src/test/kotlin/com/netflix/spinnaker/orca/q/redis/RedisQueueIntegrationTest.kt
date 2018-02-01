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

import com.netflix.spinnaker.config.RedisOrcaQueueConfiguration
import com.netflix.spinnaker.orca.q.QueueIntegrationTest
import com.netflix.spinnaker.orca.q.TestConfig
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import org.junit.runner.RunWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner

/**
 * This just runs [QueueIntegrationTest] with a [com.netflix.spinnaker.q.redis.RedisQueue].
 */
@RunWith(SpringRunner::class)
@SpringBootTest(
  classes = [
    EmbeddedRedisConfiguration::class,
    TestConfig::class,
    RedisOrcaQueueConfiguration::class
  ],
  properties = [
    "queue.retry.delay.ms=10",
    "logging.level.root=ERROR",
    "logging.level.org.springframework.test=ERROR",
    "logging.level.com.netflix.spinnaker=FATAL"
  ])
class RedisQueueIntegrationTest : QueueIntegrationTest()
