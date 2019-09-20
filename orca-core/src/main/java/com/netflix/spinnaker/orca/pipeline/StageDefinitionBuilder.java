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

import static com.netflix.spinnaker.orca.pipeline.TaskNode.Builder;
import static com.netflix.spinnaker.orca.pipeline.TaskNode.GraphType.FULL;

import com.google.common.base.CaseFormat;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.orca.pipeline.TaskNode.TaskGraph;
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface StageDefinitionBuilder {

  default @Nonnull TaskGraph buildTaskGraph(@Nonnull Stage stage) {
    Builder graphBuilder = Builder(FULL);
    taskGraph(stage, graphBuilder);
    return graphBuilder.build();
  }

  default void taskGraph(@Nonnull Stage stage, @Nonnull Builder builder) {}

  /**
   * Implement this method to define any stages that should run before any tasks in this stage as
   * part of a composed workflow.
   */
  default void beforeStages(@Nonnull Stage parent, @Nonnull StageGraphBuilder graph) {}

  /**
   * Implement this method to define any stages that should run after any tasks in this stage as
   * part of a composed workflow.
   */
  default void afterStages(@Nonnull Stage parent, @Nonnull StageGraphBuilder graph) {}

  /**
   * Implement this method to define any stages that should run in response to a failure in tasks,
   * before or after stages.
   */
  default void onFailureStages(@Nonnull Stage stage, @Nonnull StageGraphBuilder graph) {}

  /** @return the stage type this builder handles. */
  default @Nonnull String getType() {
    return getType(this.getClass());
  }

  /** Implementations can override this if they need any special cleanup on restart. */
  default void prepareStageForRestart(@Nonnull Stage stage) {}

  static String getType(Class<? extends StageDefinitionBuilder> clazz) {
    String className = clazz.getSimpleName();
    return className.substring(0, 1).toLowerCase()
        + className
            .substring(1)
            .replaceFirst("StageDefinitionBuilder$", "")
            .replaceFirst("Stage$", "");
  }

  @Deprecated
  static @Nonnull Stage newStage(
      @Nonnull Execution execution,
      @Nonnull String type,
      @Nullable String name,
      @Nonnull Map<String, Object> context,
      @Nullable Stage parent,
      @Nullable SyntheticStageOwner stageOwner) {
    Stage stage = new Stage(execution, type, name, context);
    if (parent != null) {
      stage.setParentStageId(parent.getId());
    }
    stage.setSyntheticStageOwner(stageOwner);
    return stage;
  }

  /** Return true if the stage can be manually skipped from the API. */
  default boolean canManuallySkip() {
    return false;
  }

  default boolean isForceCacheRefreshEnabled(DynamicConfigService dynamicConfigService) {
    String className = getClass().getSimpleName();

    try {
      return dynamicConfigService.isEnabled(
          String.format(
              "stages.%s.force-cache-refresh",
              CaseFormat.LOWER_CAMEL.to(
                  CaseFormat.LOWER_HYPHEN,
                  Character.toLowerCase(className.charAt(0)) + className.substring(1))),
          true);
    } catch (Exception e) {
      return true;
    }
  }

  default Collection<String> aliases() {
    if (getClass().isAnnotationPresent(Aliases.class)) {
      return Arrays.asList(getClass().getAnnotation(Aliases.class).value());
    }

    return Collections.emptyList();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @interface Aliases {
    String[] value() default {};
  }
}
