/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */
package com.netflix.spinnaker.orca.api;

import com.netflix.spinnaker.orca.api.annotations.Immutable;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.Singular;

@Data
@Builder
public final class TaskResult {
  /** A useful constant for a success result with no outputs. */
  public static final TaskResult SUCCEEDED = TaskResult.ofStatus(ExecutionStatus.SUCCEEDED);

  public static final TaskResult RUNNING = TaskResult.ofStatus(ExecutionStatus.RUNNING);

  @NonNull private final ExecutionStatus status;

  /**
   * Stage-scoped data.
   *
   * <p>Data stored in the context will be available to other tasks within this stage, but not to
   * tasks in other stages.
   */
  @Immutable
  @Singular("context")
  private final Map<String, ?> context;

  /**
   * Pipeline-scoped data.
   *
   * <p>Data stored in outputs will be available (via {@link StageExecution#getContext()} to tasks
   * in later stages of the pipeline.
   */
  @Immutable
  @Singular("output")
  private final Map<String, ?> outputs;

  public static TaskResult ofStatus(ExecutionStatus status) {
    return TaskResult.builder(status).build();
  }

  public static TaskResultBuilder builder(ExecutionStatus status) {
    return new TaskResultBuilder().status(status);
  }
}
