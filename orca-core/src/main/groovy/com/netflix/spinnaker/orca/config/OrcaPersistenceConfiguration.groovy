package com.netflix.spinnaker.orca.config

import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionStore
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisBackedExecutionStore
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisPipelineStack
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Jedis
import redis.clients.util.Pool

@Configuration
@CompileStatic
class OrcaPersistenceConfiguration {
  @Bean ExecutionStore<Orchestration> orchestrationStore(Pool<Jedis> jedisPool,
                                                         @Value('${threadPools.orchestrationStore:150}') int threadPoolSize,
                                                         @Value('${threadPools.orchestrationStore:75}') int threadPoolChunkSize,
                                                         ExtendedRegistry extendedRegistry) {
    JedisBackedExecutionStore.orchestrationStore(jedisPool,
                                                 new OrcaObjectMapper(),
                                                 threadPoolSize,
                                                 threadPoolChunkSize,
                                                 extendedRegistry)
  }

  @Bean ExecutionStore<Pipeline> pipelineStore(Pool<Jedis> jedisPool,
                                               @Value('${threadPools.pipelineStore:150}') int threadPoolSize,
                                               @Value('${threadPools.pipelineStore:75}') int threadPoolChunkSize,
                                               ExtendedRegistry extendedRegistry) {
    JedisBackedExecutionStore.pipelineStore(jedisPool,
                                            new OrcaObjectMapper(),
                                            threadPoolSize,
                                            threadPoolChunkSize,
                                            extendedRegistry)
  }

  @Bean JedisPipelineStack pipelineStack(Pool<Jedis> jedisPool) {
    new JedisPipelineStack("PIPELINE_QUEUE", jedisPool)
  }
}
