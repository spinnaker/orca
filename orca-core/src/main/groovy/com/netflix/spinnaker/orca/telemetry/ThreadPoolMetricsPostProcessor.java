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

package com.netflix.spinnaker.orca.telemetry;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import static java.lang.String.format;

@Component
public class ThreadPoolMetricsPostProcessor implements BeanPostProcessor {

  private final Registry registry;

  @Autowired
  public ThreadPoolMetricsPostProcessor(Registry registry) {this.registry = registry;}

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName) {
    if (bean instanceof ThreadPoolTaskExecutor) {
      applyThreadPoolMetrics((ThreadPoolTaskExecutor) bean, beanName);
    } else if (bean instanceof ThreadPoolTaskScheduler) {
      applyThreadPoolMetrics((ThreadPoolTaskScheduler) bean, beanName);
    }
    return bean;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    return bean;
  }

  private void applyThreadPoolMetrics(ThreadPoolTaskExecutor executor,
                                      String threadPoolName) {
    BiConsumer<String, Function<ThreadPoolExecutor, Integer>> createGauge =
      (name, fn) -> {
        Id id = registry
          .createId(format("threadpool.%s", name))
          .withTag("id", threadPoolName);

        registry.gauge(id, executor, ref -> fn.apply(ref.getThreadPoolExecutor()));
      };

    createGauge.accept("activeCount", ThreadPoolExecutor::getActiveCount);
    createGauge.accept("maximumPoolSize", ThreadPoolExecutor::getMaximumPoolSize);
    createGauge.accept("corePoolSize", ThreadPoolExecutor::getCorePoolSize);
    createGauge.accept("poolSize", ThreadPoolExecutor::getPoolSize);
    createGauge.accept("blockingQueueSize", e -> e.getQueue().size());
  }

  private void applyThreadPoolMetrics(ThreadPoolTaskScheduler executor,
                                      String threadPoolName) {
    BiConsumer<String, Function<ThreadPoolExecutor, Integer>> createGauge =
      (name, fn) -> {
        Id id = registry
          .createId(format("threadpool.%s", name))
          .withTag("id", threadPoolName);

        registry.gauge(id, executor, ref -> fn.apply(ref.getScheduledThreadPoolExecutor()));
      };

    createGauge.accept("activeCount", ThreadPoolExecutor::getActiveCount);
    createGauge.accept("maximumPoolSize", ThreadPoolExecutor::getMaximumPoolSize);
    createGauge.accept("corePoolSize", ThreadPoolExecutor::getCorePoolSize);
    createGauge.accept("poolSize", ThreadPoolExecutor::getPoolSize);
    createGauge.accept("blockingQueueSize", e -> e.getQueue().size());
  }
}
