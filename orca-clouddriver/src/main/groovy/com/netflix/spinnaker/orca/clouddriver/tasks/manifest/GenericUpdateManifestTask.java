/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public abstract class GenericUpdateManifestTask extends AbstractCloudProviderAwareTask
    implements Task {
  @Autowired KatoService kato;

  protected abstract String taskName();

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    String credentials = getCredentials(stage);
    String cloudProvider = getCloudProvider(stage);
    Map<String, Map> operation =
        new ImmutableMap.Builder<String, Map>().put(taskName(), stage.getContext()).build();

    TaskId taskId = kato.requestOperations(cloudProvider, Collections.singletonList(operation));

    Map<String, Object> outputs =
        new ImmutableMap.Builder<String, Object>()
            .put("kato.result.expected", false)
            .put("kato.last.task.id", taskId)
            .put("manifest.account.name", credentials)
            .put("manifest.name", stage.getContext().get("manifestName"))
            .put("manifest.location", stage.getContext().get("location"))
            .put(
                "outputs.manifestNamesByNamespace",
                new ImmutableMap.Builder<String, Object>()
                    .put(
                        (String) stage.getContext().getOrDefault("location", "_"),
                        Collections.singletonList(stage.getContext().get("manifestName")))
                    .build())
            .build();

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }
}
