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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.netflix.appinfo.InstanceInfo.InstanceStatus.*
import com.netflix.discovery.StatusChangeEvent
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.QueueConfiguration
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode.Builder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.nhaarman.mockito_kotlin.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner
import java.lang.Thread.sleep
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(SpringJUnit4ClassRunner::class)
@ContextConfiguration(
  classes = arrayOf(TestConfig::class, QueueConfiguration::class)
)
class SpringIntegrationSpec {

  @Autowired lateinit var queue: Queue
  @Autowired lateinit var runner: QueueExecutionRunner
  @Autowired lateinit var repository: ExecutionRepository
  @Autowired lateinit var dummyTask: DummyTask
  @Autowired lateinit var context: ApplicationContext

  @Before fun discoveryUp() {
    context.publishEvent(RemoteStatusChangedEvent(StatusChangeEvent(STARTING, UP)))
  }

  @After fun discoveryDown() {
    context.publishEvent(RemoteStatusChangedEvent(StatusChangeEvent(UP, OUT_OF_SERVICE)))
  }

  @Test fun `can run a simple pipeline`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
      }
    }
    whenever(repository.retrievePipeline(pipeline.id)).thenReturn(pipeline)

    whenever(dummyTask.execute(any())).thenReturn(TaskResult.SUCCEEDED)

    runner.start(pipeline)

    sleep(2_000)

    argumentCaptor<ExecutionStatus>().apply {
      verify(repository, atLeastOnce()).updateStatus(eq(pipeline.id), capture())
      assertThat(lastValue, equalTo(SUCCEEDED))
    }
  }
}

@Configuration
open class TestConfig {
  @Bean open fun registry(): Registry = mock() {
    on { createId(any<String>()) }.doReturn(mock<Id>())
    on { counter(any<Id>()) }.doReturn(mock<Counter>())
  }

  @Bean open fun executionRepository(): ExecutionRepository = mock()
  @Bean open fun dummyTask(): DummyTask = mock()
  @Bean open fun dummyStage(): StageDefinitionBuilder = object : StageDefinitionBuilder {
    override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: Builder) {
      builder.withTask("dummy", DummyTask::class.java)
    }

    override fun getType() = "dummy"
  }

  @Bean open fun stupidPretendScheduler(worker: ExecutionWorker): Any = object : InitializingBean, DisposableBean {
    private val running = AtomicBoolean(false)

    override fun afterPropertiesSet() {
      running.set(true)
      Thread(Runnable {
        while (running.get()) {
          worker.pollOnce()
          sleep(10)
        }
      }).start()
    }

    override fun destroy() {
      running.set(false)
    }
  }
}
