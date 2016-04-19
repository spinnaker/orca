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

import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.Value;
import static java.util.Collections.emptySet;

public interface StageDefinitionBuilder {

  default Iterable<TaskDefinition> taskGraph() {
    return emptySet();
  }

  default <T extends Execution> Iterable<Stage<T>> preStages() {
    return emptySet();
  }

  default <T extends Execution> Iterable<Stage<T>> postStages() {
    return emptySet();
  }

  /**
   * @return the stage type this builder handles.
   */
  default String getType() {
    String className = getClass().getSimpleName();
    return className.substring(0, 1).toLowerCase() + className.substring(1).replaceFirst("StageDefinitionBuilder$", "");
  }

  @Value
  class TaskDefinition {
    String id;
    String name;
    Class<? extends Task> implementingClass;
  }
}
