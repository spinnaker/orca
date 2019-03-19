/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.igor.tasks;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.igor.model.CIStageDefinition;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit.RetrofitError;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Slf4j
public abstract class RetryableIgorTask implements RetryableTask {
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(5);
  }

  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(1);
  }

  protected int getMaxConsecutiveErrors() {
    return 5;
  }

  @Override
  public @Nonnull TaskResult execute(@Nonnull Stage stage) {
    CIStageDefinition stageDefinition = stage.mapTo(CIStageDefinition.class);
    int errors = stageDefinition.getConsecutiveErrors();
    try {
      TaskResult result = tryExecute(stageDefinition);
      return resetErrorCount(result);
    } catch (RetrofitError e) {
      int status = e.getResponse().getStatus();
      if (stageDefinition.getConsecutiveErrors() < getMaxConsecutiveErrors() && isRetryable(status)) {
        log.warn(String.format("Received HTTP %s response from igor, retrying...", status));
        return new TaskResult(ExecutionStatus.RUNNING, errorContext(errors + 1));
      }
      throw e;
    }
  }

  abstract protected @Nonnull TaskResult tryExecute(@Nonnull CIStageDefinition stageDefinition);

  private TaskResult resetErrorCount(TaskResult result) {
    Map<String, Object> newContext = ImmutableMap.<String, Object>builder()
      .putAll(result.getContext())
      .put("consecutiveErrors", 0)
      .build();
    return new TaskResult(result.getStatus(), newContext, result.getOutputs());
  }

  private Map<String, Integer> errorContext(int errors) {
    return Collections.singletonMap("consecutiveErrors", errors);
  }

  private boolean isRetryable(int statusCode) {
    return statusCode == 500 || statusCode == 503;
  }
}
