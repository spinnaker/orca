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

package com.netflix.spinnaker.orca.batch.listeners;

import com.netflix.spinnaker.orca.batch.ExecutionListenerProvider;
import com.netflix.spinnaker.orca.listeners.ExecutionListener;
import com.netflix.spinnaker.orca.listeners.StageListener;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecutionListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class SpringBatchExecutionListenerProvider implements ExecutionListenerProvider {
  private final ExecutionRepository executionRepository;
  private final List<StageListener> stageListeners = new ArrayList<>();
  private final List<ExecutionListener> executionListeners = new ArrayList<>();

  public SpringBatchExecutionListenerProvider(ExecutionRepository executionRepository,
                                              Collection<StageListener> stageListeners,
                                              Collection<ExecutionListener> executionListeners) {
    this.executionRepository = executionRepository;
    this.stageListeners.addAll(stageListeners);
    this.executionListeners.addAll(executionListeners);
  }

  @Override
  public StepExecutionListener wrap(StageListener stageListener) {
    return new SpringBatchStageListener(executionRepository, stageListener);
  }

  @Override
  public JobExecutionListener wrap(ExecutionListener executionListener) {
    return new SpringBatchExecutionListener(executionRepository, executionListener);
  }

  @Override
  public Collection<StepExecutionListener> allStepExecutionListeners() {
    return stageListeners
      .stream()
      .sorted()
      .map(stageListener -> new SpringBatchStageListener(executionRepository, stageListener))
      .collect(Collectors.toList());
  }

  @Override
  public Collection<JobExecutionListener> allJobExecutionListeners() {
    return executionListeners
      .stream()
      .sorted()
      .map(executionListener -> new SpringBatchExecutionListener(executionRepository, executionListener))
      .collect(Collectors.toList());
  }
}
