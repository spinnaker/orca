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

package com.netflix.spinnaker.orca.test.redis

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import net.greghaines.jesque.Config
import net.greghaines.jesque.ConfigBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Jedis
import redis.clients.util.Pool

@Configuration
class EmbeddedRedisConfiguration {

  @Bean
  EmbeddedRedis redisServer() {
    def redis = EmbeddedRedis.embed()
    redis.jedis.withCloseable { Jedis jedis ->
      jedis.flushAll()
    }
    return redis
  }

  @Bean
  Config jesqueConfig() {
    new ConfigBuilder().withHost("127.0.0.1")
                       .withPort(redisServer().port)
                       .build()
  }

  @Bean
  Pool<Jedis> jedisPool() {
    redisServer().pool
  }
}
