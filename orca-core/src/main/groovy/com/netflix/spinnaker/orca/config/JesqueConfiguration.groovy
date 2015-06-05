/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.config

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.JesqueActivator
import net.greghaines.jesque.Config
import net.greghaines.jesque.ConfigBuilder
import net.greghaines.jesque.client.Client
import net.greghaines.jesque.client.ClientPoolImpl
import net.greghaines.jesque.worker.WorkerPool
import net.lariverosc.jesquespring.SpringWorkerFactory
import net.lariverosc.jesquespring.SpringWorkerPool
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.util.Pool

@Configuration
@Slf4j
@CompileStatic
class JesqueConfiguration {
  @Bean
  @ConditionalOnMissingBean(Pool)
  Pool<Jedis> jedisPool(@Value('${redis.connection}') String connection) {
    def jedisConnection = URI.create(connection)
    final JedisPool pool
    if (jedisConnection.userInfo != null) {
      pool = new JedisPool(jedisConnection)
    } else {
      pool = new JedisPool(jedisConnection.host, jedisConnection.port == -1 ? 6379 : jedisConnection.port)
    }
    return pool
  }

  @Bean
  @ConditionalOnMissingBean(Config)
  Config jesqueConfig(@Value('${redis.connection}') String connection) {
    def jedisConnection = URI.create(connection)
    new ConfigBuilder()
      .withHost(jedisConnection.host)
      .withPort(jedisConnection.port)
      .build()
  }

  @Bean
  Client jesqueClient(Config jesqueConfig, Pool<Jedis> jedisPool) {
    new ClientPoolImpl(jesqueConfig, jedisPool)
  }

  @Bean
  SpringWorkerFactory workerFactory(Config jesqueConfig, List<AbstractPollingNotificationAgent> notificationAgents) {
    new SpringWorkerFactory(jesqueConfig, notificationAgents.collect {
      it.notificationType
    })
  }

  @Bean(initMethod = "init", destroyMethod = "destroy")
  SpringWorkerPool workerPool(SpringWorkerFactory workerFactory,
                              @Value('${jesque.numWorkers:1}') int numWorkers) {
    def pool = new SpringWorkerPool(workerFactory, numWorkers)
    pool.togglePause(true)
    log.info "Jesque worker pool started dormant"
    return pool
  }

  @Bean
  JesqueActivator jesqueActivator(WorkerPool workerPool) {
    new JesqueActivator(workerPool)
  }
}
