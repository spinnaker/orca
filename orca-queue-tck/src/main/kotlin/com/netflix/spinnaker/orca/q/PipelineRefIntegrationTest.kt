/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.orca.q

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.OrcaQueueConfiguration
import com.netflix.spinnaker.config.QueueConfiguration
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusChangeEvent
import com.netflix.spinnaker.kork.discovery.InstanceStatus
import com.netflix.spinnaker.kork.discovery.RemoteStatusChangedEvent
import com.netflix.spinnaker.orca.api.pipeline.CancellableStage
import com.netflix.spinnaker.orca.api.pipeline.SkippableTask
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.api.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.test.pipeline
import com.netflix.spinnaker.orca.api.test.stage
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.exceptions.DefaultExceptionHandler
import com.netflix.spinnaker.orca.ext.withTask
import com.netflix.spinnaker.orca.listeners.DelegatingApplicationEventMulticaster
import com.netflix.spinnaker.orca.pipeline.RestrictExecutionDuringTimeWindow
import com.netflix.spinnaker.orca.pipeline.StageExecutionFactory
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.netflix.spinnaker.orca.sql.pipeline.persistence.PipelineRefTrigger
import com.netflix.spinnaker.q.DeadMessageCallback
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.q.memory.InMemoryQueue
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.event.ApplicationEventMulticaster
import org.springframework.context.event.SimpleApplicationEventMulticaster
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.Clock
import java.time.Duration
import java.time.ZoneId

@SpringBootTest(
  classes = [TestConfig::class],
  properties = ["queue.retry.delay.ms=10"]
)
@ExtendWith(SpringExtension::class)
abstract class PipelineRefQueueIntegrationTest {
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

  @Value("\${tasks.execution-window.timezone:America/Los_Angeles}")
  lateinit var timeZoneId: String
  private val timeZone by lazy { ZoneId.of(timeZoneId) }

  @BeforeEach
  fun discoveryUp() {
    context.publishEvent(RemoteStatusChangedEvent(DiscoveryStatusChangeEvent(InstanceStatus.STARTING, InstanceStatus.UP)))
  }

  @AfterEach
  fun discoveryDown() {
    context.publishEvent(RemoteStatusChangedEvent(DiscoveryStatusChangeEvent(InstanceStatus.UP, InstanceStatus.OUT_OF_SERVICE)))
  }

  @AfterEach
  fun resetMocks() {
    reset(dummyTask)
    whenever(dummyTask.extensionClass) doReturn dummyTask::class.java
    whenever(dummyTask.getDynamicTimeout(any())) doReturn 2000L
    whenever(dummyTask.isEnabledPropertyName) doReturn SkippableTask.isEnabledPropertyName(dummyTask.javaClass.simpleName)
  }

  @Test
  fun `can run a simple pipeline with pipelineRef and includedNestedExecution flag works as expected`() {
    val parentPipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
      }
    }
    val childPipeline = pipeline {
      application = "spinnaker"
      trigger = PipelineRefTrigger(parentExecutionId = parentPipeline.id)
      stage {
        refId = "1"
        type = "dummy"
      }
    }

    repository.store(parentPipeline)
    repository.store(childPipeline)

    whenever(dummyTask.execute(any())) doReturn TaskResult.SUCCEEDED

    context.runToCompletion(parentPipeline, runner::start, repository)

    assertThat(repository.retrieve(ExecutionType.PIPELINE, parentPipeline.id, true).status)
      .isEqualTo(ExecutionStatus.SUCCEEDED)

    context.runToCompletion(childPipeline, runner::start, repository)

    val childExecutionWithNestedExecutions = repository.retrieve(ExecutionType.PIPELINE, childPipeline.id, true)
    assertThat(childExecutionWithNestedExecutions.status).isEqualTo(ExecutionStatus.SUCCEEDED)
    assertThat(childExecutionWithNestedExecutions.trigger)
      .isInstanceOf(PipelineTrigger::class.java)
      .extracting("parentExecution.id")
      .isEqualTo(parentPipeline.id)

    val childExecutionWithoutNestedExecutions = repository.retrieve(ExecutionType.PIPELINE, childPipeline.id, false)
    assertThat(childExecutionWithoutNestedExecutions.status).isEqualTo(ExecutionStatus.SUCCEEDED)
    assertThat(childExecutionWithoutNestedExecutions.trigger)
      .isInstanceOf(PipelineRefTrigger::class.java)
      .extracting("parentExecutionId")
      .isEqualTo(parentPipeline.id)
  }

  @Test
  fun `can run a simple pipeline with pipelineTrigger and it is transformed to pipelineRefTrigger`() {
    val parentPipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
      }
    }
    val childPipeline = pipeline {
      application = "spinnaker"
      trigger = PipelineTrigger(parentExecution = parentPipeline)
      stage {
        refId = "1"
        type = "dummy"
      }
    }

    repository.store(parentPipeline)
    repository.store(childPipeline)

    whenever(dummyTask.execute(any())) doReturn TaskResult.SUCCEEDED

    context.runToCompletion(parentPipeline, runner::start, repository)

    assertThat(repository.retrieve(ExecutionType.PIPELINE, parentPipeline.id, true).status)
      .isEqualTo(ExecutionStatus.SUCCEEDED)

    context.runToCompletion(childPipeline, runner::start, repository)

    val childExecutionWithNestedExecutions = repository.retrieve(ExecutionType.PIPELINE, childPipeline.id, true)
    assertThat(childExecutionWithNestedExecutions.status).isEqualTo(ExecutionStatus.SUCCEEDED)
    assertThat(childExecutionWithNestedExecutions.trigger)
      .isInstanceOf(PipelineTrigger::class.java)
      .extracting("parentExecution.id")
      .isEqualTo(parentPipeline.id)

    val childExecutionWithoutNestedExecutions = repository.retrieve(ExecutionType.PIPELINE, childPipeline.id, false)
    assertThat(childExecutionWithoutNestedExecutions.status).isEqualTo(ExecutionStatus.SUCCEEDED)
    assertThat(childExecutionWithoutNestedExecutions.trigger)
      .isInstanceOf(PipelineRefTrigger::class.java)
      .extracting("parentExecutionId")
      .isEqualTo(parentPipeline.id)
  }

  @Test
  fun `can run a simple pipeline with DefaultTrigger and includeNestedExecutions does not make any iteration`() {
    val parentPipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
      }
    }
    val childPipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
      }
    }

    repository.store(parentPipeline)
    repository.store(childPipeline)

    whenever(dummyTask.execute(any())) doReturn TaskResult.SUCCEEDED

    context.runToCompletion(parentPipeline, runner::start, repository)

    assertThat(repository.retrieve(ExecutionType.PIPELINE, parentPipeline.id, false).status)
      .isEqualTo(ExecutionStatus.SUCCEEDED)

    context.runToCompletion(childPipeline, runner::start, repository)

    val childExecutionWithNestedExecutions = repository.retrieve(ExecutionType.PIPELINE, childPipeline.id, true)
    assertThat(childExecutionWithNestedExecutions.status).isEqualTo(ExecutionStatus.SUCCEEDED)
    assertThat(childExecutionWithNestedExecutions.trigger).isInstanceOf(DefaultTrigger::class.java)

    val childExecutionWithoutNestedExecutions = repository.retrieve(ExecutionType.PIPELINE, childPipeline.id, false)
    assertThat(childExecutionWithoutNestedExecutions.status).isEqualTo(ExecutionStatus.SUCCEEDED)
    assertThat(childExecutionWithoutNestedExecutions.trigger).isInstanceOf(DefaultTrigger::class.java)
  }
}

@Configuration
@Import(
  PropertyPlaceholderAutoConfiguration::class,
  OrcaConfiguration::class,
  QueueConfiguration::class,
  StageNavigator::class,
  RestrictExecutionDuringTimeWindow::class,
  OrcaQueueConfiguration::class
)
class PipelineRefTestConfig {

  @Bean
  fun registry(): Registry = NoopRegistry()

  @Bean
  fun dummyTask(): DummyTask = mock {
    on { extensionClass } doReturn DummyTask::class.java
    on { getDynamicTimeout(any()) } doReturn Duration.ofMinutes(2).toMillis()
    on { isEnabledPropertyName } doReturn SkippableTask.isEnabledPropertyName(DummyTask::class.java.simpleName)
  }

  @Bean
  fun dummyStage() = object : StageDefinitionBuilder {
    override fun taskGraph(stage: StageExecution, builder: TaskNode.Builder) {
      builder.withTask<DummyTask>("dummy")
    }

    override fun getType() = "dummy"
  }

  @Bean
  fun parallelStage() = object : StageDefinitionBuilder {
    override fun beforeStages(parent: StageExecution, graph: StageGraphBuilder) {
      listOf("us-east-1", "us-west-2", "eu-west-1")
        .map { region ->
          StageExecutionFactory.newStage(parent.execution, "dummy", "dummy $region", parent.context + mapOf("region" to region), parent, SyntheticStageOwner.STAGE_BEFORE)
        }
        .forEach { graph.add(it) }
    }

    override fun getType() = "parallel"
  }

  @Bean
  fun syntheticFailureStage() = object : StageDefinitionBuilder {
    override fun getType() = "syntheticFailure"

    override fun taskGraph(stage: StageExecution, builder: TaskNode.Builder) {
      builder.withTask<DummyTask>("dummy")
    }

    override fun onFailureStages(stage: StageExecution, graph: StageGraphBuilder) {
      graph.add {
        it.type = "dummy"
        it.name = "onFailure1"
        it.context = stage.context
      }

      graph.add {
        it.type = "dummy"
        it.name = "onFailure2"
        it.context = stage.context
      }
    }
  }

  @Bean
  fun pipelineStage(@Autowired repository: ExecutionRepository): StageDefinitionBuilder =
    object : CancellableStage, StageDefinitionBuilder {
      override fun taskGraph(stage: StageExecution, builder: TaskNode.Builder) {
        builder.withTask<DummyTask>("dummy")
      }

      override fun getType() = "pipeline"

      override fun cancel(stage: StageExecution?): CancellableStage.Result {
        repository.cancel(ExecutionType.PIPELINE, stage!!.context["executionId"] as String)
        return CancellableStage.Result(stage, mapOf("foo" to "bar"))
      }
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
      deadMessageHandlers = listOf(deadMessageHandler),
      publisher = publisher
    )

  @Bean
  fun applicationEventMulticaster(@Qualifier("applicationEventTaskExecutor") taskExecutor: ThreadPoolTaskExecutor): ApplicationEventMulticaster {
    // TODO rz - Add error handlers
    val async = SimpleApplicationEventMulticaster()
    async.setTaskExecutor(taskExecutor)
    val sync = SimpleApplicationEventMulticaster()

    return DelegatingApplicationEventMulticaster(sync, async)
  }
}
