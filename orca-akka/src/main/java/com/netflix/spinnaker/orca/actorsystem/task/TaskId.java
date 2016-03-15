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

package com.netflix.spinnaker.orca.actorsystem.task;

import java.io.Serializable;
import java.util.Objects;
import com.netflix.spinnaker.orca.actorsystem.stage.StageId;
import static java.lang.String.format;

public class TaskId implements Serializable {

  public final String executionId;
  public final String stageId;
  public final String taskId;

  public TaskId(String executionId, String stageId, String taskId) {
    this.executionId = executionId;
    this.stageId = stageId;
    this.taskId = taskId;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TaskId other = (TaskId) o;
    return Objects.equals(executionId, other.executionId) &&
      Objects.equals(stageId, other.stageId) &&
      Objects.equals(taskId, other.taskId);
  }

  @Override public int hashCode() {
    return Objects.hash(executionId, stageId, taskId);
  }

  @Override
  public String toString() {
    return format("%s:%s:%s", executionId, stageId, taskId);
  }

  public StageId stage() {
    return new StageId(executionId, stageId);
  }
}
