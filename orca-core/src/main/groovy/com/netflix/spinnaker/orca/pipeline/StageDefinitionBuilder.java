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

import java.util.HashMap;
import java.util.List;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.Task;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import lombok.Value;
import static java.util.Collections.emptyList;

public interface StageDefinitionBuilder {

  default List<TaskDefinition> taskGraph() {
    return emptyList();
  }

  default <T extends Execution> List<Stage<T>> preStages() {
    return emptyList();
  }

  default <T extends Execution> List<Stage<T>> postStages() {
    return emptyList();
  }

  /**
   * @return the stage type this builder handles.
   */
  default String getType() {
    String className = getClass().getSimpleName();
    return className.substring(0, 1).toLowerCase() + className.substring(1).replaceFirst("StageDefinitionBuilder$", "").replaceFirst("Stage$", "");
  }

  default Stage prepareStageForRestart(Stage stage) {
    return StageDefinitionBuilderSupport.prepareStageForRestart(stage);
  }

  @Value
  class TaskDefinition {
    String id;
    String name;
    Class<? extends com.netflix.spinnaker.orca.Task> implementingClass;
  }

  class StageDefinitionBuilderSupport {
    /**
     * Prepares a stage for restarting by:
     * - marking the halted task as NOT_STARTED and resetting its start and end times
     * - marking the stage as RUNNING
     */
    public static Stage prepareStageForRestart(Stage stage) {
      stage.getExecution().setCanceled(false);

      List<Stage> stages = stage.getExecution().getStages();
      stages
        .stream()
        .filter(s -> s.getStatus() == ExecutionStatus.CANCELED)
        .forEach(s -> {
            s.setStatus(ExecutionStatus.NOT_STARTED);
            List<Task> tasks = s.getTasks();
            tasks
              .stream()
              .filter(t -> t.getStatus() == ExecutionStatus.CANCELED)
              .forEach(t -> {
                t.setStartTime(null);
                t.setEndTime(null);
                t.setStatus(ExecutionStatus.NOT_STARTED);
              });
          }
        );

      List<Task> tasks = stage.getTasks();
      tasks
        .stream()
        .filter(t -> t.getStatus().isHalt())
        .forEach(t -> {
          t.setStartTime(null);
          t.setEndTime(null);
          t.setStatus(ExecutionStatus.NOT_STARTED);
        });

      stage.getContext().put("restartDetails", new HashMap() {{
        put("restartedBy", AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));
        put("restartTime", System.currentTimeMillis());
        if (stage.getContext().containsKey("exception")) {
          put("previousException", stage.getContext().get("exception"));
          stage.getContext().remove("exception");
        }
      }});

      stage.setStatus(ExecutionStatus.RUNNING);

      return stage;
    }
  }
}
