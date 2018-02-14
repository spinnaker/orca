/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.persistence

import java.util.concurrent.CountDownLatch
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.*
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisExecutionRepository
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import rx.schedulers.Schedulers
import spock.lang.*
import static com.netflix.spinnaker.orca.ExecutionStatus.*
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.newStage
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.*
import static java.util.concurrent.TimeUnit.SECONDS

@Subject(ExecutionRepository)
@Unroll
abstract class ExecutionRepositoryTck<T extends ExecutionRepository> extends Specification {

  @Subject
  T repository

  void setup() {
    repository = createExecutionRepository()
  }

  abstract T createExecutionRepository()

  def "can retrieve pipelines by status"() {
    given:
    def runningExecution = pipeline {
      status = RUNNING
      pipelineConfigId = "pipeline-1"
    }
    def succeededExecution = pipeline {
      status = SUCCEEDED
      pipelineConfigId = "pipeline-1"
    }

    when:
    repository.store(runningExecution)
    repository.store(succeededExecution)
    def pipelines = repository.retrievePipelinesForPipelineConfigId(
      "pipeline-1", new ExecutionCriteria(limit: 5, statuses: ["RUNNING", "SUCCEEDED", "TERMINAL"])
    ).subscribeOn(Schedulers.io()).toList().toBlocking().single()

    then:
    pipelines*.id.sort() == [runningExecution.id, succeededExecution.id].sort()

    when:
    pipelines = repository.retrievePipelinesForPipelineConfigId(
      "pipeline-1", new ExecutionCriteria(limit: 5, statuses: ["RUNNING"])
    ).subscribeOn(Schedulers.io()).toList().toBlocking().single()

    then:
    pipelines*.id.sort() == [runningExecution.id].sort()

    when:
    pipelines = repository.retrievePipelinesForPipelineConfigId(
      "pipeline-1", new ExecutionCriteria(limit: 5, statuses: ["TERMINAL"])
    ).subscribeOn(Schedulers.io()).toList().toBlocking().single()

    then:
    pipelines.isEmpty()
  }

  def "can retrieve orchestrations by status"() {
    given:
    def runningExecution = orchestration {
      status = RUNNING
      buildTime = 0
      trigger = new DefaultTrigger("manual")
    }
    def succeededExecution = orchestration {
      status = SUCCEEDED
      buildTime = 0
      trigger = new DefaultTrigger("manual")
    }

    when:
    repository.store(runningExecution)
    repository.store(succeededExecution)
    def orchestrations = repository.retrieveOrchestrationsForApplication(
      runningExecution.application, new ExecutionCriteria(limit: 5, statuses: ["RUNNING", "SUCCEEDED", "TERMINAL"])
    ).subscribeOn(Schedulers.io()).toList().toBlocking().single()

    then:
    orchestrations*.id.sort() == [runningExecution.id, succeededExecution.id].sort()

    when:
    orchestrations = repository.retrieveOrchestrationsForApplication(
      runningExecution.application, new ExecutionCriteria(limit: 5, statuses: ["RUNNING"])
    ).subscribeOn(Schedulers.io()).toList().toBlocking().single()

    then:
    orchestrations*.id.sort() == [runningExecution.id].sort()

    when:
    orchestrations = repository.retrieveOrchestrationsForApplication(
      runningExecution.application, new ExecutionCriteria(limit: 5, statuses: ["TERMINAL"])
    ).subscribeOn(Schedulers.io()).toList().toBlocking().single()

    then:
    orchestrations.isEmpty()
  }

  def "a pipeline can be retrieved after being stored"() {
    given:
    def pipeline = pipeline {
      application = "orca"
      name = "dummy-pipeline"
      trigger = new JenkinsTrigger("master", "job", 1, null)
      stage {
        type = "one"
        context = [foo: "foo"]
      }
      stage {
        type = "two"
        context = [bar: "bar"]
      }
      stage {
        type = "three"
        context = [baz: "baz"]
      }
    }
    def application = pipeline.application
    repository.store(pipeline)

    expect:
    repository.retrieve(PIPELINE).toBlocking().first().id == pipeline.id

    with(repository.retrieve(pipeline.type, pipeline.id)) {
      id == pipeline.id
      application == pipeline.application
      name == pipeline.name
      trigger == pipeline.trigger
      stages.type == pipeline.stages.type
      stages.execution.every {
        it.id == pipeline.id
      }
      stages.every {
        it.context == pipeline.namedStage(it.type).context
      }
    }
  }

  def "trying to retrieve an invalid #type.simpleName id throws an exception"() {
    when:
    repository.retrieve(type, "invalid")

    then:
    thrown ExecutionNotFoundException

    where:
    type << ExecutionType.values()
  }

  def "trying to delete a non-existent #type.simpleName id does not throw an exception"() {
    when:
    repository.delete(type, "invalid")

    then:
    notThrown ExecutionNotFoundException

    where:
    type << ExecutionType.values()
  }

  def "deleting a pipeline removes pipeline and stages"() {
    given:
    def pipeline = pipeline {
      stage { type = "one" }
      stage { type = "two" }
      stage {
        type = "one-a"
        name = "one-1"
      }
      stage {
        type = "one-b"
        name = "one-1"
      }
      stage {
        type = "one-a-a"
        name = "three"
      }
    }

    and:
    repository.store(pipeline)
    repository.delete(PIPELINE, pipeline.id)

    when:
    repository.retrieve(PIPELINE, pipeline.id)

    then:
    thrown ExecutionNotFoundException

    and:
    repository.retrieve(PIPELINE).toList().toBlocking().first() == []
  }

  def "updateStatus sets startTime to current time if new status is RUNNING"() {
    given:
    repository.store(execution)

    expect:
    with(repository.retrieve(execution.type, execution.id)) {
      startTime == null
    }

    when:
    repository.updateStatus(execution.id, RUNNING)

    then:
    with(repository.retrieve(execution.type, execution.id)) {
      status == RUNNING
      startTime != null
    }

    where:
    execution << [pipeline {
      trigger = new PipelineTrigger(pipeline())
    }, orchestration {
      trigger = new DefaultTrigger("manual")
    }]
  }

  def "updateStatus sets endTime to current time if new status is #status"() {
    given:
    repository.store(execution)

    expect:
    with(repository.retrieve(execution.type, execution.id)) {
      endTime == null
    }

    when:
    repository.updateStatus(execution.id, status)

    then:
    with(repository.retrieve(execution.type, execution.id)) {
      status == status
      endTime != null
    }

    where:
    execution                                                | status
    pipeline { trigger = new PipelineTrigger(pipeline()) }   | CANCELED
    orchestration { trigger = new DefaultTrigger("manual") } | SUCCEEDED
    orchestration { trigger = new DefaultTrigger("manual") } | TERMINAL
  }

  def "cancelling a not-yet-started execution updates the status immediately"() {
    given:
    def execution = pipeline()
    repository.store(execution)

    expect:
    with(repository.retrieve(execution.type, execution.id)) {
      status == NOT_STARTED
    }

    when:
    repository.cancel(execution.id)


    then:
    with(repository.retrieve(execution.type, execution.id)) {
      canceled
      status == CANCELED
    }
  }

  def "cancelling a running execution does not update the status immediately"() {
    given:
    def execution = pipeline()
    repository.store(execution)
    repository.updateStatus(execution.id, RUNNING)

    expect:
    with(repository.retrieve(execution.type, execution.id)) {
      status == RUNNING
    }

    when:
    repository.cancel(execution.id)


    then:
    with(repository.retrieve(execution.type, execution.id)) {
      canceled
      status == RUNNING
    }
  }

  @Unroll
  def "cancelling a running execution with a user adds a 'canceledBy' field, and an optional 'cancellationReason' field"() {
    given:
    def execution = pipeline()
    def user = "user@netflix.com"
    repository.store(execution)
    repository.updateStatus(execution.id, RUNNING)

    expect:
    with(repository.retrieve(execution.type, execution.id)) {
      status == RUNNING
    }

    when:
    repository.cancel(execution.id, user, reason)


    then:
    with(repository.retrieve(execution.type, execution.id)) {
      canceled
      canceledBy == user
      status == RUNNING
      cancellationReason == expectedCancellationReason
    }

    where:
    reason             || expectedCancellationReason
    "some good reason" || "some good reason"
    ""                 || null
    null               || null
  }

  def "pausing/resuming a running execution will set appropriate 'paused' details"() {
    given:
    def execution = pipeline()
    repository.store(execution)
    repository.updateStatus(execution.id, RUNNING)

    when:
    repository.pause(execution.id, "user@netflix.com")

    then:
    with(repository.retrieve(execution.type, execution.id)) {
      status == PAUSED
      paused.pauseTime != null
      paused.resumeTime == null
      paused.pausedBy == "user@netflix.com"
      paused.pausedMs == 0
      paused.paused == true
    }

    when:
    repository.resume(execution.id, "another@netflix.com")

    then:
    with(repository.retrieve(execution.type, execution.id)) {
      status == RUNNING
      paused.pauseTime != null
      paused.resumeTime != null
      paused.pausedBy == "user@netflix.com"
      paused.resumedBy == "another@netflix.com"
      paused.pausedMs == (paused.resumeTime - paused.pauseTime)
      paused.paused == false
    }
  }

  @Unroll
  def "should only #method a #expectedStatus execution"() {
    given:
    def execution = pipeline()
    repository.store(execution)
    repository.updateStatus(execution.id, status)

    when:
    repository."${method}"(execution.id, "user@netflix.com")

    then:
    def e = thrown(IllegalStateException)
    e.message.startsWith("Unable to ${method} pipeline that is not ${expectedStatus}")

    where:
    method   | status      || expectedStatus
    "pause"  | PAUSED      || RUNNING
    "pause"  | NOT_STARTED || RUNNING
    "resume" | RUNNING     || PAUSED
    "resume" | NOT_STARTED || PAUSED
  }

  @Unroll
  def "should force resume an execution regardless of status"() {
    given:
    def execution = pipeline()
    repository.store(execution)
    repository.updateStatus(execution.id, RUNNING)

    when:
    repository.pause(execution.id, "user@netflix.com")
    execution = repository.retrieve(execution.type, execution.id)

    then:
    execution.paused.isPaused()

    when:
    repository.updateStatus(execution.id, status)
    repository.resume(execution.id, "user@netflix.com", true)
    execution = repository.retrieve(execution.type, execution.id)

    then:
    execution.status == RUNNING
    !execution.paused.isPaused()

    where:
    status << ExecutionStatus.values()
  }

  def "should return task ref for currently running orchestration by correlation id"() {
    given:
    def execution = orchestration {
      trigger = new DefaultTrigger("manual", "covfefe")
    }
    repository.store(execution)
    repository.updateStatus(execution.id, RUNNING)

    when:
    def result = repository.retrieveOrchestrationForCorrelationId('covfefe')

    then:
    result.id == execution.id

    when:
    repository.updateStatus(execution.id, SUCCEEDED)
    repository.retrieveOrchestrationForCorrelationId('covfefe')

    then:
    thrown(ExecutionNotFoundException)
  }

  def "parses the parent execution of a pipeline trigger"() {
    given:
    def execution = pipeline {
      trigger = new PipelineTrigger(pipeline())
    }
    repository.store(execution)

    expect:
    with(repository.retrieve(PIPELINE, execution.id)) {
      trigger.parentExecution instanceof Execution
    }
  }
}

class JedisExecutionRepositorySpec extends ExecutionRepositoryTck<JedisExecutionRepository> {

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedisPrevious

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
    embeddedRedisPrevious = EmbeddedRedis.embed()
  }

  def cleanup() {
    embeddedRedis.jedis.withCloseable { it.flushDB() }
    embeddedRedisPrevious.jedis.withCloseable { it.flushDB() }
  }

  Pool<Jedis> jedisPool = embeddedRedis.pool
  Pool<Jedis> jedisPoolPrevious = embeddedRedisPrevious.pool

  RedisClientDelegate redisClientDelegate = new JedisClientDelegate(jedisPool)
  Optional<RedisClientDelegate> previousRedisClientDelegate = Optional.of(new JedisClientDelegate(jedisPoolPrevious))

  @AutoCleanup
  def jedis = jedisPool.resource

  @Override
  JedisExecutionRepository createExecutionRepository() {
    return new JedisExecutionRepository(new NoopRegistry(), redisClientDelegate, previousRedisClientDelegate, 1, 50)
  }

  def "cleans up indexes of non-existent executions"() {
    given:
    jedis.sadd("allJobs:pipeline", id)

    when:
    def result = repository.retrieve(PIPELINE).toList().toBlocking().first()

    then:
    result.isEmpty()

    and:
    !jedis.sismember("allJobs:pipeline", id)

    where:
    id = "some-pipeline-id"
  }

  def "storing/deleting a pipeline updates the executionsByPipeline set"() {
    given:
    def pipeline = pipeline {
      stage {
        type = "one"
      }
      application = "someApp"
    }

    when:
    repository.store(pipeline)

    then:
    jedis.zrange(JedisExecutionRepository.executionsByPipelineKey(pipeline.pipelineConfigId), 0, 1) == [
      pipeline.id
    ] as Set<String>

    when:
    repository.delete(pipeline.type, pipeline.id)
    repository.retrieve(pipeline.type, pipeline.id)

    then:
    thrown ExecutionNotFoundException

    and:
    repository.retrieve(PIPELINE).toList().toBlocking().first() == []
    jedis.zrange(JedisExecutionRepository.executionsByPipelineKey(pipeline.pipelineConfigId), 0, 1).isEmpty()
  }

  @Unroll
  def "retrieving orchestrations limits the number of returned results"() {
    given:
    4.times {
      repository.store(orchestration {
        application = "orca"
        trigger = new DefaultTrigger("manual", "fnord")
      })
    }

    when:
    def retrieved = repository.retrieveOrchestrationsForApplication("orca", new ExecutionCriteria(limit: limit))
      .toList().toBlocking().first()

    then:
    retrieved.size() == actual

    where:
    limit || actual
    0     || 0
    1     || 1
    2     || 2
    4     || 4
    100   || 4
  }

  def "can store an orchestration already in previousRedis back to previousRedis"() {
    given:
    def orchestration = orchestration {
      application = "paperclips"
      trigger = new DefaultTrigger("manual", "fnord")
      stage {
        type = "one"
        context = [:]
      }
    }

    when:
    def previousRepository = new JedisExecutionRepository(new NoopRegistry(), previousRedisClientDelegate.get(), Optional.empty(), 1, 50)
    previousRepository.store(orchestration)
    def retrieved = repository.retrieve(ORCHESTRATION, orchestration.id)

    then:
    retrieved.id == orchestration.id

    and:
    def stage = retrieved.stages.first()
    stage.setContext([this: "works"])
    repository.store(retrieved)

    then:
    previousRepository.retrieve(ORCHESTRATION, orchestration.id).stages.first().getContext() == [this: "works"]
  }

  def "can updateStageContext against previousRedis"() {
    given:
    def orchestration = orchestration {
      application = "paperclips"
      trigger = new DefaultTrigger("manual", "fnord")
      stage {
        type = "one"
        context = [:]
      }
    }

    when:
    def previousRepository = new JedisExecutionRepository(new NoopRegistry(), previousRedisClientDelegate.get(), Optional.empty(), 1, 50)
    previousRepository.store(orchestration)
    def retrieved = repository.retrieve(ORCHESTRATION, orchestration.id)

    then:
    retrieved.id == orchestration.id

    and:
    def stage = retrieved.stages.first()
    stage.setContext([why: 'hello'])
    repository.updateStageContext(stage)

    then:
    previousRepository.retrieve(ORCHESTRATION, orchestration.id).stages.first().getContext() == [why: 'hello']

  }

  def "can retrieve running orchestration in previousRedis by correlation id"() {
    given:
    def previousRepository = new JedisExecutionRepository(new NoopRegistry(), previousRedisClientDelegate.get(), Optional.empty(), 1, 50)
    def execution = orchestration {
      trigger = new DefaultTrigger("manual", "covfefe")
    }
    previousRepository.store(execution)
    previousRepository.updateStatus(execution.id, RUNNING)

    when:
    def result = repository.retrieveOrchestrationForCorrelationId('covfefe')

    then:
    result.id == execution.id

    when:
    repository.updateStatus(execution.id, SUCCEEDED)
    repository.retrieveOrchestrationForCorrelationId('covfefe')

    then:
    thrown(ExecutionNotFoundException)
  }

  def "can retrieve orchestrations from multiple redis stores"() {
    given:
    3.times {
      repository.store(orchestration {
        application = "orca"
        trigger = new DefaultTrigger("manual", "fnord")
      })
    }

    and:
    def previousRepository = new JedisExecutionRepository(new NoopRegistry(), previousRedisClientDelegate.get(), Optional.empty(), 1, 50)
    3.times {
      previousRepository.store(orchestration {
        application = "orca"
        trigger = new DefaultTrigger("manual", "fnord")
      })
    }

    when:
    // TODO-AJ limits are current applied to each backing redis
    def retrieved = repository.retrieveOrchestrationsForApplication("orca", new ExecutionCriteria(limit: 2))
      .toList().toBlocking().first()

    then:
    // orchestrations are stored in an unsorted set and results are non-deterministic
    retrieved.size() == 4
  }

  def "can delete orchestrations from multiple redis stores"() {
    given:
    def orchestration1 = orchestration {
      application = "orca"
      trigger = new DefaultTrigger("manual", "fnord")
    }
    repository.store(orchestration1)

    and:
    def previousRepository = new JedisExecutionRepository(new NoopRegistry(), previousRedisClientDelegate.get(), Optional.empty(), 1, 50)
    def orchestration2 = orchestration {
      application = "orca"
      trigger = new DefaultTrigger("manual", "fnord")
    }
    previousRepository.store(orchestration2)

    when:
    repository.delete(orchestration1.type, orchestration1.id)
    def retrieved = repository.retrieveOrchestrationsForApplication("orca", new ExecutionCriteria(limit: 2))
      .toList().toBlocking().first()

    then:
    retrieved*.id == [orchestration2.id]

    when:
    repository.delete(orchestration2.type, orchestration2.id)
    retrieved = repository.retrieveOrchestrationsForApplication("orca", new ExecutionCriteria(limit: 2))
      .toList().toBlocking().first()

    then:
    retrieved.isEmpty()
  }

  def "can retrieve pipelines from multiple redis stores"() {
    given:
    repository.store(pipeline {
      application = "orca"
      pipelineConfigId = "pipeline-1"
      buildTime = 10
    })
    repository.store(pipeline {
      application = "orca"
      pipelineConfigId = "pipeline-1"
      buildTime = 11
    })
    repository.store(pipeline {
      application = "orca"
      pipelineConfigId = "pipeline-1"
      buildTime = 12
    })

    and:
    def previousRepository = new JedisExecutionRepository(new NoopRegistry(), previousRedisClientDelegate.get(), Optional.empty(), 1, 50)
    previousRepository.store(pipeline {
      application = "orca"
      pipelineConfigId = "pipeline-1"
      buildTime = 7
    })
    previousRepository.store(pipeline {
      application = "orca"
      pipelineConfigId = "pipeline-1"
      buildTime = 8
    })
    previousRepository.store(pipeline {
      application = "orca"
      pipelineConfigId = "pipeline-1"
      buildTime = 9
    })

    when:
    // TODO-AJ limits are current applied to each backing redis
    def retrieved = repository.retrievePipelinesForPipelineConfigId("pipeline-1", new ExecutionCriteria(limit: 2))
      .toList().toBlocking().first()

    then:
    // pipelines are stored in a sorted sets and results should be reverse buildTime ordered
    retrieved*.buildTime == [12L, 11L, 9L, 8L]
  }

  def "can delete pipelines from multiple redis stores"() {
    given:
    def pipeline1 = pipeline {
      application = "orca"
      pipelineConfigId = "pipeline-1"
      buildTime = 11
    }
    repository.store(pipeline1)

    and:
    def previousRepository = new JedisExecutionRepository(new NoopRegistry(), previousRedisClientDelegate.get(), Optional.empty(), 1, 50)
    def pipeline2 = pipeline {
      application = "orca"
      pipelineConfigId = "pipeline-1"
      buildTime = 10
    }
    previousRepository.store(pipeline2)

    when:
    repository.delete(pipeline1.type, pipeline1.id)
    def retrieved = repository.retrievePipelinesForPipelineConfigId("pipeline-1", new ExecutionCriteria(limit: 2))
      .toList().toBlocking().first()

    then:
    retrieved*.id == [pipeline2.id]

    when:
    repository.delete(pipeline2.type, pipeline2.id)
    retrieved = repository.retrievePipelinesForPipelineConfigId("pipeline-1", new ExecutionCriteria(limit: 2))
      .toList().toBlocking().first()

    then:
    retrieved.isEmpty()
  }

  def "should remove null 'stage' keys"() {
    given:
    def pipeline = pipeline {
      application = "orca"
      name = "dummy-pipeline"
      stage {
        type = "one"
        context = [foo: "foo"]
        startTime = 100
        endTime = 200
      }
    }
    repository.store(pipeline)

    when:
    def fetchedPipeline = repository.retrieve(pipeline.type, pipeline.id)

    then:
    fetchedPipeline.stages[0].startTime == 100
    fetchedPipeline.stages[0].endTime == 200

    when:
    fetchedPipeline.stages[0].startTime = null
    fetchedPipeline.stages[0].endTime = null
    repository.storeStage(fetchedPipeline.stages[0])

    fetchedPipeline = repository.retrieve(pipeline.type, pipeline.id)

    then:
    fetchedPipeline.stages[0].startTime == null
    fetchedPipeline.stages[0].endTime == null
  }

  def "can remove a stage leaving other stages unaffected"() {
    given:
    def pipeline = pipeline {
      application = "orca"
      name = "dummy-pipeline"
      stage { type = "one" }
      stage { type = "two" }
      stage { type = "three" }
    }

    repository.store(pipeline)

    expect:
    repository.retrieve(pipeline.type, pipeline.id).stages.size() == 3

    when:
    repository.removeStage(pipeline, pipeline.namedStage("two").id)

    then:
    with(repository.retrieve(pipeline.type, pipeline.id)) {
      stages.size() == 2
      stages.type == ["one", "three"]
    }
  }

  @Unroll
  def "can add a synthetic stage #position"() {
    given:
    def pipeline = pipeline {
      application = "orca"
      name = "dummy-pipeline"
      stage { type = "one" }
      stage { type = "two" }
      stage { type = "three" }
    }

    repository.store(pipeline)

    expect:
    repository.retrieve(pipeline.type, pipeline.id).stages.size() == 3

    when:
    def stage = newStage(pipeline, "whatever", "two-whatever", [:], pipeline.namedStage("two"), position)
    repository.addStage(stage)

    then:
    with(repository.retrieve(pipeline.type, pipeline.id)) {
      stages.size() == 4
      stages.name == expectedStageNames
    }

    where:
    position     | expectedStageNames
    STAGE_BEFORE | ["one", "two-whatever", "two", "three"]
    STAGE_AFTER  | ["one", "two", "two-whatever", "three"]
  }

  def "can concurrently add stages without overwriting"() {
    given:
    def pipeline = pipeline {
      application = "orca"
      name = "dummy-pipeline"
      stage { type = "one" }
      stage { type = "two" }
      stage { type = "three" }
    }

    repository.store(pipeline)

    expect:
    repository.retrieve(pipeline.type, pipeline.id).stages.size() == 3

    when:
    def stage1 = newStage(pipeline, "whatever", "one-whatever", [:], pipeline.namedStage("one"), STAGE_BEFORE)
    def stage2 = newStage(pipeline, "whatever", "two-whatever", [:], pipeline.namedStage("two"), STAGE_BEFORE)
    def stage3 = newStage(pipeline, "whatever", "three-whatever", [:], pipeline.namedStage("three"), STAGE_BEFORE)
    def startLatch = new CountDownLatch(1)
    def doneLatch = new CountDownLatch(3)
    [stage1, stage2, stage3].each { stage ->
      Thread.start {
        startLatch.await(1, SECONDS)
        repository.addStage(stage)
        doneLatch.countDown()
      }
    }
    startLatch.countDown()

    then:
    doneLatch.await(1, SECONDS)

    and:
    with(repository.retrieve(pipeline.type, pipeline.id)) {
      stages.size() == 6
      stages.name == ["one-whatever", "one", "two-whatever", "two", "three-whatever", "three"]
    }
  }

  def "can save a stage with all data and update stage context"() {
    given:
    def pipeline = pipeline {
      application = "orca"
      name = "dummy-pipeline"
      stage { type = "one" }
    }

    repository.store(pipeline)

    def stage = newStage(pipeline, "whatever", "one-whatever", [:], pipeline.namedStage("one"), STAGE_BEFORE)
    stage.lastModified = new Stage.LastModifiedDetails(user: "rfletcher@netflix.com", allowedAccounts: ["whatever"], lastModifiedTime: System.currentTimeMillis())
    stage.startTime = System.currentTimeMillis()
    stage.endTime = System.currentTimeMillis()
    stage.refId = "1<1"

    when:
    repository.addStage(stage)

    then:
    notThrown(Exception)

    when:
    stage.setContext([foo: 'bar'])
    repository.updateStageContext(stage)

    then:
    def stored = repository.retrieve(PIPELINE, pipeline.id)

    and:
    stored.stageById(stage.id).context == [foo: 'bar']

  }
}
