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

import com.netflix.spinnaker.orca.pipeline.TaskNode.TaskGraph;
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.orca.pipeline.TaskNode.Builder;
import static com.netflix.spinnaker.orca.pipeline.TaskNode.GraphType.FULL;
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER;
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

public interface StageDefinitionBuilder {

  default @Nonnull TaskGraph buildTaskGraph(@Nonnull Stage stage) {
    Builder graphBuilder = Builder(FULL);
    taskGraph(stage, graphBuilder);
    return graphBuilder.build();
  }

  default void taskGraph(
    @Nonnull Stage stage, @Nonnull Builder builder) {
  }

  @Deprecated
  default @Nonnull List<Stage> aroundStages(@Nonnull Stage stage) {
    return emptyList();
  }

  @Deprecated
  default @Nonnull List<Stage> parallelStages(
    @Nonnull Stage stage) {
    return emptyList();
  }

  default void beforeStages(
    @Nonnull Stage parent,
    @Nonnull StageGraphBuilder builder
  ) {
    List<Stage> stages = aroundStages(parent)
      .stream()
      .filter((it) -> it.getSyntheticStageOwner() == STAGE_BEFORE)
      .collect(toList());
    if (!stages.isEmpty()) {
      builder.add(stages.get(0));
    }
    for (int i = 1; i < stages.size(); i++) {
      builder.connect(stages.get(i - 1), stages.get(i));
    }
    parallelStages(parent)
      .stream()
      .filter((it) -> it.getSyntheticStageOwner() == STAGE_BEFORE)
      .forEach(builder::add);
  }

  default void afterStages(
    @Nonnull Stage parent,
    @Nonnull StageGraphBuilder builder
  ) {
    List<Stage> stages = aroundStages(parent)
      .stream()
      .filter((it) -> it.getSyntheticStageOwner() == STAGE_AFTER)
      .collect(toList());
    if (!stages.isEmpty()) {
      builder.add(stages.get(0));
    }
    for (int i = 1; i < stages.size(); i++) {
      builder.connect(stages.get(i - 1), stages.get(i));
    }
    parallelStages(parent)
      .stream()
      .filter((it) -> it.getSyntheticStageOwner() == STAGE_AFTER)
      .forEach(builder::add);
  }

  default void onFailureStages(
    @Nonnull Stage stage,
    @Nonnull StageGraphBuilder builder
  ) {}

  /**
   * @return the stage type this builder handles.
   */
  default @Nonnull String getType() {
    return getType(this.getClass());
  }

  /**
   * Implementations can override this if they need any special cleanup on
   * restart.
   */
  default void prepareStageForRestart(@Nonnull Stage stage) { }

  static String getType(Class<? extends StageDefinitionBuilder> clazz) {
    String className = clazz.getSimpleName();
    return className.substring(0, 1).toLowerCase() + className.substring(1).replaceFirst("StageDefinitionBuilder$", "").replaceFirst("Stage$", "");
  }

  @Deprecated
  static @Nonnull Stage newStage(
    @Nonnull Execution execution,
    @Nonnull String type,
    @Nullable String name,
    @Nonnull Map<String, Object> context,
    @Nullable Stage parent,
    @Nullable SyntheticStageOwner stageOwner
  ) {
    Stage stage = new Stage(execution, type, name, context);
    if (parent != null) {
      stage.setParentStageId(parent.getId());
    }
    stage.setSyntheticStageOwner(stageOwner);
    return stage;
  }
}
