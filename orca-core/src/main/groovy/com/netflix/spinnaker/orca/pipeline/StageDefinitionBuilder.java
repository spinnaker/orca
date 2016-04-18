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

import java.io.Serializable;
import com.netflix.spinnaker.orca.Task;
import static java.util.Collections.emptySet;

public interface StageDefinitionBuilder {

  default Iterable<TaskDefinition> taskGraph() {
    return emptySet();
  }

  default Iterable<StageDefinitionBuilder> preStages() {
    return emptySet();
  }

  default Iterable<StageDefinitionBuilder> postStages() {
    return emptySet();
  }

  /**
   * @return the stage type this builder handles.
   */
  String getType();

  class TaskDefinition implements Serializable {
    private final String id;
    private final String name;
    private final Class<? extends Task> implementingClass;

    public TaskDefinition(String id, String name, Class<? extends Task> implementingClass) {
      this.id = id;
      this.name = name;
      this.implementingClass = implementingClass;
    }

    public String getId() {
      return id;
    }

    public String getName() {
      return name;
    }

    public Class<? extends Task> getImplementingClass() {
      return implementingClass;
    }
  }
}
