/*
 *
 *  * Copyright 2021 Amazon.com, Inc. or its affiliates.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License")
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package com.amazon.spinnaker.orca.aws.lambda.updatecode;

import com.amazon.spinnaker.orca.aws.lambda.CloudDriverProperties;
import com.amazon.spinnaker.orca.aws.lambda.LambdaStageBaseTask;
import com.amazon.spinnaker.orca.aws.lambda.model.LambdaDefinition;
import com.amazon.spinnaker.orca.aws.lambda.utils.LambdaCloudDriverUtils;
import com.amazon.spinnaker.orca.aws.lambda.utils.LambdaStageConstants;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.util.Map;
import javax.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaWaitForCacheCodeUpdateTask implements LambdaStageBaseTask {
  private static Logger logger = LoggerFactory.getLogger(LambdaWaitForCacheCodeUpdateTask.class);

  @Autowired CloudDriverProperties props;

  @Autowired private LambdaCloudDriverUtils utils;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    logger.debug("Executing LambdaWaitForCacheCodeUpdateTask...");
    return waitForCacheUpdate(stage);
  }

  private TaskResult waitForCacheUpdate(@NotNull StageExecution stage) {
    Boolean requiresPublishFlag =
        (Boolean) stage.getContext().getOrDefault("publish", Boolean.FALSE);
    if (requiresPublishFlag
        && stage.getContext().containsKey(LambdaStageConstants.updateCodeUrlKey)) {
      String codeUpdateUrl = (String) stage.getContext().get(LambdaStageConstants.updateCodeUrlKey);
      String version = utils.getPublishedVersion(codeUpdateUrl);
      for (int i = 0; i < 10; i++) {
        LambdaDefinition lf = utils.findLambda(stage);
        if (lf != null) {
          Map<String, String> revisions = lf.getRevisions();
          if (revisions.containsValue(version)) {
            return taskComplete(stage);
          }
        }
        utils.await(10000);
      }
      return this.formErrorTaskResult(stage, "Failed to update cache after CodeUpdateTask");
    }
    return taskComplete(stage);
  }
}
