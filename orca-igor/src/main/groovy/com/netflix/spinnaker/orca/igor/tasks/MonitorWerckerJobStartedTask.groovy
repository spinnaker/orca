/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.orca.igor.tasks

import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.igor.BuildService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull
import java.util.concurrent.TimeUnit

@Slf4j
@Component
class MonitorWerckerJobStartedTask implements OverridableTimeoutRetryableTask {
  long backoffPeriod = 10000
  long timeout = TimeUnit.HOURS.toMillis(2)

  @Autowired
  BuildService buildService

  @Override
  TaskResult execute(@Nonnull final StageExecution stage) {
    String master = stage.context.master
    String job = stage.context.job
    Integer buildNumber = Integer.valueOf(stage.context.queuedBuild)

    try {
      Map<String, Object> build = buildService.getBuild(buildNumber, master, job)
      if ("not_built".equals(build?.result) || build?.number == null) {
        //The build has not yet started, so the job started monitoring task needs to be re-run
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      } else {
        //The build has started, so the job started monitoring task is completed
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([buildNumber: build.number]).build()
      }

    } catch (SpinnakerHttpException e) {
      if ([503, 500, 404].contains(e.responseCode)) {
        log.warn("Http ${e.responseCode} received from `igor`, retrying...")
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      }

      throw e
    }
  }
}
