/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws

import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForRequiredInstancesDownTask
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Jedis
import redis.clients.util.Pool

@Configuration
class NetflixAWSConfiguration {

  @Value('${tasks.useWaitForAllNetflixAWSInstancesDownTask:false}')
  boolean useWaitForAllNetflixAWSInstancesDownTask

  @Bean
  Class<? extends Task> waitForAllInstancesDownOnDisableTaskType() {
    return useWaitForAllNetflixAWSInstancesDownTask ? WaitForAllNetflixAWSInstancesDownTask : WaitForRequiredInstancesDownTask
  }

  @Bean
  RedisClientDelegate redisClientDelegate(Pool<Jedis> jedisPool) {
    return new JedisClientDelegate("primaryDefault", jedisPool)
  }
}
