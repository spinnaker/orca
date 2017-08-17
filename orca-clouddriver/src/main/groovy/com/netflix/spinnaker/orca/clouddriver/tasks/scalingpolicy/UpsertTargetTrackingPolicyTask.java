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

package com.netflix.spinnaker.orca.clouddriver.tasks.scalingpolicy;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class UpsertTargetTrackingPolicyTask extends AbstractCloudProviderAwareTask implements Task {

  @Autowired
  private KatoService kato;

  @Override
  public TaskResult execute(Stage stage) {
    Map context = stage.getContext();
    Map<String, Map> operation = new HashMap<>();
    operation.put("upsertTargetTrackingPolicy", context);
    TaskId taskId = kato.requestOperations(getCloudProvider(stage), Collections.singletonList(operation)).toBlocking()
      .first();

    Map<String, Object> result = new HashMap<>();
    result.put("deploy.account.name", context.get("credentials"));
    result.put("kato.last.task.id", taskId);
    Map<String, Collection<String>> serverGroups = new HashMap<>();
    serverGroups.put((String) context.get("region"), Collections.singletonList((String) context.get("serverGroupName")));
    result.put("deploy.server.groups", serverGroups);

    return new TaskResult(ExecutionStatus.SUCCEEDED, result);
  }
}
