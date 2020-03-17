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

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution

import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.igor.BuildService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Slf4j
@Component
class MonitorQueuedJenkinsJobTask implements OverridableTimeoutRetryableTask {

  long backoffPeriod = 10000
  long timeout = TimeUnit.HOURS.toMillis(2)

  @Autowired
  BuildService buildService

  @Override
  TaskResult execute(StageExecution stage) {
    String master = stage.context.master
    String job = stage.context.job
    String queuedBuild = stage.context.queuedBuild

    try {
      Map<String, Object> build = buildService.queuedBuild(master, queuedBuild)
      if (build?.number == null) {
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      } else {
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([buildNumber: build.number]).build()
      }
    } catch (RetrofitError e) {
      if ([503, 500, 404].contains(e.response?.status)) {
        log.warn("Http ${e.response.status} received from `igor`, retrying...")
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      }
      throw e
    }
  }
}
