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
import com.netflix.spinnaker.orca.Task;

public abstract class TaskMessage implements Serializable {

  private final TaskId id;

  public TaskMessage(TaskId id) {
    this.id = id;
  }

  public final TaskId id() {
    return id;
  }

  public static class RequestTask extends TaskMessage {
    private final Class<? extends Task> type;

    public RequestTask(Class<? extends Task> type, TaskId identifier) {
      super(identifier);
      this.type = type;
    }

    public RequestTask(Class<? extends Task> type, String executionId, String stageId, String identifier) {
      this(type, new TaskId(executionId, stageId, identifier));
    }

    public Class<? extends Task> type() {
      return type;
    }
  }

  public static class ExecuteTask extends TaskMessage {
    public ExecuteTask(TaskId identifier) {
      super(identifier);
    }

    public ExecuteTask(String executionId, String stageId, String identifier) {
      this(new TaskId(executionId, stageId, identifier));
    }
  }

  public static class ResumeTask extends TaskMessage {
    public ResumeTask(
      TaskId identifier) {
      super(identifier);
    }

    public ResumeTask(String executionId, String stageId, String identifier) {
      this(new TaskId(executionId, stageId, identifier));
    }
  }

  public static class GetTaskStatus extends TaskMessage {
    public GetTaskStatus(TaskId id) {
      super(id);
    }

    public GetTaskStatus(String executionId, String stageId, String identifier) {
      this(new TaskId(executionId, stageId, identifier));
    }
  }
}
