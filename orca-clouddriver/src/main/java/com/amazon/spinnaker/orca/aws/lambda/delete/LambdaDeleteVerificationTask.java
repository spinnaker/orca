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

package com.amazon.spinnaker.orca.aws.lambda.delete;

import com.amazon.spinnaker.orca.aws.lambda.CloudDriverProperties;
import com.amazon.spinnaker.orca.aws.lambda.LambdaStageBaseTask;
import com.amazon.spinnaker.orca.aws.lambda.utils.LambdaCloudDriverUtils;
import com.amazon.spinnaker.orca.aws.lambda.verify.model.LambdaCloudDriverTaskResults;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaDeleteVerificationTask implements LambdaStageBaseTask {

  private static final Logger logger = LoggerFactory.getLogger(LambdaDeleteVerificationTask.class);

  @Autowired CloudDriverProperties props;

  @Autowired private LambdaCloudDriverUtils utils;

  @NotNull
  @Override
  public TaskResult execute(@NotNull StageExecution stage) {
    prepareTask(stage);
    Map<String, Object> stageContext = stage.getContext();
    String url = (String) stageContext.get("url");
    if (url != null) {
      LambdaCloudDriverTaskResults op = utils.verifyStatus(url);
      if (!op.getStatus().isCompleted()) {
        return TaskResult.builder(ExecutionStatus.RUNNING).build();
      }
      if (op.getStatus().isFailed()) {
        List<String> allMessages = Arrays.asList(op.getErrors().getMessage());
        return formErrorListTaskResult(stage, allMessages);
      }
      copyContextToOutput(stage);
    }
    addToOutput(stage, "deleteTask", "done");
    return taskComplete(stage);
  }
}
