/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.listeners;

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.TaskExecution;

public interface StageListener {
  default void beforeTask(Persister persister, StageExecution stage, TaskExecution task) {
    // do nothing
  }

  default void beforeStage(Persister persister, StageExecution stage) {
    // do nothing
  }

  default void afterTask(
      Persister persister,
      StageExecution stage,
      TaskExecution task,
      ExecutionStatus executionStatus,
      boolean wasSuccessful) {
    // do nothing
  }

  default void afterStage(Persister persister, StageExecution stage) {
    // do nothing
  }
}
