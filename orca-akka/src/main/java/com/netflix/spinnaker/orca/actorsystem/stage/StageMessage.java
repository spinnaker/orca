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

package com.netflix.spinnaker.orca.actorsystem.stage;

import com.netflix.spinnaker.orca.ExecutionStatus;
import static com.netflix.spinnaker.orca.ExecutionStatus.CANCELED;

public abstract class StageMessage {

  private final StageId id;

  protected StageMessage(StageId id) {
    this.id = id;
  }

  public StageId id() {
    return id;
  }

  public static abstract class TaskStatusUpdate extends StageMessage {

    private final ExecutionStatus status;

    public TaskStatusUpdate(StageId id, ExecutionStatus status) {
      super(id);
      this.status = status;
    }

    public ExecutionStatus status() {
      return status;
    }
  }

  /**
   * Task has completed execution either successfully or not.
   */
  public static class TaskComplete extends TaskStatusUpdate {
    public TaskComplete(StageId id, ExecutionStatus status) {
      super(id, status);
    }
  }

  /**
   * Task is not yet complete.
   */
  public static class TaskIncomplete extends TaskStatusUpdate {
    public TaskIncomplete(StageId id, ExecutionStatus status) {
      super(id, status);
    }
  }

  /**
   * Task has been cancelled because the execution has been cancelled.
   */
  public static class TaskCancelled extends TaskStatusUpdate {
    public TaskCancelled(StageId id) {
      super(id, CANCELED);
    }
  }

  /**
   * Task is being skipped because it already had a completed status.
   */
  public static class TaskSkipped extends TaskStatusUpdate {
    public TaskSkipped(StageId id, ExecutionStatus status) {
      super(id, status);
    }
  }
}
