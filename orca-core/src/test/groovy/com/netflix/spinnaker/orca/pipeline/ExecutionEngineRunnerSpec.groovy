package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.orca.api.pipeline.ExecutionRunner
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionEngineVersion
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import org.jetbrains.annotations.Nullable
import spock.lang.Specification
import spock.lang.Unroll

import javax.annotation.Nonnull

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionEngine.v2
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionEngine.v3
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionEngine.v4

class ExecutionEngineRunnerSpec extends Specification {

  @Unroll
  def "Finds the correct execution runner based on the execution engine"() {
    given:
    ExecutionEngineRunner executionEngineRunner = new ExecutionEngineRunner([new V3EngineRunner(), new V4EngineRunner()])

    when:
    def runner = executionEngineRunner.executionRunner(supplied)

    then:
    runner.class == expected

    where:
    supplied | expected
    v2       | V3EngineRunner.class //v2 is obsolete, use v3 engine
    v3       | V3EngineRunner.class
    v4       | V4EngineRunner.class
  }

  @Unroll
  def "Throws UnsupportedOperationException when execution engine runner can not be found"() {
    given:
    ExecutionEngineRunner executionEngineRunner = new ExecutionEngineRunner([new UnsupportedRunner()])

    when:
    executionEngineRunner.executionRunner(v3)

    then:
    thrown(UnsupportedOperationException)
  }
}

@ExecutionEngineVersion(v3)
class V3EngineRunner implements ExecutionRunner {
  @Override
  void start(@Nonnull PipelineExecution execution) {}
  @Override
  void restart(@Nonnull PipelineExecution execution, @Nonnull String stageId) {}
  @Override
  void reschedule(@Nonnull PipelineExecution execution) {}
  @Override
  void unpause(@Nonnull PipelineExecution execution) {}
  @Override
  void cancel(@Nonnull PipelineExecution execution, @Nonnull String user, @Nullable String reason) {}
}

@ExecutionEngineVersion(v4)
class V4EngineRunner implements ExecutionRunner {
  @Override
  void start(@Nonnull PipelineExecution execution) {}
  @Override
  void restart(@Nonnull PipelineExecution execution, @Nonnull String stageId) {}
  @Override
  void reschedule(@Nonnull PipelineExecution execution) {}
  @Override
  void unpause(@Nonnull PipelineExecution execution) {}
  @Override
  void cancel(@Nonnull PipelineExecution execution, @Nonnull String user, @Nullable String reason) {}
}

class UnsupportedRunner implements ExecutionRunner {
  @Override
  void start(@Nonnull PipelineExecution execution) {}
  @Override
  void restart(@Nonnull PipelineExecution execution, @Nonnull String stageId) {}
  @Override
  void reschedule(@Nonnull PipelineExecution execution) {}
  @Override
  void unpause(@Nonnull PipelineExecution execution) {}
  @Override
  void cancel(@Nonnull PipelineExecution execution, @Nonnull String user, @Nullable String reason) {}
}
