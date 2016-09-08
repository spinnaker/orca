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

import groovy.transform.CompileStatic
import com.netflix.spinnaker.config.SpringBatchConfiguration
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.TerminateInstancesTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForDownInstanceHealthTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForTerminatedInstancesTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForUpInstanceHealthTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.config.OrcaPersistenceConfiguration
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.kato.tasks.DisableInstancesTask
import com.netflix.spinnaker.orca.kato.tasks.rollingpush.CheckForRemainingTerminationsTask
import com.netflix.spinnaker.orca.kato.tasks.rollingpush.DetermineTerminationCandidatesTask
import com.netflix.spinnaker.orca.kato.tasks.rollingpush.DetermineTerminationPhaseInstancesTask
import com.netflix.spinnaker.orca.kato.tasks.rollingpush.WaitForNewInstanceLaunchTask
import com.netflix.spinnaker.orca.pipeline.PipelineLauncher
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.test.JobCompletionListener
import com.netflix.spinnaker.orca.test.TestConfiguration
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import org.spockframework.spring.xml.SpockMockFactoryBean
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import org.springframework.test.context.ContextConfiguration
import spock.lang.Shared
import spock.lang.Specification
import static com.netflix.spinnaker.orca.ExecutionStatus.REDIRECT
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.kato.pipeline.RollingPushStage.PushCompleteTask

@ContextConfiguration(classes = [EmbeddedRedisConfiguration, JesqueConfiguration,
  BatchTestConfiguration, SpringBatchConfiguration, OrcaConfiguration,
  OrcaPersistenceConfiguration, JobCompletionListener, TestConfiguration,
  MockTaskConfig, RollingPushStage, DownstreamStage])
class RollingPushStageSpec extends Specification {

  @Shared def mapper = new OrcaObjectMapper()
  @Autowired PipelineLauncher pipelineLauncher

  /**
   * Task that runs before the loop
   */
  @Autowired @Qualifier("preLoop") Task preLoopTask

  /**
   * Task that is the start of the loop
   */
  @Autowired @Qualifier("startOfLoop") Task startOfLoopTask

  /**
   * Tasks inside the loop
   */
  @Autowired @Qualifier("inLoop") Collection<Task> loopTasks

  /**
   * Task that is the end of the loop
   */
  @Autowired @Qualifier("endOfLoop") Task endOfLoopTask

  /**
   * Task that is after the end of the loop
   */
  @Autowired @Qualifier("postLoop") Task postLoopTask

  /**
   * Task in the stage downstream from rolling push
   */
  @Autowired @Qualifier("downstream") Task downstreamTask

  private static final SUCCESS = new DefaultTaskResult(SUCCEEDED)
  private static final REDIR = new DefaultTaskResult(REDIRECT)

  def "rolling push loops until completion"() {
    given: "everything in the rolling push loop will succeed"
    preLoopTask.execute(_) >> SUCCESS
    startOfLoopTask.execute(_) >> SUCCESS
    loopTasks.each { task ->
      task.execute(_) >> SUCCESS
      if (task instanceof RetryableTask) {
        task.getBackoffPeriod() >> 5000L
        task.getTimeout() >> 3600000L
      }
    }

    and: "the loop will repeat a couple of times"
    endOfLoopTask.execute(_) >> REDIR >> REDIR >> SUCCESS

    when:
    pipelineLauncher.start(configJson)

    then: "the loop repeats"
    3 * startOfLoopTask.execute(_) >> SUCCESS

    then: "the stage completes"
    1 * postLoopTask.execute(_) >> SUCCESS

    then: "the downstream stage runs afterward"
    1 * downstreamTask.execute(_) >> SUCCESS

    where:
    config = [
      application: "app",
      name       : "my-pipeline",
      stages     : [
        [type: RollingPushStage.PIPELINE_CONFIG_TYPE, refId: "1"],
        [type: "downstream", refId: "2", requisiteStageRefIds: ["1"]]
      ],
      version    : 2
    ]
    configJson = mapper.writeValueAsString(config)
  }

  static class MockTaskConfig {
    @Bean
    @Qualifier("preLoop")
    FactoryBean<DetermineTerminationCandidatesTask> determineTerminationCandidatesTask() {
      new SpockMockFactoryBean<>(DetermineTerminationCandidatesTask)
    }

    @Bean
    @Qualifier("startOfLoop")
    FactoryBean<DetermineTerminationPhaseInstancesTask> determineTerminationPhaseInstancesTask() {
      new SpockMockFactoryBean<>(DetermineTerminationPhaseInstancesTask)
    }

    @Bean
    @Qualifier("endOfLoop")
    FactoryBean<CheckForRemainingTerminationsTask> checkForRemainingTerminationsTask() {
      new SpockMockFactoryBean<>(CheckForRemainingTerminationsTask)
    }

    @Bean
    @Qualifier("postLoop")
    FactoryBean<PushCompleteTask> pushCompleteTask() {
      new SpockMockFactoryBean<>(PushCompleteTask)
    }

    @Bean
    @Qualifier("downstream")
    FactoryBean<DownstreamTask> endTask() {
      new SpockMockFactoryBean<>(DownstreamTask)
    }

    @Bean
    @Qualifier("inLoop")
    FactoryBean<DisableInstancesTask> disableInstancesTask() {
      new SpockMockFactoryBean<>(DisableInstancesTask)
    }

    @Bean
    @Qualifier("inLoop")
    FactoryBean<MonitorKatoTask> monitorKatoTask() {
      new SpockMockFactoryBean<>(MonitorKatoTask)
    }

    @Bean
    @Qualifier("inLoop")
    FactoryBean<WaitForDownInstanceHealthTask> waitForDownInstanceHealthTask() {
      new SpockMockFactoryBean<>(WaitForDownInstanceHealthTask)
    }

    @Bean
    @Qualifier("inLoop")
    FactoryBean<TerminateInstancesTask> terminateInstancesTask() {
      new SpockMockFactoryBean<>(TerminateInstancesTask)
    }

    @Bean
    @Qualifier("inLoop")
    FactoryBean<WaitForTerminatedInstancesTask> waitForTerminatedInstancesTask() {
      new SpockMockFactoryBean<>(WaitForTerminatedInstancesTask)
    }

    @Bean
    @Qualifier("inLoop")
    FactoryBean<ServerGroupCacheForceRefreshTask> serverGroupCacheForceRefreshTask() {
      new SpockMockFactoryBean<>(ServerGroupCacheForceRefreshTask)
    }

    @Bean
    @Qualifier("inLoop")
    FactoryBean<WaitForNewInstanceLaunchTask> waitForNewInstanceLaunchTask() {
      new SpockMockFactoryBean<>(WaitForNewInstanceLaunchTask)
    }

    @Bean
    @Qualifier("inLoop")
    FactoryBean<WaitForUpInstanceHealthTask> waitForUpInstanceHealthTask() {
      new SpockMockFactoryBean<>(WaitForUpInstanceHealthTask)
    }
  }

  @Component
  @CompileStatic
  static class DownstreamStage implements StageDefinitionBuilder {
    def <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
      builder.withTask("task1", DownstreamTask)
    }
  }

  @CompileStatic
  static interface DownstreamTask extends Task {}
}
