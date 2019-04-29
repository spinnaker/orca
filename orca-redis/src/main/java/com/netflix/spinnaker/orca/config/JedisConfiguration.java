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
package com.netflix.spinnaker.orca.config;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import rx.Scheduler;
import rx.schedulers.Schedulers;

@Configuration
public class JedisConfiguration {

  @Bean("QueryAll")
  public ThreadPoolTaskExecutor queryAll() {
    return newFixedThreadPool(10);
  }

  @Bean
  public Scheduler queryAllScheduler(
    @Qualifier("QueryAll") ThreadPoolTaskExecutor executor) {
    return Schedulers.from(executor);
  }

  @Bean("QueryByApp")
  public ThreadPoolTaskExecutor queryByApp(
    @Value("${threadPool.executionRepository:150}") int threadPoolSize
  ) {
    return newFixedThreadPool(threadPoolSize);
  }

  @Bean
  public Scheduler queryByAppScheduler(
    @Qualifier("QueryByApp") ThreadPoolTaskExecutor executor) {
    return Schedulers.from(executor);
  }

  private static ThreadPoolTaskExecutor newFixedThreadPool(int threadPoolSize) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(threadPoolSize);
    executor.setMaxPoolSize(threadPoolSize);
    return executor;
  }
}
