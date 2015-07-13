/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.persistence.jedis

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spectator.api.ValueFunction
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionStore
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import rx.Observable
import rx.Scheduler
import rx.schedulers.Schedulers

@Slf4j
@CompileStatic
class JedisBackedExecutionStore<T extends Execution> implements ExecutionStore<T> {

  static ExecutionStore<Pipeline> pipelineStore(Pool<Jedis> jedisPool,
                                                ObjectMapper mapper,
                                                int threadPoolSize,
                                                int threadPoolChunkSize,
                                                ExtendedRegistry extendedRegistry) {
    new JedisBackedExecutionStore(PIPELINE,
                                  Pipeline,
                                  jedisPool,
                                  mapper,
                                  threadPoolSize,
                                  threadPoolChunkSize,
                                  extendedRegistry)
  }

  static ExecutionStore<Orchestration> orchestrationStore(Pool<Jedis> jedisPool,
                                                          ObjectMapper mapper,
                                                          int threadPoolSize,
                                                          int threadPoolChunkSize,
                                                          ExtendedRegistry extendedRegistry) {
    new JedisBackedExecutionStore(ORCHESTRATION,
                                  Orchestration,
                                  jedisPool,
                                  mapper,
                                  threadPoolSize,
                                  threadPoolChunkSize,
                                  extendedRegistry)
  }

  private final Executor fetchAllExecutor = Executors.newFixedThreadPool(10)
  private final Executor fetchApplicationExecutor
  private final int chunkSize

  private final String prefix
  private final Class<T> executionClass
  private final Pool<Jedis> jedisPool
  private final ObjectMapper mapper

  private JedisBackedExecutionStore(String prefix,
                                    Class<T> executionClass,
                                    Pool<Jedis> jedisPool,
                                    ObjectMapper mapper,
                                    int threadPoolSize,
                                    int threadPoolChunkSize,
                                    ExtendedRegistry extendedRegistry) {
    this.prefix = prefix
    this.executionClass = executionClass
    this.jedisPool = jedisPool
    this.mapper = mapper
    this.fetchApplicationExecutor = Executors.newFixedThreadPool(threadPoolSize)
    this.chunkSize = threadPoolChunkSize

    def createGuage = { Executor executor, String threadPoolName, String valueName ->
      def id = extendedRegistry
        .createId("threadpool.${valueName}" as String)
        .withTag("id", threadPoolName)

      extendedRegistry.gauge(id, executor, new ValueFunction() {
        @Override
        @CompileDynamic
        double apply(Object ref) {
          ref."${valueName}"
        }
      })
    }

    createGuage.call(fetchAllExecutor, "${getClass().simpleName}-fetchAll", "activeCount")
    createGuage.call(fetchAllExecutor, "${getClass().simpleName}-fetchAll", "maximumPoolSize")
    createGuage.call(fetchAllExecutor, "${getClass().simpleName}-fetchAll", "corePoolSize")
    createGuage.call(fetchAllExecutor, "${getClass().simpleName}-fetchAll", "poolSize")

    createGuage.call(fetchApplicationExecutor, "${getClass().simpleName}-fetchApplication", "activeCount")
    createGuage.call(fetchApplicationExecutor, "${getClass().simpleName}-fetchApplication", "maximumPoolSize")
    createGuage.call(fetchApplicationExecutor, "${getClass().simpleName}-fetchApplication", "corePoolSize")
    createGuage.call(fetchApplicationExecutor, "${getClass().simpleName}-fetchApplication", "poolSize")
  }

  @Override
  Observable<T> all() {
    retrieveObservable(alljobsKey, Schedulers.from(fetchAllExecutor), chunkSize)
  }

  @Override
  Observable<T> allForApplication(String application) {
    retrieveObservable(getAppKey(application), Schedulers.from(fetchApplicationExecutor), chunkSize)
  }

  @Override
  void store(T execution) {
    withConnection { Jedis jedis ->
      if (!execution.id) {
        execution.id = UUID.randomUUID().toString()
        jedis.sadd(alljobsKey, execution.id)
        def appKey = getAppKey(execution.application)
        jedis.sadd(appKey, execution.id)
      }
      def json = mapper.writeValueAsString(execution)

      def key = "${prefix}:$execution.id"
      jedis.hset(key, "config", json)
    }
  }

  @Override
  void storeStage(Stage<T> stage) {
    def json = mapper.writeValueAsString(stage)

    def key = "${prefix}:stage:${stage.id}"
    withConnection { Jedis jedis ->
      jedis.hset(key, "config", json)
    }
  }

  @Override
  void delete(String id) {
    def key = "${prefix}:$id"
    def storePrefix = prefix
    withConnection { Jedis jedis ->
      try {
        T item = retrieve(id)
        def appKey = getAppKey(item.application)
        jedis.srem(appKey, id)

        item.stages.each { Stage stage ->
          def stageKey = "${storePrefix}:stage:${stage.id}"
          jedis.hdel(stageKey, "config")
        }
      } catch (ExecutionNotFoundException ignored) {
        // do nothing
      } finally {
        jedis.hdel(key, "config")
        jedis.srem(alljobsKey, id)
      }
    }
  }

  @Override
  Stage<T> retrieveStage(String id) {
    def key = "${prefix}:stage:${id}"
    withConnection { Jedis jedis ->
      return jedis.exists(key) ? mapper.readValue(jedis.hget(key, "config"), Stage) : null
    }
  }

  @Override
  List<Stage<T>> retrieveStages(List<String> ids) {
    def keyPrefix = prefix
    withConnection { Jedis jedis ->
      def pipeline = jedis.pipelined()
      ids.each { id ->
        pipeline.hget("${keyPrefix}:stage:${id}", "config")
      }
      def results = pipeline.syncAndReturnAll()
      return results.collect { it ? mapper.readValue(it as String, Stage) : null }
    }
  }

  @Override
  @CompileDynamic
  T retrieve(String id) throws ExecutionNotFoundException {
    def key = "${prefix}:$id"
    withConnection { Jedis jedis ->
      if (jedis.exists(key)) {
        def json = jedis.hget(key, "config")
        def execution = mapper.readValue(json, executionClass)

        def reorderedStages = []
        execution.stages.findAll { it.parentStageId == null }.each { Stage<T> parentStage ->
          reorderedStages << parentStage

          def children = new LinkedList<Stage<T>>(execution.stages.findAll { it.parentStageId == parentStage.id })
          while (!children.isEmpty()) {
            def child = children.remove(0)
            children.addAll(0, execution.stages.findAll { it.parentStageId == child.id })
            reorderedStages << child
          }
        }
        def retrievedStages = retrieveStages(reorderedStages.collect { it.id })
        execution.stages = reorderedStages.collect {
          def explicitStage = retrievedStages.find { stage -> stage?.id == it.id } ?: it
          explicitStage.execution = execution
          return explicitStage
        }
        execution
      } else {
        throw new ExecutionNotFoundException("No ${prefix} execution found for $id")
      }
    }
  }

  private Observable<T> retrieveObservable(String key, Scheduler scheduler, int chunkSize) {
    Observable.just(key)
              .flatMapIterable(this.&getIds)
              .buffer(chunkSize)
              .flatMap { Iterable<String> ids -> getAll(key, ids).subscribeOn(scheduler) }
  }

  private Iterable<String> getIds(String key) {
    withConnection { Jedis jedis -> jedis.smembers(key) }
  }

  private Observable<T> getAll(String lookupKey, Iterable<String> ids) {
    Observable.from(ids)
              .flatMap { String executionId ->
      try {
        return Observable.just(retrieve(executionId))
      } catch (ExecutionNotFoundException ignored) {
        log.info("Execution (${executionId}) does not exist")
        delete(executionId)
        withConnection { Jedis jedis ->
          jedis.srem(lookupKey, executionId)
        }
      } catch (Exception e) {
        log.error("Failed to retrieve execution '${executionId}', message: ${e.message}")
      }
      return Observable.empty()
    }
  }

  private final <T> T withConnection(Closure<T> action) {
    jedisPool.resource.withCloseable(action)
  }

  private String getAlljobsKey() {
    "allJobs:${prefix}"
  }

  private String getAppKey(String app) {
    "${prefix}:app:${app}"
  }
}
