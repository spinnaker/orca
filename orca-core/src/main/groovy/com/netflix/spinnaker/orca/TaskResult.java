package com.netflix.spinnaker.orca;

import java.util.Map;
import com.google.common.collect.ImmutableMap;
import lombok.Value;
import static java.util.Collections.emptyMap;

@Value
public final class TaskResult {
  /**
   * A useful constant for a success result with no outputs.
   */
  public static final TaskResult SUCCEEDED = new TaskResult(ExecutionStatus.SUCCEEDED);

  ExecutionStatus status;
  ImmutableMap<String, ?> stageOutputs;

  public TaskResult(ExecutionStatus status) {
    this(status, emptyMap());
  }

  public TaskResult(ExecutionStatus status, Map<String, ?> stageOutputs) {
    this.status = status;
    this.stageOutputs = ImmutableMap.copyOf(stageOutputs);
  }

  @Deprecated
  public ImmutableMap<String, ?> getOutputs() {
    return stageOutputs;
  }
}
