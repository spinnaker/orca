/*
 * Copyright 2023 Armory, Inc.
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

package com.netflix.spinnaker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.jedis.RedisClientSelector;
import com.netflix.spinnaker.kork.jedis.lock.RedisLockManager;
import com.netflix.spinnaker.kork.lock.LockManager;
import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LockConfiguration {

  @Bean
  public LockManager lockManager(
      Clock clock,
      Registry registry,
      ObjectMapper mapper,
      RedisClientSelector redisClientSelector) {
    return new RedisLockManager(
        "RedisLockManager",
        clock,
        registry,
        mapper,
        redisClientSelector.primary("executionRepository"),
        Optional.empty(),
        Optional.empty());
  }
}
