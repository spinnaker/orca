package com.netflix.spinnaker.orca.clouddriver.tasks.job;

import com.netflix.spinnaker.kork.annotations.Alpha;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

@Component
@Alpha
public class WaitOnRemoteJobStageCompletion extends WaitOnJobCompletion {

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    return TaskResult
        .builder(stage.getStatus())
        .context(stage.getContext())
        .outputs(stage.getOutputs())
        .build();
  }
}
