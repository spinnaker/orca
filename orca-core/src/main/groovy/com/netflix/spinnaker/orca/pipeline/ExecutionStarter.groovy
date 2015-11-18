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

package com.netflix.spinnaker.orca.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.appinfo.InstanceInfo
import com.netflix.spinnaker.orca.pipeline.model.Execution
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.batch.core.*
import org.springframework.batch.core.configuration.JobRegistry
import org.springframework.batch.core.configuration.support.ReferenceJobFactory
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

@Slf4j
@CompileStatic
abstract class ExecutionStarter<T extends Execution> {

  private final String type

  ExecutionStarter(String type) {
    this.type = type
  }

  @Autowired protected JobRegistry jobRegistry
  @Autowired protected JobLauncher launcher
  @Autowired protected JobOperator jobOperator
  @Autowired protected JobRepository jobRepository
  @Autowired protected ObjectMapper mapper
  @Autowired @Qualifier("instanceInfo") protected InstanceInfo currentInstance

  T start(String configJson) {
    Map<String, Serializable> config = mapper.readValue(configJson, Map)
    def subject = create(config)
    persistExecution(subject)

    if (queueExecution(subject)) {
      log.info "Queueing: $subject.id"
      return subject
    }

    return startExecution(subject)
  }

  T startExecution(T subject) {
    def job = createJob(subject)
    persistExecution(subject)
    if (!subject.startTime && subject.status.isComplete()) {
      // this execution has never been started but is already in a complete status (indicates a failure building execution graph)
      onCompleteBeforeLaunch(subject)
      return subject
    }

    log.info "Starting $subject.id"
    launcher.run job, createJobParameters(subject)
    afterJobLaunch(subject)

    return subject
  }

  public Job createJob(T subject) {
    def jobName = executionJobBuilder.jobNameFor(subject)
    if (!jobRegistry.jobNames.contains(jobName)) {
      def job = executionJobBuilder.build(subject)
      jobRegistry.register(new ReferenceJobFactory(job))
    }
    return jobRegistry.getJob(jobName)
  }

  void resume(T subject) {
    log.warn "Resuming $subject.id"
    def jobName = executionJobBuilder.jobNameFor(subject)
    def execution = jobRepository.getLastJobExecution(jobName, createJobParameters(subject))
    if (execution) {
      jobOperator.restart(execution.id)
    } else {
      startExecution(subject)
    }
  }

  protected JobParameters createJobParameters(T subject) {
    def params = new JobParametersBuilder()
    params.addString(type, subject.id)
    params.addString("application", subject.application)
    params.addString("timestamp", System.currentTimeMillis() as String)
    params.toJobParameters()
  }

  /**
   * Hook for subclasses to decide if this execution should be queued or start immediately.
   * @return true iff the stage should be queued.
   */
  protected boolean queueExecution(T subject) {false}

  /**
   * Hook for anything necessary after the job has started.
   */
  protected void afterJobLaunch(T subject) {}

  /**
   * Hook for when any configured stage has indicated that this pipeline is complete (usually terminated) before
   * actually be started. Subclasses overriding this implementation should call super.onCompleteBeforeLaunch().
   */
  protected void onCompleteBeforeLaunch(T subject) {
    log.warn("Unable to start execution that has previously been completed " +
      "(${subject.class.simpleName}:${subject.id}:${subject.status})")
  }

  protected abstract ExecutionJobBuilder<T> getExecutionJobBuilder()

  protected abstract void persistExecution(T subject)

  protected abstract T create(Map<String, Serializable> config)
}
