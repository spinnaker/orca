/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.events;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.TaskExecution;
import javax.annotation.Nonnull;
import lombok.Getter;

public class TaskStarted extends ExecutionEvent {
  @Getter private final StageExecution stage;

  @Getter private final TaskExecution task;

  public TaskStarted(
      @Nonnull Object source, @Nonnull StageExecution stage, @Nonnull TaskExecution task) {
    super(source, stage.getExecution().getType(), stage.getExecution().getId());

    this.stage = stage;
    this.task = task;
  }
}
