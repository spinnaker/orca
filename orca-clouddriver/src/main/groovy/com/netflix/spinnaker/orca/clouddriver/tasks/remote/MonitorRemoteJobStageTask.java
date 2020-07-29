package com.netflix.spinnaker.orca.clouddriver.tasks.remote;

import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;

import javax.annotation.Nonnull;

public class MonitorRemoteJobStageTask implements RetryableTask {
  @Override
  public long getBackoffPeriod() {
    return 0;
  }

  @Override
  public long getTimeout() {
    return 0;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
  }
}
