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
 */
package com.netflix.spinnaker.orca.api.pipeline.events;

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.TaskExecution;

/** Emitted when a {@link TaskExecution} has started. */
@NonnullByDefault
public interface TaskStarted extends ExecutionEvent {

  /** @return the {@link StageExecution#getId()} the task belongs to */
  String getStageId();

  /** @return the {@link StageExecution#getType()} of the stage */
  String getStageType();

  /** @return the configured {@link StageExecution#getName()} */
  String getStageName();

  /** @return the {@link TaskExecution#getId()} */
  String getTaskId();

  /** @return the {@link TaskExecution#getImplementingClass()} */
  String getTaskType();

  /** @return the {@link TaskExecution#getName()} */
  String getTaskName();
}
