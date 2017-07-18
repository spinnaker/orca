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

import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner;
import static com.netflix.spinnaker.orca.pipeline.TaskNode.Builder;
import static com.netflix.spinnaker.orca.pipeline.TaskNode.GraphType.FULL;
import static java.util.Collections.emptyList;

public interface StageDefinitionBuilder {

  default @Nonnull TaskNode.TaskGraph buildTaskGraph(@Nonnull Stage<?> stage) {
    Builder graphBuilder = Builder(FULL);
    taskGraph(stage, graphBuilder);
    return graphBuilder.build();
  }

  default <T extends Execution<T>> void taskGraph(
    @Nonnull Stage<T> stage, @Nonnull Builder builder) {
  }

  default @Nonnull <T extends Execution<T>> List<Stage<T>> aroundStages(
    @Nonnull Stage<T> stage) {
    return emptyList();
  }

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

  static @Nonnull <E extends Execution<E>> Stage<E> newStage(
    @Nonnull E execution,
    @Nonnull String type,
    @Nullable String name,
    @Nonnull Map<String, Object> context,
    @Nullable Stage<E> parent,
    @Nullable SyntheticStageOwner stageOwner
  ) {
    Stage<E> stage = new Stage<>(execution, type, name, context);
    if (parent != null) {
      stage.setParentStageId(parent.getId());
    }
    stage.setSyntheticStageOwner(stageOwner);
    return stage;
  }
}
