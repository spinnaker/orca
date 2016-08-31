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

package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.config.SpringBatchConfiguration
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.TerminateInstancesTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForDownInstanceHealthTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForTerminatedInstancesTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForUpInstanceHealthTask
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.config.OrcaPersistenceConfiguration
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.kato.tasks.DisableInstancesTask
import com.netflix.spinnaker.orca.kato.tasks.rollingpush.CheckForRemainingTerminationsTask
import com.netflix.spinnaker.orca.kato.tasks.rollingpush.DetermineTerminationCandidatesTask
import com.netflix.spinnaker.orca.kato.tasks.rollingpush.DetermineTerminationPhaseInstancesTask
import com.netflix.spinnaker.orca.kato.tasks.rollingpush.WaitForNewInstanceLaunchTask
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.test.JobCompletionListener
import com.netflix.spinnaker.orca.test.TestConfiguration
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.orca.ExecutionStatus.REDIRECT
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED

class RollingPushStageSpec extends Specification {

  private static final DefaultTaskResult SUCCESS = new DefaultTaskResult(SUCCEEDED)
  private static final DefaultTaskResult REDIR = new DefaultTaskResult(REDIRECT)

  @AutoCleanup("destroy")
  def applicationContext = new AnnotationConfigApplicationContext()
  @Shared def mapper = new OrcaObjectMapper()
  @Autowired PipelineStarter pipelineStarter

  def endTask = new TestTask(delegate: Mock(Task))
  def endStage = new AutowiredTestStage("end", endTask)

  def preCycleTask = Stub(DetermineTerminationCandidatesTask) {
    execute(_) >> SUCCESS
  }
  def startOfCycleTask = Mock(DetermineTerminationPhaseInstancesTask)
  def endOfCycleTask = Stub(CheckForRemainingTerminationsTask)
  def cycleTasks = [DisableInstancesTask, MonitorKatoTask, WaitForDownInstanceHealthTask, TerminateInstancesTask, WaitForTerminatedInstancesTask, ServerGroupCacheForceRefreshTask, WaitForNewInstanceLaunchTask, WaitForUpInstanceHealthTask].collect {
    if (RetryableTask.isAssignableFrom(it)) {
      Stub(it) {
        execute(_) >> SUCCESS
        getBackoffPeriod() >> 5000L
        getTimeout() >> 3600000L
      }
    } else {
      Stub(it) {
        execute(_) >> SUCCESS
      }
    }
  }

  def setup() {
    applicationContext.with {
      register(EmbeddedRedisConfiguration, JesqueConfiguration,
               BatchTestConfiguration, SpringBatchConfiguration, OrcaConfiguration, OrcaPersistenceConfiguration,
               JobCompletionListener, TestConfiguration)
      register(RollingPushStage)
      register(RollingPushStage.RedirectResetListener)
      beanFactory.registerSingleton("endStage", endStage)
      beanFactory.registerSingleton("task1", endTask)
      ([preCycleTask, startOfCycleTask, endOfCycleTask] + cycleTasks).each { task ->
        beanFactory.registerSingleton(task.getClass().simpleName, task)
      }
      refresh()
      beanFactory.autowireBean(endStage)
      beanFactory.autowireBean(this)
    }
  }

  def "rolling push loops until completion"() {
    given:
    endOfCycleTask.execute(_) >> REDIR >> REDIR >> SUCCESS

    when:
    pipelineStarter.start(configJson)

    then:
    3 * startOfCycleTask.execute(_) >> SUCCESS

    then:
    1 * endTask.delegate.execute(_) >> SUCCESS

    where:
    config = [
      application: "app",
      name       : "my-pipeline",
      stages     : [[type: RollingPushStage.PIPELINE_CONFIG_TYPE], [type: "end"]],
      version: 2
    ]
    configJson = mapper.writeValueAsString(config)

  }

  @CompileStatic
  static class AutowiredTestStage implements StageDefinitionBuilder {
    private final String type
    private final List<Task> tasks = []

    AutowiredTestStage(String type, Task... tasks) {
      this.type = type
      this.tasks.addAll tasks
    }

    @Override
    def <T extends Execution> List<StageDefinitionBuilder.TaskDefinition> taskGraph(Stage<T> parentStage) {
      def i = 1
      tasks.collect { Task task ->
       new StageDefinitionBuilder.TaskDefinition("task${i++}", task.class)
      }
    }

    @Override
    String getType() {
      return type
    }
  }

  static class TestTask implements Task {
    @Delegate
    Task delegate
  }
}
