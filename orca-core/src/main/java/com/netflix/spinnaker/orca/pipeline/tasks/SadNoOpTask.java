package com.netflix.spinnaker.orca.pipeline.tasks;

import com.netflix.spinnaker.orca.api.pipeline.SkippableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class SadNoOpTask implements SkippableTask {
  @NotNull
  @Override
  public TaskResult execute(@NotNull StageExecution stage) {
    return TaskResult.builder(ExecutionStatus.TERMINAL).build();
  }
}
