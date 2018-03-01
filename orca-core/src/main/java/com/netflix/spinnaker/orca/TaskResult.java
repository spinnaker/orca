/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca;

import java.util.Map;
import javax.annotation.Nonnull;
import com.google.common.collect.ImmutableMap;
import static java.util.Collections.emptyMap;

public final class TaskResult {
  /**
   * A useful constant for a success result with no outputs.
   */
  public static final TaskResult SUCCEEDED = new TaskResult(ExecutionStatus.SUCCEEDED);

  private final ExecutionStatus status;
  private final ImmutableMap<String, ?> context;
  private final ImmutableMap<String, ?> outputs;
  private final String error;

  public TaskResult(ExecutionStatus status) {
    this(status, emptyMap(), emptyMap(), null);
  }

  public TaskResult(ExecutionStatus status, Map<String, ?> context) {
    this(status, context, emptyMap(), null);
  }

  public TaskResult(ExecutionStatus status, Map<String, ?> context, Map<String, ?> outputs) {
    this(status, context, outputs, null);
  }

  public TaskResult(ExecutionStatus status, Map<String, ?> context, Map<String, ?> outputs, String error) {
    this.status = status;
    this.context = ImmutableMap.copyOf(context);
    this.outputs = ImmutableMap.copyOf(outputs);
    this.error = error;
  }

  public @Nonnull TaskResult withContext(@Nonnull Map<String, ?> context) {
    return new TaskResult(this.status, context, this.outputs, this.error);
  }

  public @Nonnull TaskResult withOutputs(@Nonnull Map<String, ?> outputs) {
    return new TaskResult(this.status, this.context, outputs, this.error);
  }

  public @Nonnull TaskResult withError(@Nonnull String error) {
    return new TaskResult(this.status, this.context, this.outputs, error);
  }

  public @Nonnull ExecutionStatus getStatus() {
    return status;
  }

  /**
   * Updates to the current stage context.
   */
  public @Nonnull Map<String, ?> getContext() {
    return context;
  }

  /**
   * Values to be output from the stage and potentially accessed by downstream
   * stages.
   */
  public @Nonnull Map<String, ?> getOutputs() {
    return outputs;
  }
}
