/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.clouddriver.tasks.lambdas;

import com.netflix.spinnaker.orca.DefaultTaskResult;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

public class InvokeLambdaTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  private static final String CLOUD_OPERATION_TYPE = "invokeLambda";
  @Autowired
  private KatoService kato;

  @Override
  public TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage);
    String account = getCredentials(stage);

    Map context = stage.getContext();

    Collection<Map<String, Map>> operations = new ArrayList<>();
    Map<String, Map> op = new HashMap<String, Map>() {
      {
        put(CLOUD_OPERATION_TYPE, context);
      }
    };

    operations.add(op);

    TaskId taskId = kato.requestOperations(cloudProvider, operations)
      .toBlocking()
      .first();

    Map target = new HashMap() {
      {
        put("credentials", account);
        put("region", context.get("region"));
        put("function", context.get("function"));
        put("payload", context.get("payload"));
      }
    };

    Map outputs = new HashMap() {
      {
        put("notification.type", CLOUD_OPERATION_TYPE.toLowerCase());
        put("kato.result.expected", true);
        put("kato.last.task.id", taskId);
        put("targets", Collections.singletonList(target));
      }
    };

    return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, outputs);
  }

  @Override
  public long getBackoffPeriod() {
    return 2000;
  }

  @Override
  public long getTimeout() {
    return 60000;
  }
}
