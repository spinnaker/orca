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

package com.netflix.spinnaker.orca.config

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.batch.ExecutionPropagationListener
import com.netflix.spinnaker.orca.libdiffs.ComparableLooseVersion
import com.netflix.spinnaker.orca.libdiffs.DefaultComparableLooseVersion
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationContext

import java.time.Clock
import java.time.Duration
import java.util.concurrent.ThreadPoolExecutor
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.ValueFunction
import com.netflix.spinnaker.kork.eureka.EurekaConfiguration
import com.netflix.spinnaker.orca.batch.StageStatusPropagationListener
import com.netflix.spinnaker.orca.batch.StageTaskPropagationListener
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.batch.exceptions.DefaultExceptionHandler
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.notifications.scheduling.SuspendedPipelinesNotificationHandler
import com.netflix.spinnaker.orca.pipeline.OrchestrationStarter
import com.netflix.spinnaker.orca.pipeline.PipelineStarterListener
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.PipelineStack
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryPipelineStack
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.springframework.batch.core.configuration.ListableJobLocator
import org.springframework.batch.core.configuration.annotation.BatchConfigurer
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.launch.support.SimpleJobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.*
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import rx.Scheduler
import rx.schedulers.Schedulers
import static java.time.temporal.ChronoUnit.MINUTES
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Configuration
@Import([EurekaConfiguration])
@ComponentScan([
"com.netflix.spinnaker.orca.pipeline",
"com.netflix.spinnaker.orca.notifications.scheduling",
"com.netflix.spinnaker.orca.restart",
"com.netflix.spinnaker.orca.deprecation"
])
@CompileStatic
class OrcaConfiguration {

  @Bean
  Clock clock() {
    Clock.systemDefaultZone()
  }

  @Bean
  Duration minInactivity() {
    Duration.of(3, MINUTES)
  }

  @Bean Scheduler scheduler() {
    Schedulers.io()
  }

  @Bean
  @CompileDynamic
  @ConditionalOnMissingBean(TaskExecutor)
  TaskExecutor getTaskExecutor(Registry registry) {
    def executor = new ThreadPoolTaskExecutor(maxPoolSize: 150, corePoolSize: 150)
    applyThreadPoolMetrics(registry, executor, "TaskExecutor")
    return executor
  }

  @Bean @ConditionalOnMissingBean(BatchConfigurer)
  BatchConfigurer batchConfigurer(TaskExecutor taskExecutor) {
    new MultiThreadedBatchConfigurer(taskExecutor)
  }

  @Bean @ConditionalOnMissingBean(JobOperator)
  JobOperator jobOperator(JobLauncher jobLauncher, JobRepository jobRepository, JobExplorer jobExplorer,
                          ListableJobLocator jobRegistry) {
    def jobOperator = new SimpleJobOperator()
    jobOperator.jobLauncher = jobLauncher
    jobOperator.jobRepository = jobRepository
    jobOperator.jobExplorer = jobExplorer
    jobOperator.jobRegistry = jobRegistry
    return jobOperator
  }

  @Bean
  @Scope(SCOPE_PROTOTYPE)
  ObjectMapper mapper() {
    new OrcaObjectMapper()
  }

  @Bean @ConditionalOnMissingBean(name = "pipelineStack")
  PipelineStack pipelineStack() {
    new InMemoryPipelineStack()
  }

  @Bean OrchestrationStarter orchestrationStarter() {
    new OrchestrationStarter()
  }

  @Bean @Scope(SCOPE_PROTOTYPE) // Scope is really important here...
  SuspendedPipelinesNotificationHandler suspendedPipelinesNotificationHandler(Map input) {
    new SuspendedPipelinesNotificationHandler(input)
  }

  @Bean @Order(Ordered.LOWEST_PRECEDENCE)
  DefaultExceptionHandler defaultExceptionHandler() {
    new DefaultExceptionHandler()
  }

  @Bean
  TaskTaskletAdapter taskTaskletAdapter(ExecutionRepository executionRepository,
                                        List<ExceptionHandler> exceptionHandlers,
                                        Registry registry) {
    new TaskTaskletAdapter(executionRepository, exceptionHandlers, registry)
  }

  @Bean
  StageStatusPropagationListener stageStatusPropagationListener(ExecutionRepository executionRepository) {
    new StageStatusPropagationListener(executionRepository)
  }

  @Bean
  StageTaskPropagationListener stageTaskPropagationListener(ExecutionRepository executionRepository) {
    new StageTaskPropagationListener(executionRepository)
  }

  @Bean
  ExecutionPropagationListener executionPropagationListenerBefore(ExecutionRepository executionRepository) {
    // need a dedicated beforeJob listener due to how spring boot ordered listeners
    new ExecutionPropagationListener(executionRepository, true, false)
  }

  @Bean
  ExecutionPropagationListener executionPropagationListenerAfter(ExecutionRepository executionRepository) {
    // need a dedicated afterJob listener due to how spring boot ordered listeners
    new ExecutionPropagationListener(executionRepository, false, true)
  }

  @Bean
  PipelineStarterListener pipelineStarterListener() {
    new PipelineStarterListener()
  }

  @Bean
  @ConditionalOnProperty(value = 'jarDiffs.enabled', matchIfMissing = false)
  ComparableLooseVersion comparableLooseVersion() {
    new DefaultComparableLooseVersion()
  }

  @Bean
  StageNavigator stageNavigator(ApplicationContext applicationContext) {
    return new StageNavigator(applicationContext)
  }

  @CompileDynamic
  public static ThreadPoolTaskExecutor applyThreadPoolMetrics(Registry registry,
                                                          ThreadPoolTaskExecutor executor,
                                                          String threadPoolName) {
    def createGuage = { String name, Closure valueCallback ->
      def id = registry
        .createId("threadpool.${name}" as String)
        .withTag("id", threadPoolName)

      registry.gauge(id, executor, new ValueFunction() {
        @Override
        double apply(Object ref) {
          valueCallback(((ThreadPoolTaskExecutor) ref).threadPoolExecutor)
        }
      })
    }

    createGuage.call("activeCount", { ThreadPoolExecutor e -> e.activeCount })
    createGuage.call("maximumPoolSize", { ThreadPoolExecutor e -> e.maximumPoolSize })
    createGuage.call("corePoolSize", { ThreadPoolExecutor e -> e.corePoolSize })
    createGuage.call("poolSize", { ThreadPoolExecutor e -> e.poolSize })
    createGuage.call("blockingQueueSize", { ThreadPoolExecutor e -> e.queue.size() })

    return executor
  }
}
