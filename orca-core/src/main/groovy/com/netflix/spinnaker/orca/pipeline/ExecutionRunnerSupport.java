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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static com.google.common.collect.Lists.reverse;
import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED;

// TODO: this should probably become an abstract base class once some implementations exist
@Component
public class ExecutionRunnerSupport implements ExecutionRunner {
  private final Collection<StageDefinitionBuilder> stageDefinitionBuilders;

  @Autowired
  public ExecutionRunnerSupport(Collection<StageDefinitionBuilder> stageDefinitionBuilders) {
    this.stageDefinitionBuilders = stageDefinitionBuilders;
  }

  @Override public <T extends Execution> void start(T execution) throws Exception {
    List<Stage<T>> stages = new ArrayList<>(execution.getStages()); // need to clone because we'll be modifying the list
    stages.stream().forEach(this::planStage);
  }

  protected <T extends Execution> void planStage(Stage<T> stage) {
    StageDefinitionBuilder builder = findBuilderForStage(stage);
    builder
      .preStages()
      .forEach(preStage -> {
        List<Stage> stages = stage.getExecution().getStages();
        int index = stages.indexOf(stage);
        stages.add(index, preStage);
        planStage(preStage);
      });
    reverse(
      builder
        .postStages())
      .forEach(postStage -> {
        List<Stage> stages = stage.getExecution().getStages();
        int index = stages.indexOf(stage);
        stages.add(index + 1, postStage);
        planStage(postStage);
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

  private <T extends Execution> StageDefinitionBuilder findBuilderForStage(Stage<T> stage) {
    return stageDefinitionBuilders
      .stream()
      .filter(builder1 -> builder1.getType().equals(stage.getType()))
      .findFirst()
      .orElseThrow(() -> new NoSuchStageDefinitionBuilder(stage.getType()));
  }
}
