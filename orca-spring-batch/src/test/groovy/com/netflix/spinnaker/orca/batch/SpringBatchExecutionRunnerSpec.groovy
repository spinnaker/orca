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

package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.batch.listeners.SpringBatchExecutionListenerProvider
import com.netflix.spinnaker.orca.listeners.ExecutionListener
import com.netflix.spinnaker.orca.listeners.StageListener
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner
import com.netflix.spinnaker.orca.pipeline.ExecutionRunnerSpec
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import org.springframework.batch.core.configuration.JobRegistry
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.retry.backoff.Sleeper
import spock.lang.Subject

class SpringBatchExecutionRunnerSpec extends ExecutionRunnerSpec {

  def applicationContext = new AnnotationConfigApplicationContext()
  @Autowired JobRegistry jobRegistry
  @Autowired JobBuilderFactory jobs
  @Autowired StepBuilderFactory steps
  @Autowired TaskTaskletAdapter taskTaskletAdapter
  @Autowired(required = false) Collection<Task> tasks = []
  @Autowired(required = false) Collection<StageListener> stageListeners = []
  @Autowired(required = false) Collection<ExecutionListener> executionListeners = []
  @Autowired JobLauncher jobLauncher
  def executionRepository = Stub(ExecutionRepository)

  private void startContext(
    @ClosureParams(value = SimpleType, options = "org.springframework.beans.factory.config.ConfigurableListableBeanFactory")
      Closure withBeans) {
    applicationContext.with {
      register(BatchTestConfiguration, TaskTaskletAdapterImpl)
      beanFactory.registerSingleton("executionRepository", executionRepository)
      beanFactory.registerSingleton("exceptionHandler", Mock(ExceptionHandler))
      beanFactory.registerSingleton("sleeper", Stub(Sleeper))
      withBeans(beanFactory)
      refresh()

      beanFactory.autowireBean(this)
    }
  }

  @Override
  ExecutionRunner create(StageDefinitionBuilder... stageDefBuilders) {
    startContext { beanFactory ->
      beanFactory.registerSingleton("task1", new TestTask(delegate: Mock(Task)))
    }
    return new SpringBatchExecutionRunner(
      stageDefBuilders.toList(),
      executionRepository,
      jobLauncher,
      jobRegistry,
      jobs,
      steps,
      taskTaskletAdapter,
      tasks,
      new SpringBatchExecutionListenerProvider(executionRepository, stageListeners, executionListeners)
    )
  }

  def "creates a batch job and runs it"() {
    given:
    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    def stageDefinitionBuilder = Stub(StageDefinitionBuilder) {
      getType() >> stageType
      taskGraph(_) >> [new StageDefinitionBuilder.TaskDefinition("task1", TestTask)]
    }
    @Subject runner = create(stageDefinitionBuilder)

    when:
    runner.start(execution)

    then:
    1 * tasks[0].delegate.execute(_)

    where:
    stageType = "foo"
    execution = Pipeline.builder().withId("1").withStage(stageType).build()
  }

  static class TestTask implements Task {
    @Delegate
    Task delegate
  }
}
