package com.netflix.spinnaker.orca.pipeline;

import com.netflix.spinnaker.kork.annotations.VisibleForTesting;
import com.netflix.spinnaker.orca.api.pipeline.ExecutionRunner;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionEngine;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionEngineVersion;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import java.util.List;
import javax.annotation.Nonnull;
import org.jetbrains.annotations.Nullable;

/**
 * Executes the {@link PipelineExecution} based on the specified {@link ExecutionEngine}. The {@link
 * ExecutionEngine} is expected to be specified via the {@link ExecutionEngineVersion} annotation.
 */
public class ExecutionEngineRunner implements ExecutionRunner {

  private final List<ExecutionRunner> executionRunners;

  public ExecutionEngineRunner(List<ExecutionRunner> executionRunners) {
    this.executionRunners = executionRunners;
  }

  @Override
  public void start(@Nonnull PipelineExecution execution) {
    executionRunner(execution.getExecutionEngine()).start(execution);
  }

  @Override
  public void restart(@Nonnull PipelineExecution execution, @Nonnull String stageId) {
    executionRunner(execution.getExecutionEngine()).restart(execution, stageId);
  }

  @Override
  public void reschedule(@Nonnull PipelineExecution execution) {
    executionRunner(execution.getExecutionEngine()).reschedule(execution);
  }

  @Override
  public void unpause(@Nonnull PipelineExecution execution) {
    executionRunner(execution.getExecutionEngine()).unpause(execution);
  }

  @Override
  public void cancel(
      @Nonnull PipelineExecution execution, @Nonnull String user, @Nullable String reason) {
    executionRunner(execution.getExecutionEngine()).cancel(execution, user, reason);
  }

  @VisibleForTesting
  protected ExecutionRunner executionRunner(ExecutionEngine executionEngine) {
    return executionRunners.stream()
        .filter(it -> it.getClass().isAnnotationPresent(ExecutionEngineVersion.class))
        .filter(
            it ->
                it.getClass().getAnnotation(ExecutionEngineVersion.class).value()
                    == executionEngine)
        .findFirst()
        .orElseGet(
            () ->
                executionRunners.stream()
                    .filter(it -> it.getClass().isAnnotationPresent(ExecutionEngineVersion.class))
                    .filter(
                        it ->
                            it.getClass().getAnnotation(ExecutionEngineVersion.class).value()
                                == ExecutionEngine.DEFAULT)
                    .findFirst()
                    .orElseThrow(
                        () ->
                            new UnsupportedOperationException(
                                "No execution engine runner found!")));
  }
}
