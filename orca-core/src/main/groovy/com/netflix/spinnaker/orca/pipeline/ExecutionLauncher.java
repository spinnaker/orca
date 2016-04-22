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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.extern.slf4j.Slf4j;
import static com.google.common.collect.Lists.reverse;
import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED;
import static java.lang.String.format;

@Slf4j
public abstract class ExecutionLauncher<T extends Execution> {

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
    if (shouldQueue(execution)) {
      log.info("Queueing {}", execution.getId());
    } else {
      planStages(execution);
      runner.start(execution);
    }
    return execution;
  }

  private void planStages(T execution) {
    List<Stage<T>> stages = new ArrayList<>(execution.getStages()); // need to clone because we'll be modifying the list
    stages.stream().forEach(this::planStage);
  }

  private void planStage(Stage<T> stage) {
    StageDefinitionBuilder builder = findBuilderForStage(stage);
    builder
      .preStages()
      .forEach(preStage -> {
        List<Stage> stages = stage.getExecution().getStages();
        int index = stages.indexOf(stage);
        stages.add(index, preStage);
        planStage((Stage<T>) preStage);
      });
    reverse(
      builder
        .postStages())
      .forEach(postStage -> {
        List<Stage> stages = stage.getExecution().getStages();
        int index = stages.indexOf(stage);
        stages.add(index + 1, postStage);
        planStage((Stage<T>) postStage);
      });
    builder
      .taskGraph()
      .forEach(taskDef -> {
        DefaultTask task = new DefaultTask();
        task.setId(taskDef.getId());
        task.setName(taskDef.getName());
        task.setStatus(NOT_STARTED);
        stage.getTasks().add(task);
      });
  }

  private StageDefinitionBuilder findBuilderForStage(Stage<T> stage) {
    return stageDefinitionBuilders
      .stream()
      .filter(builder1 -> builder1.getType().equals(stage.getType()))
      .findFirst()
      .orElseThrow(() -> new NoSuchStageDefinitionBuilder(stage.getType()));
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

  public static class NoSuchStageDefinitionBuilder extends RuntimeException {
    public NoSuchStageDefinitionBuilder(String type) {
      super(format("No StageDefinitionBuilder implementation for %s found", type));
    }
  }
}
