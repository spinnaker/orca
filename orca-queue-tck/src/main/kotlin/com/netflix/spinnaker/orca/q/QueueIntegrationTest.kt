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

package com.netflix.spinnaker.orca.q

import com.netflix.appinfo.InstanceInfo.InstanceStatus.*
import com.netflix.discovery.StatusChangeEvent
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.OrcaQueueConfiguration
import com.netflix.spinnaker.config.QueueConfiguration
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.exceptions.DefaultExceptionHandler
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.pipeline.RestrictExecutionDuringTimeWindow
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.newStage
import com.netflix.spinnaker.orca.pipeline.TaskNode.Builder
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisConfiguration
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import com.netflix.spinnaker.q.DeadMessageCallback
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.q.memory.InMemoryQueue
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit4.SpringRunner
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import java.time.Clock
import java.time.Duration
import java.time.Instant.now
import java.time.ZoneId

@SpringBootTest(
  classes = [TestConfig::class],
  properties = ["queue.retry.delay.ms=10"]
)
@RunWith(SpringRunner::class)
class QueueIntegrationTest {

  @Autowired
  lateinit var queue: Queue
  @Autowired
  lateinit var runner: QueueExecutionRunner
  @Autowired
  lateinit var repository: ExecutionRepository
  @Autowired
  lateinit var dummyTask: DummyTask
  @Autowired
  lateinit var context: ConfigurableApplicationContext

  @Value("\${tasks.executionWindow.timezone:America/Los_Angeles}")
  lateinit var timeZoneId: String
  private val timeZone by lazy { ZoneId.of(timeZoneId) }

  @Before
  fun discoveryUp() {
    context.publishEvent(RemoteStatusChangedEvent(StatusChangeEvent(STARTING, UP)))
  }

  @After
  fun discoveryDown() {
    context.publishEvent(RemoteStatusChangedEvent(StatusChangeEvent(UP, OUT_OF_SERVICE)))
  }

  @After
  fun resetMocks() = reset(dummyTask)

  @Test
  fun `can run a simple pipeline`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
      }
    }
    repository.store(pipeline)

    whenever(dummyTask.timeout) doReturn 2000L
    whenever(dummyTask.execute(any())) doReturn TaskResult.SUCCEEDED

    context.runToCompletion(pipeline, runner::start, repository)

    assertThat(repository.retrieve(PIPELINE, pipeline.id).status)
      .isEqualTo(SUCCEEDED)
  }

  @Test
  fun `will run tasks to completion`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
      }
    }
    repository.store(pipeline)

    whenever(dummyTask.timeout) doReturn 2000L
    whenever(dummyTask.execute(any())) doReturn TaskResult(RUNNING) doReturn TaskResult.SUCCEEDED

    context.runToCompletion(pipeline, runner::start, repository)

    assertThat(repository.retrieve(PIPELINE, pipeline.id).status)
      .isEqualTo(SUCCEEDED)
  }

  @Test
  fun `can run a fork join pipeline`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
      }
      stage {
        refId = "2a"
        requisiteStageRefIds = setOf("1")
        type = "dummy"
      }
      stage {
        refId = "2b"
        requisiteStageRefIds = setOf("1")
        type = "dummy"
      }
      stage {
        refId = "3"
        requisiteStageRefIds = setOf("2a", "2b")
        type = "dummy"
      }
    }
    repository.store(pipeline)

    whenever(dummyTask.timeout) doReturn 2000L
    whenever(dummyTask.execute(any())) doReturn TaskResult.SUCCEEDED

    context.runToCompletion(pipeline, runner::start, repository)

    repository.retrieve(PIPELINE, pipeline.id).apply {
      assertThat(status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("1").status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("2a").status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("2b").status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("3").status).isEqualTo(SUCCEEDED)
    }
  }

  @Test
  fun `can run a pipeline that ends in a branch`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
      }
      stage {
        refId = "2a"
        requisiteStageRefIds = setOf("1")
        type = "dummy"
      }
      stage {
        refId = "2b1"
        requisiteStageRefIds = setOf("1")
        type = "dummy"
      }
      stage {
        refId = "2b2"
        requisiteStageRefIds = setOf("2b1")
        type = "dummy"
      }
    }
    repository.store(pipeline)

    whenever(dummyTask.timeout) doReturn 2000L
    whenever(dummyTask.execute(any())) doReturn TaskResult.SUCCEEDED

    context.runToCompletion(pipeline, runner::start, repository)

    repository.retrieve(PIPELINE, pipeline.id).apply {
      assertThat(status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("1").status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("2a").status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("2b1").status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("2b2").status).isEqualTo(SUCCEEDED)
    }
  }

  @Test
  fun `can skip stages`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
        context["stageEnabled"] = mapOf(
          "type" to "expression",
          "expression" to "false"
        )
      }
    }
    repository.store(pipeline)

    context.runToCompletion(pipeline, runner::start, repository)

    assertThat(repository.retrieve(PIPELINE, pipeline.id).status)
      .isEqualTo(SUCCEEDED)

    verify(dummyTask, never()).execute(any())
  }

  @Test
  fun `pipeline fails if a task fails`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
      }
    }
    repository.store(pipeline)

    whenever(dummyTask.execute(any())) doReturn TaskResult(TERMINAL)

    context.runToCompletion(pipeline, runner::start, repository)

    assertThat(repository.retrieve(PIPELINE, pipeline.id).status)
      .isEqualTo(TERMINAL)
  }

  @Test
  fun `parallel stages that fail cancel other branches`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
      }
      stage {
        refId = "2a1"
        type = "dummy"
        requisiteStageRefIds = listOf("1")
      }
      stage {
        refId = "2a2"
        type = "dummy"
        requisiteStageRefIds = listOf("2a1")
      }
      stage {
        refId = "2b"
        type = "dummy"
        requisiteStageRefIds = listOf("1")
      }
      stage {
        refId = "3"
        type = "dummy"
        requisiteStageRefIds = listOf("2a2", "2b")
      }
    }
    repository.store(pipeline)

    whenever(dummyTask.timeout) doReturn 2000L
    whenever(dummyTask.execute(argThat { refId == "2a1" })) doReturn TaskResult(TERMINAL)
    whenever(dummyTask.execute(argThat { refId != "2a1" })) doReturn TaskResult.SUCCEEDED

    context.runToCompletion(pipeline, runner::start, repository)

    repository.retrieve(PIPELINE, pipeline.id).apply {
      assertThat(status).isEqualTo(TERMINAL)
      assertThat(stageByRef("1").status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("2a1").status).isEqualTo(TERMINAL)
      assertThat(stageByRef("2a2").status).isEqualTo(NOT_STARTED)
      assertThat(stageByRef("2b").status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("3").status).isEqualTo(NOT_STARTED)
    }
  }

  @Test
  fun `stages set to allow failure will proceed in spite of errors`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
      }
      stage {
        refId = "2a1"
        type = "dummy"
        requisiteStageRefIds = listOf("1")
        context["continuePipeline"] = true
      }
      stage {
        refId = "2a2"
        type = "dummy"
        requisiteStageRefIds = listOf("2a1")
      }
      stage {
        refId = "2b"
        type = "dummy"
        requisiteStageRefIds = listOf("1")
      }
      stage {
        refId = "3"
        type = "dummy"
        requisiteStageRefIds = listOf("2a2", "2b")
      }
    }
    repository.store(pipeline)

    whenever(dummyTask.timeout) doReturn 2000L
    whenever(dummyTask.execute(argThat { refId == "2a1" })) doReturn TaskResult(TERMINAL)
    whenever(dummyTask.execute(argThat { refId != "2a1" })) doReturn TaskResult.SUCCEEDED

    context.runToCompletion(pipeline, runner::start, repository)

    repository.retrieve(PIPELINE, pipeline.id).apply {
      assertThat(status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("1").status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("2a1").status).isEqualTo(FAILED_CONTINUE)
      assertThat(stageByRef("2a2").status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("2b").status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("3").status).isEqualTo(SUCCEEDED)
    }
  }

  @Test
  fun `stages set to allow failure but fail the pipeline will run to completion but then mark the pipeline failed`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
      }
      stage {
        refId = "2a1"
        type = "dummy"
        requisiteStageRefIds = listOf("1")
        context["continuePipeline"] = false
        context["failPipeline"] = false
        context["completeOtherBranchesThenFail"] = true
      }
      stage {
        refId = "2a2"
        type = "dummy"
        requisiteStageRefIds = listOf("2a1")
      }
      stage {
        refId = "2b1"
        type = "dummy"
        requisiteStageRefIds = listOf("1")
      }
      stage {
        refId = "2b2"
        type = "dummy"
        requisiteStageRefIds = listOf("1")
      }
      stage {
        refId = "3"
        type = "dummy"
        requisiteStageRefIds = listOf("2a2", "2b2")
      }
    }
    repository.store(pipeline)

    whenever(dummyTask.timeout) doReturn 2000L
    whenever(dummyTask.execute(argThat { refId == "2a1" })) doReturn TaskResult(TERMINAL)
    whenever(dummyTask.execute(argThat { refId != "2a1" })) doReturn TaskResult.SUCCEEDED

    context.runToCompletion(pipeline, runner::start, repository)

    repository.retrieve(PIPELINE, pipeline.id).apply {
      assertThat(status).isEqualTo(TERMINAL)
      assertThat(stageByRef("1").status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("2a1").status).isEqualTo(STOPPED)
      assertThat(stageByRef("2a2").status).isEqualTo(NOT_STARTED)
      assertThat(stageByRef("2b1").status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("2b2").status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("3").status).isEqualTo(NOT_STARTED)
    }
  }

  @Test
  fun `can run a stage with an execution window`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
        context = mapOf(
          "restrictExecutionDuringTimeWindow" to true,
          "restrictedExecutionWindow" to mapOf(
            "days" to (1..7).toList(),
            "whitelist" to listOf(mapOf(
              "startHour" to now().atZone(timeZone).hour,
              "startMin" to 0,
              "endHour" to now().atZone(timeZone).hour + 1,
              "endMin" to 0
            ))
          )
        )
      }
    }
    repository.store(pipeline)

    whenever(dummyTask.timeout) doReturn 2000L
    whenever(dummyTask.execute(any())) doReturn TaskResult.SUCCEEDED

    context.runToCompletion(pipeline, runner::start, repository)

    repository.retrieve(PIPELINE, pipeline.id).apply {
      assertThat(status).isEqualTo(SUCCEEDED)
      assertThat(stages.size).isEqualTo(2)
      assertThat(stages.first().type).isEqualTo(RestrictExecutionDuringTimeWindow.TYPE)
      assertThat(stages.map { it.status }).allMatch { it == SUCCEEDED }
    }
  }

  @Test
  fun `parallel stages do not duplicate execution windows`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "parallel"
        context = mapOf(
          "restrictExecutionDuringTimeWindow" to true,
          "restrictedExecutionWindow" to mapOf(
            "days" to (1..7).toList(),
            "whitelist" to listOf(mapOf(
              "startHour" to now().atZone(timeZone).hour,
              "startMin" to 0,
              "endHour" to now().atZone(timeZone).hour + 1,
              "endMin" to 0
            ))
          )
        )
      }
    }
    repository.store(pipeline)

    whenever(dummyTask.timeout) doReturn 2000L
    whenever(dummyTask.execute(any())) doReturn TaskResult.SUCCEEDED

    context.runToCompletion(pipeline, runner::start, repository)

    repository.retrieve(PIPELINE, pipeline.id).apply {
      assertThat(status).isEqualTo(SUCCEEDED)
      assertThat(stages.size).isEqualTo(5)
      assertThat(stages.first().type).isEqualTo(RestrictExecutionDuringTimeWindow.TYPE)
      assertThat(stages[1..3].map { it.type }).allMatch { it == "dummy" }
      assertThat(stages.last().type).isEqualTo("parallel")
      assertThat(stages.map { it.status }).allMatch { it == SUCCEEDED }
    }
  }

  // TODO: this test is verifying a bunch of things at once, it would make sense to break it up
  @Test
  fun `can resolve expressions in stage contexts`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
        context = mapOf(
          "expr" to "\${1 == 1}",
          "key" to mapOf(
            "expr" to "\${1 == 1}"
          )
        )
      }
    }
    repository.store(pipeline)

    whenever(dummyTask.timeout) doReturn 2000L
    whenever(dummyTask.execute(any())) doReturn TaskResult(SUCCEEDED, mapOf("output" to "foo"))

    context.runToCompletion(pipeline, runner::start, repository)

    verify(dummyTask).execute(check {
      // expressions should be resolved in the stage passes to tasks
      assertThat(it.context["expr"]).isEqualTo(true)
      assertThat((it.context["key"] as Map<String, Any>)["expr"]).isEqualTo(true)
    })

    repository.retrieve(PIPELINE, pipeline.id).apply {
      assertThat(status).isEqualTo(SUCCEEDED)
      // resolved expressions should be persisted
      assertThat(stages.first().context["expr"]).isEqualTo(true)
      assertThat((stages.first().context["key"] as Map<String, Any>)["expr"]).isEqualTo(true)
    }
  }

  @Test
  fun `a restarted branch will not stall due to original cancellation`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
        status = TERMINAL
        startTime = now().minusSeconds(30).toEpochMilli()
        endTime = now().minusSeconds(10).toEpochMilli()
      }
      stage {
        refId = "2"
        type = "dummy"
        status = CANCELED // parallel stage canceled when other failed
        startTime = now().minusSeconds(30).toEpochMilli()
        endTime = now().minusSeconds(10).toEpochMilli()
      }
      stage {
        refId = "3"
        requisiteStageRefIds = setOf("1", "2")
        type = "dummy"
        status = NOT_STARTED // never ran first time
      }
      status = TERMINAL
      startTime = now().minusSeconds(31).toEpochMilli()
      endTime = now().minusSeconds(9).toEpochMilli()
    }
    repository.store(pipeline)

    whenever(dummyTask.timeout) doReturn 2000L
    whenever(dummyTask.execute(any())) doReturn TaskResult.SUCCEEDED // second run succeeds

    context.restartAndRunToCompletion(pipeline.stageByRef("1"), runner::restart, repository)

    repository.retrieve(PIPELINE, pipeline.id).apply {
      assertThat(status).isEqualTo(CANCELED)
      assertThat(stageByRef("1").status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("2").status).isEqualTo(CANCELED)
    }
  }

  @Test
  fun `conditional stages can depend on outputs of previous stages`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
      }
      stage {
        refId = "2a"
        requisiteStageRefIds = setOf("1")
        type = "dummy"
        context["stageEnabled"] = mapOf(
          "type" to "expression",
          "expression" to "\${foo == true}"
        )
      }
      stage {
        refId = "2b"
        requisiteStageRefIds = setOf("1")
        type = "dummy"
        context["stageEnabled"] = mapOf(
          "type" to "expression",
          "expression" to "\${foo == false}"
        )
      }
    }
    repository.store(pipeline)

    whenever(dummyTask.timeout) doReturn 2000L
    whenever(dummyTask.execute(any())) doAnswer {
      val stage = it.arguments.first() as Stage
      if (stage.refId == "1") {
        TaskResult(SUCCEEDED, emptyMap<String, Any?>(), mapOf("foo" to false))
      } else {
        TaskResult.SUCCEEDED
      }
    }

    context.runToCompletion(pipeline, runner::start, repository)

    repository.retrieve(PIPELINE, pipeline.id).apply {
      assertThat(status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("1").status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("2a").status).isEqualTo(SKIPPED)
      assertThat(stageByRef("2b").status).isEqualTo(SUCCEEDED)
    }
  }

  @Test
  fun `conditional stages can depend on global context values after restart`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
        status = SUCCEEDED
      }
      stage {
        refId = "2a"
        requisiteStageRefIds = setOf("1")
        type = "dummy"
        status = SKIPPED
        context = mapOf(
          "stageEnabled" to mapOf(
            "type" to "expression",
            "expression" to "\${foo == true}"
          )
        )
      }
      stage {
        refId = "2b"
        requisiteStageRefIds = setOf("1")
        type = "dummy"
        status = TERMINAL
        context = mapOf(
          "stageEnabled" to mapOf(
            "type" to "expression",
            "expression" to "\${foo == false}"
          )
        )
      }
      status = TERMINAL
    }
    repository.store(pipeline)

    whenever(dummyTask.timeout) doReturn 2000L
    whenever(dummyTask.execute(any())) doAnswer {
      val stage = it.arguments.first() as Stage
      if (stage.refId == "1") {
        TaskResult(SUCCEEDED, emptyMap<String, Any?>(), mapOf("foo" to false))
      } else {
        TaskResult.SUCCEEDED
      }
    }

    context.restartAndRunToCompletion(pipeline.stageByRef("1"), runner::restart, repository)

    repository.retrieve(PIPELINE, pipeline.id).apply {
      assertThat(status).isEqualTo(SUCCEEDED)
      assertThat(stageByRef("2a").status).isEqualTo(SKIPPED)
      assertThat(stageByRef("2b").status).isEqualTo(SUCCEEDED)
    }
  }
}

@Configuration
@Import(
  EmbeddedRedisConfiguration::class,
  PropertyPlaceholderAutoConfiguration::class,
  OrcaConfiguration::class,
  QueueConfiguration::class,
  JedisConfiguration::class,
  JedisExecutionRepository::class,
  StageNavigator::class,
  RestrictExecutionDuringTimeWindow::class,
  OrcaQueueConfiguration::class
)
class TestConfig {
  @Bean
  fun queueRedisPool(jedisPool: Pool<Jedis>) = jedisPool

  @Bean
  fun registry(): Registry = NoopRegistry()

  @Bean
  fun dummyTask(): DummyTask = mock {
    on { timeout } doReturn Duration.ofMinutes(2).toMillis()
  }

  @Bean
  fun dummyStage() = object : StageDefinitionBuilder {
    override fun taskGraph(stage: Stage, builder: Builder) {
      builder.withTask("dummy", DummyTask::class.java)
    }

    override fun getType() = "dummy"
  }

  @Bean
  fun parallelStage() = object : StageDefinitionBuilder {
    override fun parallelStages(stage: Stage) =
      listOf("us-east-1", "us-west-2", "eu-west-1").map { region ->
        newStage(stage.execution, "dummy", "dummy $region", stage.context + mapOf("region" to region), stage, STAGE_BEFORE)
      }

    override fun getType() = "parallel"
  }

  @Bean
  fun currentInstanceId() = "localhost"

  @Bean
  fun contextParameterProcessor() = ContextParameterProcessor()

  @Bean
  fun defaultExceptionHandler() = DefaultExceptionHandler()

  @Bean
  fun deadMessageHandler(): DeadMessageCallback = { _, _ -> }

  @Bean
  @ConditionalOnMissingBean(Queue::class)
  fun queue(
    clock: Clock,
    deadMessageHandler: DeadMessageCallback,
    publisher: EventPublisher
  ) =
    InMemoryQueue(
      clock = clock,
      deadMessageHandler = deadMessageHandler,
      publisher = publisher
    )
}

