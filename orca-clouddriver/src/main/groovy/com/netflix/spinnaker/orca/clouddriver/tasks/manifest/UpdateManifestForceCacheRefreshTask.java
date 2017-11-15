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
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class UpdateManifestForceCacheRefreshTask extends AbstractCloudProviderAwareTask implements Task {
  private final static String REFRESH_TYPE = "Manifest";
  public final static String TASK_NAME = "forceCacheRefresh";

  @Autowired
  CloudDriverCacheService cacheService;

  @Override
  public TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage);
    String account = getCredentials(stage);
    String name = (String) stage.getContext().get("manifest.name");
    String location = (String) stage.getContext().get("manifest.location");

    Map<String, String> request = new ImmutableMap.Builder<String, String>()
        .put("account", account)
        .put("name", name)
        .put("location", location)
        .build();

    cacheService.forceCacheUpdate(cloudProvider, REFRESH_TYPE, request);

    return new TaskResult(ExecutionStatus.SUCCEEDED);
  }
}
