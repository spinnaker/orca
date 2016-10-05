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

import java.util.Collection;
import java.util.Map;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.NoOpTask;

/**
 * Implement for stages that will create parallel branches to perform the same
 * tasks for multiple contexts. For example, a multi-region bake or deploy.
 */
public interface BranchingStageDefinitionBuilder extends StageDefinitionBuilder {

  /**
   * Produce the different contexts for each parallel branch.
   */
  <T extends Execution<T>> Collection<Map<String, Object>> parallelContexts(Stage<T> stage);

  /**
   * The task that should be executed _after_ the parallel split.
   *
   * TODO-AJ Only necessary for v1 execution support
   */
  @Deprecated
  default Task completeParallelTask() {
    return new NoOpTask();
  }

  /**
   * Define any tasks that should run _before_ the parallel split.
   */
  default <T extends Execution<T>> void preBranchGraph(Stage<T> stage, TaskNode.Builder builder) {
  }

  /**
   * Define any tasks that should run _after_ the parallel split.
   */
  default <T extends Execution<T>> void postBranchGraph(Stage<T> stage, TaskNode.Builder builder) {
  }

  /**
   * Override this to rename the stage if it has parallel flows.
   * This affects the base stage not the individual parallel synthetic stages.
   */
  default <T extends Execution<T>> String parallelStageName(Stage<T> stage, boolean hasParallelFlows) {
    return stage.getName();
  }

  /**
   * Determines the type of child stage.
   */
  default String getChildStageType(Stage childStage) {
    return childStage.getType();
  }
}

