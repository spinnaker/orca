/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda;

import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.LambdaUtils;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaDefinition;
import java.time.Duration;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LambdaWaitToStabilizeTask implements LambdaStageBaseTask {

  final String PENDING_STATE = "Pending";
  final String ACTIVE_STATE = "Active";
  final String FUNCTION_CREATING = "Creating";

  private final LambdaUtils utils;

  public LambdaWaitToStabilizeTask(LambdaUtils utils) {
    this.utils = utils;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    log.debug("Executing LambdaWaitToStabilizeTask...");
    return waitForStableState(stage);
  }

  private TaskResult waitForStableState(@Nonnull StageExecution stage) {
    LambdaDefinition lf;
    int counter = 0;
    while (true) {
      lf = utils.retrieveLambdaFromCache(stage);
      if (lf != null && lf.getState() != null) {
        log.info(
            String.format(
                "%s lambda state from the cache %s", lf.getFunctionName(), lf.getState()));
        if (lf.getState().equals(PENDING_STATE)
            && lf.getStateReasonCode() != null
            && lf.getStateReasonCode().equals(FUNCTION_CREATING)) {
          utils.await(Duration.ofSeconds(30).toMillis());
          continue;
        }
        if (lf.getState().equals(ACTIVE_STATE)) {
          log.info(lf.getFunctionName() + " is active");
          return taskComplete(stage);
        }
      } else {
        log.info(
            "waiting for up to 10 minutes for it to show up in the cache... requires a full cache refresh cycle");
        utils.await(Duration.ofMinutes(1).toMillis());
        if (++counter > 10) break;
      }
    }
    return this.formErrorTaskResult(
        stage,
        String.format(
            "Failed to stabilize function with state: %s and reason: %s",
            lf != null && lf.getState() != null ? lf.getState() : "Unknown",
            lf != null && lf.getStateReason() != null ? lf.getStateReason() : "Unknown reason"));
  }
}
