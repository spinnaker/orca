/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.igor.tasks

import com.google.common.base.Enums
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.igor.BuildService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit

@Slf4j
@Component
class MonitorJenkinsJobTask implements OverridableTimeoutRetryableTask {

  long backoffPeriod = 10000
  long timeout = TimeUnit.HOURS.toMillis(2)

  @Autowired
  BuildService buildService

  @Autowired
  RetrySupport retrySupport

  @Override
  TaskResult execute(StageExecution stage) {
    String master = stage.context.master
    String job = stage.context.job

    if (!stage.context.buildNumber) {
      log.error("failed to get build number for job ${job} from master ${master}")
      return TaskResult.ofStatus(ExecutionStatus.TERMINAL)
    }

    def buildNumber = (int) stage.context.buildNumber
    try {
      Map<String, Object> build = buildService.getBuild(buildNumber, master, job)
      Map outputs = [:]
      String result = build.result
      if ((build.building && build.building != 'false') || (build.running && build.running != 'false')) {
        return TaskResult.builder(ExecutionStatus.RUNNING).context([buildInfo: build]).build()
      }

      outputs.buildInfo = build

      def jobStatus = Optional.ofNullable(result)
          .map { Enums.getIfPresent(JenkinsJobStatus.class, it).orNull() }
          .orElse(null)
      if (jobStatus == null) {
        return TaskResult.builder(ExecutionStatus.RUNNING).context([buildInfo: build]).build()
      }

      ExecutionStatus taskStatus = jobStatus.executionStatusEquivalent
      if (jobStatus == JenkinsJobStatus.UNSTABLE && stage.context.markUnstableAsSuccessful) {
        taskStatus = JenkinsJobStatus.SUCCESS.executionStatusEquivalent
      }

      if (jobStatus.errorBreadcrumb != null) {
        stage.appendErrorMessage(jobStatus.errorBreadcrumb)
      }

      return TaskResult.builder(taskStatus).context(outputs).outputs(outputs).build()
    } catch (SpinnakerHttpException e) {
      if ([503, 500, 404].contains(e.responseCode)) {
        log.warn("Http ${e.responseCode} received from `igor`, retrying...")
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      }

      throw e
    }
  }

  /**
   * Maps Jenkins job statuses to {@link ExecutionStatus}
   */
  static enum JenkinsJobStatus {
    ABORTED(ExecutionStatus.CANCELED, "Job was aborted (see Jenkins)"),
    FAILURE(ExecutionStatus.TERMINAL, "Job failed (see Jenkins)"),
    SUCCESS(ExecutionStatus.SUCCEEDED, null),
    UNSTABLE(ExecutionStatus.TERMINAL, "Job is unstable")

    /**
     * Orca's equivalent execution status value.
     */
    ExecutionStatus executionStatusEquivalent

    /**
     * An optional error message breadcrumb that is surfaced to users when the jenkins job fails.
     */
    String errorBreadcrumb

    JenkinsJobStatus(ExecutionStatus executionStatusEquivalent, String errorBreadcrumb) {
      this.executionStatusEquivalent = executionStatusEquivalent
      this.errorBreadcrumb = errorBreadcrumb
    }
  }
}
