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

package com.netflix.spinnaker.orca.listeners;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;

public class DefaultPersister implements Persister {
  private final ExecutionRepository executionRepository;

  public DefaultPersister(ExecutionRepository executionRepository) {
    this.executionRepository = executionRepository;
  }

  @Override
  public void save(Stage stage) {
    executionRepository.storeStage(stage);
  }

  @Override
  public boolean isCanceled(String executionId) {
    return executionRepository.isCanceled(executionId);
  }

  @Override
  public void updateStatus(String executionId, ExecutionStatus executionStatus) {
    executionRepository.updateStatus(executionId, executionStatus);
  }
}
