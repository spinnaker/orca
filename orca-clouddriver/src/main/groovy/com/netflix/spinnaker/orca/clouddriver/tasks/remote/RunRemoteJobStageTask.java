package com.netflix.spinnaker.orca.clouddriver.tasks.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.remote.Callback;
import com.netflix.spinnaker.orca.pipeline.remote.RemoteStageService;
import com.netflix.spinnaker.orca.pipeline.remote.RunStage;
import org.slf4j.MDC;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class RunRemoteJobStageTask implements RetryableTask {

  private final RemoteStageService remoteStageService;
  private final ObjectMapper objectMapper;

  public RunRemoteJobStageTask(ObjectMapper objectMapper, RemoteStageService remoteStageService) {
    this.objectMapper = objectMapper;
    this.remoteStageService = remoteStageService;
  }

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
    Callback callback = Callback.builder()
        .uri("some-gate-url")
        .headers(Collections.singletonMap("X-SPINNAKER-EXECUTION-ID", MDC.get("X-SPINNAKER-EXECUTION-ID")))
        .build();

    RunStage runStage = RunStage.builder()
        .type(stage.getType())
        .stageExecutionId(stage.getId())
        .pipelineExecutionId(stage.getExecution().getId())
        .remoteContext((Map<String, Object>) stage.getContext().get("remote"))
        .upstreamStageOutputs(stage.getOutputs())
        .callback(callback)
        .build();

    String runStageString;
    try {
      runStageString = objectMapper.writeValueAsString(runStage);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Malformed run stage in " + stage + ": " + e.getMessage(), e);
    }

    remoteStageService.runStage(runStage);

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).build();
  }
}
