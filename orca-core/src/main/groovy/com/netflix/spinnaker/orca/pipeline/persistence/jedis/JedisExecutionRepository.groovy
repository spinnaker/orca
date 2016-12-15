/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.persistence.jedis

import java.util.function.Function
import groovy.transform.CompileStatic
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.*
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import groovy.transform.CompileDynamic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCommands
import redis.clients.jedis.Response
import redis.clients.util.Pool
import rx.Observable
import rx.Scheduler
import rx.functions.Func1
import rx.schedulers.Schedulers
import static com.google.common.base.Predicates.notNull
import static com.google.common.collect.Maps.filterValues
import static java.lang.System.currentTimeMillis

@Component
@Slf4j
@CompileStatic
class JedisExecutionRepository implements ExecutionRepository {

  private static final TypeReference<List<Task>> LIST_OF_TASKS = new TypeReference<List<Task>>() {}
  private static final TypeReference<Map<String, Object>> MAP_STRING_TO_OBJECT = new TypeReference<Map<String, Object>>() {}
  private final Pool<Jedis> jedisPool
  private final Optional<Pool<Jedis>> jedisPoolPrevious
  private final ObjectMapper mapper = new OrcaObjectMapper()
  private final int chunkSize
  private final Scheduler queryAllScheduler
  private final Scheduler queryByAppScheduler

  @Autowired
  StageNavigator stageNavigator

  @Autowired
  JedisExecutionRepository(
    Registry registry,
    Pool<Jedis> jedisPool,
    Optional<Pool<Jedis>> jedisPoolPrevious,
    @Value('${threadPool.executionRepository:150}') int threadPoolSize,
    @Value('${chunkSize.executionRepository:75}') int threadPoolChunkSize
  ) {
    this(
      jedisPool,
      jedisPoolPrevious,
      Schedulers.from(newFixedThreadPool(registry, 10, "QueryAll")),
      Schedulers.from(newFixedThreadPool(registry, threadPoolSize, "QueryByApp")),
      threadPoolChunkSize
    )
  }

  JedisExecutionRepository(
    Registry registry,
    Pool<Jedis> jedisPool,
    int threadPoolSize,
    int threadPoolChunkSize
  ) {
    this(
      jedisPool,
      Optional.empty(),
      Schedulers.from(newFixedThreadPool(registry, 10, "QueryAll")),
      Schedulers.from(newFixedThreadPool(registry, threadPoolSize, "QueryByApp")),
      threadPoolChunkSize
    )
  }

  JedisExecutionRepository(
    Pool<Jedis> jedisPool,
    Optional<Pool<Jedis>> jedisPoolPrevious,
    Scheduler queryAllScheduler,
    Scheduler queryByAppScheduler,
    int threadPoolChunkSize
  ) {
    this.jedisPool = jedisPool
    this.jedisPoolPrevious = jedisPoolPrevious
    this.queryAllScheduler = queryAllScheduler
    this.queryByAppScheduler = queryByAppScheduler
    this.chunkSize = threadPoolChunkSize
  }

  @Override
  void store(Orchestration orchestration) {
    withJedis(getJedisPoolForId(orchestration.id)) { Jedis jedis ->
      storeExecutionInternal(jedis, orchestration)
    }
  }

  @Override
  void store(Pipeline pipeline) {
    withJedis(getJedisPoolForId(pipeline.id)) { Jedis jedis ->
      storeExecutionInternal(jedis, pipeline)
      jedis.zadd(executionsByPipelineKey(pipeline.pipelineConfigId), pipeline.buildTime, pipeline.id)
    }
  }

  @Override
  void storeExecutionContext(String id, Map<String, Object> context) {
    String key = fetchKey(id)
    withJedis(getJedisPoolForId(key)) { Jedis jedis ->
      jedis.hset(key, "context", mapper.writeValueAsString(context))
    }
  }

  @Override
  void cancel(String id) {
    cancel(id, null, null)
  }

  @Override
  void cancel(String id, String user, String reason) {
    String key = fetchKey(id)
    withJedis(getJedisPoolForId(key)) { Jedis jedis ->
      def data = [canceled: "true"]
      if (user) {
        data.canceledBy = user
      }
      if (reason) {
        data.cancellationReason = reason
      }
      def currentStatus = ExecutionStatus.valueOf(jedis.hget(key, "status"))
      if (currentStatus == ExecutionStatus.NOT_STARTED) {
        data.status = ExecutionStatus.CANCELED.name()
      }
      jedis.hmset(key, data)
    }
  }

  @Override
  boolean isCanceled(String id) {
    String key = fetchKey(id)
    withJedis(getJedisPoolForId(key)) { Jedis jedis ->
      Boolean.valueOf(jedis.hget(key, "canceled"))
    }
  }

  @Override
  void pause(String id, String user) {
    String key = fetchKey(id)
    withJedis(getJedisPoolForId(key)) { Jedis jedis ->
      def currentStatus = ExecutionStatus.valueOf(jedis.hget(key, "status"))
      if (currentStatus != ExecutionStatus.RUNNING) {
        throw new IllegalStateException("Unable to pause pipeline that is not RUNNING (executionId: ${id}, currentStatus: ${currentStatus})")
      }

      def pausedDetails = new Execution.PausedDetails(
        pausedBy: user,
        pauseTime: currentTimeMillis()
      )

      def data = [
        paused: mapper.writeValueAsString(pausedDetails),
        status: ExecutionStatus.PAUSED.toString()
      ]
      jedis.hmset(key, data)
    }
  }

  @Override
  void resume(String id, String user, boolean ignoreCurrentStatus = false) {
    String key = fetchKey(id)
    withJedis(getJedisPoolForId(key)) { Jedis jedis ->
      def currentStatus = ExecutionStatus.valueOf(jedis.hget(key, "status"))
      if (!ignoreCurrentStatus && currentStatus != ExecutionStatus.PAUSED) {
        throw new IllegalStateException("Unable to resume pipeline that is not PAUSED (executionId: ${id}, currentStatus: ${currentStatus})")
      }

      def pausedDetails = mapper.readValue(jedis.hget(key, "paused"), Execution.PausedDetails)
      pausedDetails.resumedBy = user
      pausedDetails.resumeTime = currentTimeMillis()

      def data = [
        paused: mapper.writeValueAsString(pausedDetails),
        status: ExecutionStatus.RUNNING.toString()
      ]
      jedis.hmset(key, data)
    }
  }

  @Override
  void updateStatus(String id, ExecutionStatus status) {
    String key = fetchKey(id)
    withJedis(getJedisPoolForId(key)) { Jedis jedis ->
      Map<String, String> map = [status: status.name()]
      if (status == ExecutionStatus.RUNNING) {
        map.startTime = String.valueOf(currentTimeMillis())
      } else if (status.complete) {
        map.endTime = String.valueOf(currentTimeMillis())
      }
      jedis.hmset(key, map)
    }
  }

  @Override
  void storeStage(PipelineStage stage) {
    withJedis(getJedisPoolForId("pipeline:${stage.execution.id}" )) { Jedis jedis ->
      storeStageInternal(jedis, Pipeline, stage)
    }
  }

  @Override
  void storeStage(Stage stage) {
    if (stage instanceof OrchestrationStage) {
      storeStage((OrchestrationStage) stage)
    } else {
      storeStage((PipelineStage) stage)
    }
  }

  @Override
  void storeStage(OrchestrationStage stage) {
    withJedis(getJedisPoolForId("orchestration:${stage.execution.id}")) { Jedis jedis ->
      storeStageInternal(jedis, Orchestration, stage)
    }
  }

  @Override
  Pipeline retrievePipeline(String id) {
    withJedis(getJedisPoolForId("pipeline:${id}")) { Jedis jedis ->
      retrieveInternal(jedis, Pipeline, id)
    }
  }

  @Override
  void deletePipeline(String id) {
    withJedis(getJedisPoolForId("pipeline:${id}")) { Jedis jedis ->
      deleteInternal(jedis, Pipeline, id)
    }
  }

  @Override
  Observable<Pipeline> retrievePipelines() {
    return Observable.merge(allJedis().collect {all(Pipeline, it)})
  }

  @Override
  Observable<Pipeline> retrievePipelinesForApplication(String application) {
    return Observable.merge(allJedis().collect {allForApplication(Pipeline, application, it)})
  }

  @Override
  @CompileDynamic
  Observable<Pipeline> retrievePipelinesForPipelineConfigId(String pipelineConfigId,
                                                            ExecutionCriteria criteria) {
    /**
     * Fetch pipeline ids from the primary redis (and secondary if configured)
     */
    Map<Pool<Jedis>, List<String>> filteredPipelineIdsByJedis = [:].withDefault { [] }
    if (criteria.statuses) {
      allJedis().each { Pool<Jedis> jedisPool ->
        withJedis(jedisPool) { Jedis jedis ->
          def pipelineKeys = jedis.zrevrange(executionsByPipelineKey(pipelineConfigId), 0, -1)
          def allowedExecutionStatuses = criteria.statuses*.toString() as Set<String>

          def pipeline = jedis.pipelined()
          def fetches = pipelineKeys.collect {
            pipeline.hget("pipeline:${it}" as String, "status")
          }
          pipeline.sync()

          fetches.eachWithIndex { Response<String> entry, int index ->
            if (allowedExecutionStatuses.contains(entry.get())) {
              filteredPipelineIdsByJedis[jedisPool] << pipelineKeys[index]
            }
          }
        }
      }
    }

    def fnBuilder = { Pool<Jedis> targetPool, List<String> pipelineIds ->
      new Func1<String, Iterable<String>>() {
        @Override
        Iterable<String> call(String key) {
          withJedis(targetPool) { Jedis jedis ->
            return criteria.statuses ? pipelineIds : jedis.zrevrange(key, 0, (criteria.limit - 1))
          }
        }
      }
    }

    /**
     * Construct an observable that will retrieve pipelines from the primary redis
     */
    def currentPipelineIds = filteredPipelineIdsByJedis[jedisPool]
    currentPipelineIds = currentPipelineIds.subList(0, Math.min(criteria.limit, currentPipelineIds.size()))

    def currentObservable = retrieveObservable(
      Pipeline,
      executionsByPipelineKey(pipelineConfigId),
      fnBuilder.call(jedisPool, currentPipelineIds),
      queryByAppScheduler,
      jedisPool
    )

    if (jedisPoolPrevious.present) {
      /**
       * If configured, construct an observable the will retrieve pipelines from the secondary redis
       */
      def previousPipelineIds = filteredPipelineIdsByJedis[jedisPoolPrevious.get()]
      previousPipelineIds = previousPipelineIds - currentPipelineIds
      previousPipelineIds = previousPipelineIds.subList(0, Math.min(criteria.limit, previousPipelineIds.size()))

      def previousObservable = retrieveObservable(
        Pipeline,
        executionsByPipelineKey(pipelineConfigId),
        fnBuilder.call(jedisPoolPrevious.get(), previousPipelineIds),
        queryByAppScheduler,
        jedisPoolPrevious.get()
      )

      // merge primary + secondary observables
      return Observable.merge(currentObservable, previousObservable)
    }

    return currentObservable
  }

  @Override
  Orchestration retrieveOrchestration(String id) {
    withJedis(getJedisPoolForId("orchestration:${id}")) { Jedis jedis ->
      retrieveInternal(jedis, Orchestration, id)
    }
  }

  @Override
  void deleteOrchestration(String id) {
    withJedis(getJedisPoolForId("orchestration:${id}")) { Jedis jedis ->
      deleteInternal(jedis, Orchestration, id)
    }
  }

  @Override
  Observable<Orchestration> retrieveOrchestrations() {
    return Observable.merge(allJedis().collect {all(Orchestration, it)})
  }

  @Override
  @CompileDynamic
  Observable<Orchestration> retrieveOrchestrationsForApplication(String application, ExecutionCriteria criteria) {
    def allOrchestrationsKey = appKey(Orchestration, application)

    /**
     * Fetch orchestration ids from the primary redis (and secondary if configured)
     */
    Map<Pool<Jedis>, List<String>> filteredOrchestrationIdsByJedis = [:].withDefault { [] }
    if (criteria.statuses) {
      allJedis().each { Pool<Jedis> targetPool ->
        withJedis(targetPool) { Jedis jedis ->
          def orchestrationKeys = jedis.smembers(allOrchestrationsKey) as List<String>
          def allowedExecutionStatuses = criteria.statuses*.toString() as Set<String>

          def pipeline = jedis.pipelined()
          def fetches = orchestrationKeys.collect {
            pipeline.hget("orchestration:${it}" as String, "status")
          }
          pipeline.sync()

          fetches.eachWithIndex { Response<String> entry, int index ->
            if (allowedExecutionStatuses.contains(entry.get())) {
              filteredOrchestrationIdsByJedis[targetPool] << orchestrationKeys[index]
            }
          }
        }
      }
    }

    def fnBuilder = { Pool<Jedis> targetPool, List<String> orchestrationIds ->
      new Func1<String, Iterable<String>>() {
        @Override
        Iterable<String> call(String key) {
          withJedis(targetPool) { Jedis jedis ->
            if (criteria.statuses) {
              return orchestrationIds
            }
            def unfiltered = jedis.smembers(key).toList()
            return unfiltered.subList(0, Math.min(criteria.limit, unfiltered.size()))
          }
        }
      }
    }

    /**
     * Construct an observable that will retrieve orchestrations from the primary redis
     */
    def currentOrchestrationIds = filteredOrchestrationIdsByJedis[jedisPool]
    currentOrchestrationIds = currentOrchestrationIds.subList(0, Math.min(criteria.limit, currentOrchestrationIds.size()))

    def currentObservable = retrieveObservable(
      Orchestration,
      allOrchestrationsKey,
      fnBuilder.call(jedisPool, currentOrchestrationIds),
      queryByAppScheduler,
      jedisPool
    )

    if (jedisPoolPrevious.present) {
      /**
       * If configured, construct an observable the will retrieve orchestrations from the secondary redis
       */
      def previousOrchestrationIds = filteredOrchestrationIdsByJedis[jedisPoolPrevious.get()]
      previousOrchestrationIds = previousOrchestrationIds - currentOrchestrationIds
      previousOrchestrationIds = previousOrchestrationIds.subList(0, Math.min(criteria.limit, previousOrchestrationIds.size()))

      def previousObservable = retrieveObservable(
        Orchestration,
        allOrchestrationsKey,
        fnBuilder.call(jedisPoolPrevious.get(), previousOrchestrationIds),
        queryByAppScheduler,
        jedisPoolPrevious.get()
      )

      // merge primary + secondary observables
      return Observable.merge(currentObservable, previousObservable)
    }

    return currentObservable
  }

  private void storeExecutionInternal(JedisCommands jedis, Execution execution) {
    def prefix = execution.getClass().simpleName.toLowerCase()

    if (!execution.id) {
      execution.id = UUID.randomUUID().toString()
      jedis.sadd(alljobsKey(execution.getClass()), execution.id)
      jedis.sadd(appKey(execution.getClass(), execution.application), execution.id)
    }

    String key = "${prefix}:$execution.id"

    Map<String, String> map = [
      application         : execution.application,
      appConfig           : mapper.writeValueAsString(execution.appConfig),
      canceled            : String.valueOf(execution.canceled),
      parallel            : String.valueOf(execution.parallel),
      limitConcurrent     : String.valueOf(execution.limitConcurrent),
      buildTime           : Long.toString(execution.buildTime ?: 0L),
      startTime           : execution.startTime?.toString(),
      endTime             : execution.endTime?.toString(),
      executingInstance   : execution.executingInstance,
      executionEngine     : execution.executionEngine,
      status              : execution.status?.name(),
      authentication      : mapper.writeValueAsString(execution.authentication),
      paused              : mapper.writeValueAsString(execution.paused),
      keepWaitingPipelines: String.valueOf(execution.keepWaitingPipelines)
    ]
    // TODO: store separately? Seems crazy to be using a hash rather than a set
    map.stageIndex = execution.stages.id.join(",")
    execution.stages.each { stage ->
      map.putAll(serializeStage(stage))
    }
    if (execution instanceof Pipeline) {
      map.name = execution.name
      map.pipelineConfigId = execution.pipelineConfigId
      map.trigger = mapper.writeValueAsString(execution.trigger)
      map.notifications = mapper.writeValueAsString(execution.notifications)
      map.initialConfig = mapper.writeValueAsString(execution.initialConfig)
    } else if (execution instanceof Orchestration) {
      map.description = execution.description
    }

    jedis.hdel(key, "config")
    jedis.hmset(key, filterValues(map, notNull()))
  }

  private Map<String, String> serializeStage(Stage stage) {
    Map<String, String> map = [:]
    map["stage.${stage.id}.refId".toString()] = stage.refId
    map["stage.${stage.id}.type".toString()] = stage.type
    map["stage.${stage.id}.name".toString()] = stage.name
    map["stage.${stage.id}.startTime".toString()] = stage.startTime?.toString()
    map["stage.${stage.id}.endTime".toString()] = stage.endTime?.toString()
    map["stage.${stage.id}.status".toString()] = stage.status.name()
    map["stage.${stage.id}.initializationStage".toString()] = String.valueOf(stage.initializationStage)
    map["stage.${stage.id}.syntheticStageOwner".toString()] = stage.syntheticStageOwner?.name()
    map["stage.${stage.id}.parentStageId".toString()] = stage.parentStageId
    map["stage.${stage.id}.requisiteStageRefIds".toString()] = stage.requisiteStageRefIds?.join(",")
    map["stage.${stage.id}.scheduledTime".toString()] = String.valueOf(stage.scheduledTime)
    map["stage.${stage.id}.context".toString()] = mapper.writeValueAsString(stage.context)
    map["stage.${stage.id}.tasks".toString()] = mapper.writeValueAsString(stage.tasks)
    map["stage.${stage.id}.lastModified".toString()] = stage.lastModified ? mapper.writeValueAsString(stage.lastModified) : null
    return map
  }

  private <T extends Execution> void storeStageInternal(Jedis jedis, Class<T> type, Stage<T> stage) {
    def prefix = type.simpleName.toLowerCase()
    def key = "$prefix:$stage.execution.id"

    def serializedStage = serializeStage(stage)
    jedis.hmset(key, filterValues(serializedStage, notNull()))
    jedis.hdel(key, serializedStage.keySet().findAll { serializedStage[it] == null } as String[])
  }

  @CompileDynamic
  private <T extends Execution> T retrieveInternal(Jedis jedis, Class<T> type, String id) throws ExecutionNotFoundException {
    def prefix = type.simpleName.toLowerCase()
    def key = "$prefix:$id"
    if (jedis.exists(key)) {
      Map<String, String> map = jedis.hgetAll(key)
      def execution = type.newInstance()
      execution.id = id
      execution.application = map.application
      execution.appConfig.putAll(mapper.readValue(map.appConfig, Map))
      execution.context.putAll(map.context ? mapper.readValue(map.context, Map) : [:])
      execution.canceled = Boolean.parseBoolean(map.canceled)
      execution.canceledBy = map.canceledBy
      execution.cancellationReason = map.cancellationReason
      execution.parallel = Boolean.parseBoolean(map.parallel)
      execution.limitConcurrent = Boolean.parseBoolean(map.limitConcurrent)
      execution.buildTime = map.buildTime?.toLong()
      execution.startTime = map.startTime?.toLong()
      execution.endTime = map.endTime?.toLong()
      execution.executingInstance = map.executingInstance
      execution.executionEngine = map.executionEngine
      execution.status = map.status ? ExecutionStatus.valueOf(map.status) : null
      execution.authentication = mapper.readValue(map.authentication, Execution.AuthenticationDetails)
      execution.paused = map.paused ? mapper.readValue(map.paused, Execution.PausedDetails) : null
      execution.keepWaitingPipelines = Boolean.parseBoolean(map.keepWaitingPipelines)

      def stageIds = map.stageIndex.tokenize(",")
      stageIds.each { stageId ->
        def stage = execution instanceof Pipeline ? new PipelineStage() : new OrchestrationStage()
        stage.stageNavigator = stageNavigator
        stage.id = stageId
        stage.refId = map["stage.${stageId}.refId".toString()]
        stage.type = map["stage.${stageId}.type".toString()]
        stage.name = map["stage.${stageId}.name".toString()]
        stage.startTime = map["stage.${stageId}.startTime".toString()]?.toLong()
        stage.endTime = map["stage.${stageId}.endTime".toString()]?.toLong()
        stage.status = ExecutionStatus.valueOf(map["stage.${stageId}.status".toString()])
        stage.initializationStage = map["stage.${stageId}.initializationStage".toString()].toBoolean()
        stage.syntheticStageOwner = map["stage.${stageId}.syntheticStageOwner".toString()] ? SyntheticStageOwner.valueOf(map["stage.${stageId}.syntheticStageOwner".toString()]) : null
        stage.parentStageId = map["stage.${stageId}.parentStageId".toString()]
        stage.requisiteStageRefIds = map["stage.${stageId}.requisiteStageRefIds".toString()]?.tokenize(",")
        stage.scheduledTime = map["stage.${stageId}.scheduledTime".toString()]?.toLong()
        stage.context = mapper.readValue(map["stage.${stageId}.context".toString()], MAP_STRING_TO_OBJECT)
        stage.tasks = mapper.readValue(map["stage.${stageId}.tasks".toString()], LIST_OF_TASKS)
        if (map["stage.${stageId}.lastModified".toString()]) {
          stage.lastModified = mapper.readValue(map["stage.${stageId}.lastModified".toString()], MAP_STRING_TO_OBJECT)
        }
        stage.execution = execution
        execution.stages << stage
      }
      if (execution instanceof Pipeline) {
        execution.name = map.name
        execution.pipelineConfigId = map.pipelineConfigId
        execution.trigger.putAll(mapper.readValue(map.trigger, Map))
        execution.notifications.addAll(mapper.readValue(map.notifications, List))
        execution.initialConfig.putAll(mapper.readValue(map.initialConfig, Map))
      } else if (execution instanceof Orchestration) {
        execution.description = map.description
      }
      return execution
    } else {
      throw new ExecutionNotFoundException("No ${type.simpleName} found for $id")
    }
  }

  private <T extends Execution> void deleteInternal(Jedis jedis, Class<T> type, String id) {
    def prefix = type.simpleName.toLowerCase()
    def key = "$prefix:$id"
    try {
      def application = jedis.hget(key, "application")
      def appKey = appKey(type, application)
      jedis.srem(appKey, id)

      if (type == Pipeline) {
        def pipelineConfigId = jedis.hget(key, "pipelineConfigId")
        jedis.zrem(executionsByPipelineKey(pipelineConfigId), id)
      }
    } catch (ExecutionNotFoundException ignored) {
      // do nothing
    } finally {
      jedis.del(key)
      jedis.srem(alljobsKey(type), id)
    }
  }

  private <T extends Execution> Observable<T> all(Class<T> type, Pool<Jedis> jedisPool) {
    retrieveObservable(type, alljobsKey(type), queryAllScheduler, jedisPool)
  }

  private <T extends Execution> Observable<T> allForApplication(Class<T> type, String application, Pool<Jedis> jedisPool) {
    retrieveObservable(type, appKey(type, application), queryByAppScheduler, jedisPool)
  }

  @CompileDynamic
  private <T extends Execution> Observable<T> retrieveObservable(Class<T> type,
                                                                 String lookupKey,
                                                                 Scheduler scheduler,
                                                                 Pool<Jedis> jedisPool) {
    return retrieveObservable(type, lookupKey, new Func1<String, Iterable<String>>() {
      @Override
      Iterable<String> call(String key) {
        withJedis(jedisPool) { Jedis jedis ->
          return jedis.smembers(key)
        }
      }
    }, scheduler, jedisPool)
  }

  @CompileDynamic
  private <T extends Execution> Observable<T> retrieveObservable(Class<T> type,
                                                                 String lookupKey,
                                                                 Func1<String, Iterable<String>> lookupKeyFetcher,
                                                                 Scheduler scheduler,
                                                                 Pool<Jedis> jedisPool) {
    Observable
      .just(lookupKey)
      .flatMapIterable(lookupKeyFetcher)
      .buffer(chunkSize)
      .flatMap { Collection<String> ids ->
      Observable
        .from(ids)
        .flatMap { String executionId ->
        withJedis(jedisPool) { Jedis jedis ->
          try {
            return Observable.just(retrieveInternal(jedis, type, executionId))
          } catch (ExecutionNotFoundException ignored) {
            log.info("Execution (${executionId}) does not exist")
            if (jedis.type(lookupKey) == "zset") {
              jedis.zrem(lookupKey, executionId)
            } else {
              jedis.srem(lookupKey, executionId)
            }
          } catch (Exception e) {
            log.error("Failed to retrieve execution '${executionId}', message: ${e.message}", e)
          }
          return Observable.empty()
        }
      }
      .subscribeOn(scheduler)
    }
  }

  private static String alljobsKey(Class type) {
    "allJobs:${type.simpleName.toLowerCase()}"
  }

  private static String appKey(Class type, String app) {
    "${type.simpleName.toLowerCase()}:app:${app}"
  }

  static String executionsByPipelineKey(String pipelineConfigId) {
    pipelineConfigId = pipelineConfigId ?: "---"
    "pipeline:executions:$pipelineConfigId"
  }

  private String fetchKey(String id) {
    String key = withJedis(jedisPool) { Jedis jedis ->
      if (jedis.exists("pipeline:$id")) {
        return "pipeline:$id"
      } else if (jedis.exists("orchestration:$id")) {
        return "orchestration:$id"
      }
      return null
    }

    if (!key && jedisPoolPrevious.present) {
      key = withJedis(jedisPoolPrevious.get()) { Jedis jedis ->
        if (jedis.exists("pipeline:$id")) {
          return "pipeline:$id"
        } else if (jedis.exists("orchestration:$id")) {
          return "orchestration:$id"
        }
        return null
      }
    }

    if (!key) {
      throw new ExecutionNotFoundException("No execution found with id $id")
    }

    return key
  }

  private Pool<Jedis> getJedisPoolForId(String id) {
    if (!id) {
      return jedisPool
    }

    Pool<Jedis> jedisPoolForId = null
    withJedis(jedisPool) { Jedis jedis ->
      if (jedis.exists(id)) {
        jedisPoolForId = jedisPool
      }
    }

    if (!jedisPoolForId && jedisPoolPrevious.present) {
      withJedis(jedisPoolPrevious.get()) { Jedis jedis ->
        if (jedis.exists(id)) {
          jedisPoolForId = jedisPoolPrevious.get()
        }
      }
    }

    return jedisPoolForId ?: jedisPool
  }

  private <T> T withJedis(Pool<Jedis> jedisPool, Function<Jedis, T> action) {
    jedisPool.resource.withCloseable(action.&apply)
  }

  private Collection<Pool<Jedis>> allJedis() {
    return ([jedisPool] + (jedisPoolPrevious.present ? [jedisPoolPrevious.get()] : []))
  }

  private static ThreadPoolTaskExecutor newFixedThreadPool(Registry registry,
                                                           int threadPoolSize,
                                                           String threadPoolName) {
    def executor = new ThreadPoolTaskExecutor(maxPoolSize: threadPoolSize, corePoolSize: threadPoolSize)
    executor.afterPropertiesSet()
    return OrcaConfiguration.applyThreadPoolMetrics(registry, executor, threadPoolName)
  }
}
