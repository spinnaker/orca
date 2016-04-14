/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED;

public abstract class ExecutionLauncher<T extends Execution> {

  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected final ObjectMapper objectMapper;
  protected final InstanceInfo currentInstance;
  private final ExecutionRunner runner;
  private final Collection<StageDefinitionBuilder> stageDefinitionBuilders;

  protected ExecutionLauncher(ObjectMapper objectMapper,
                              InstanceInfo currentInstance,
                              ExecutionRunner runner,
                              Collection<StageDefinitionBuilder> stageDefinitionBuilders) {
    this.objectMapper = objectMapper;
    this.currentInstance = currentInstance;
    this.runner = runner;
    this.stageDefinitionBuilders = stageDefinitionBuilders;
  }

  public T start(String configJson) throws IOException {
    final T execution = parse(configJson);
    planTasks(execution);
    if (shouldQueue(execution)) {
      log.info("Queueing {}", execution.getId());
    } else {
      runner.start(execution);
    }
    return execution;
  }

  private void planTasks(T execution) {
    Stream<Stage> stream = execution
      .getStages()
      .stream();
    stream
      .forEach(stage -> {
        Optional<StageDefinitionBuilder> maybeStageDefinitionBuilder = stageDefinitionBuilders
          .stream()
          .filter(builder -> builder.getType().equals(stage.getType()))
          .findFirst();
        maybeStageDefinitionBuilder
          .orElseThrow(RuntimeException::new)
          .taskGraph()
          .forEach(taskDef -> {
            DefaultTask task = new DefaultTask();
            task.setId(taskDef.getId());
            task.setName(taskDef.getName());
            task.setStatus(NOT_STARTED);
            stage.getTasks().add(task);
          });
      });
  }

  protected abstract T parse(String configJson) throws IOException;

  /**
   * Hook for subclasses to decide if this execution should be queued or start immediately.
   *
   * @return true if the stage should be queued.
   */
  protected boolean shouldQueue(T execution) {
    return false;
  }

  public interface ExecutionRunner {
    void start(Execution execution);
  }

  // TODO: remove this – just need it to make spring context valid
  @Component
  @ConditionalOnMissingBean(ExecutionRunner.class)
  public static class NoOpExecutionRunner implements ExecutionRunner {
    @Override public void start(Execution execution) {
    }
  }
}

