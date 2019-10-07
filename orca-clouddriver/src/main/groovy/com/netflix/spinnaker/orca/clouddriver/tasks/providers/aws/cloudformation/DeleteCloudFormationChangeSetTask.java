/*
 * Copyright (c) 2019 Adevinta
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
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.cloudformation;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.*;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DeleteCloudFormationChangeSetTask extends AbstractCloudProviderAwareTask
    implements Task {

  @Autowired KatoService katoService;

  public static final String TASK_NAME = "deleteCloudFormationChangeSet";

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    String cloudProvider = getCloudProvider(stage);

    Map<String, Object> task = new HashMap<>(stage.getContext());

    String stackName = (String) task.get("stackName");
    String changeSetName = (String) task.get("changeSetName");

    if ((boolean) Optional.ofNullable(stage.getContext().get("deleteChangeSet")).orElse(false)) {
      log.debug(
          "Deleting CloudFormation changeset {} from stack {} as requested.",
          changeSetName,
          stackName);
      List<String> regions = (List<String>) task.get("regions");
      String region =
          regions.stream()
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "No regions selected. At least one region must be chosen."));
      task.put("region", region);

      Map<String, Map> operation =
          new ImmutableMap.Builder<String, Map>().put(TASK_NAME, task).build();

      katoService
          .requestOperations(cloudProvider, Collections.singletonList(operation))
          .toBlocking()
          .first();
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).build();
    } else {
      log.debug(
          "Not deleting CloudFormation ChangeSet {} from stack {}.", changeSetName, stackName);
      return TaskResult.builder(ExecutionStatus.SKIPPED).build();
    }
  }
}
