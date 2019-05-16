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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup;

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.*;
import com.netflix.spinnaker.orca.kato.pipeline.Nameable;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BulkDestroyServerGroupStage implements StageDefinitionBuilder, Nameable {
  private static final String PIPELINE_CONFIG_TYPE = "bulkDestroyServerGroup";

  private final DynamicConfigService dynamicConfigService;

  @Autowired
  public BulkDestroyServerGroupStage(DynamicConfigService dynamicConfigService) {
    this.dynamicConfigService = dynamicConfigService;
  }

  @Override
  public void taskGraph(Stage stage, TaskNode.Builder builder) {
    // TODO(cfieber): how to do locking here...
    // inject an acquire lock stage per distinct cluster in the operation?
    // break into several parallel bulk ops based on cluster and lock/unlock around those?
    // question: do traffic guard checks actually even work in the bulk disable/destroy tasks?

    builder
        .withTask("bulkDisableServerGroup", BulkDisableServerGroupTask.class)
        .withTask("monitorServerGroups", MonitorKatoTask.class)
        .withTask("waitForNotUpInstances", WaitForAllInstancesNotUpTask.class);

    if (isForceCacheRefreshEnabled(dynamicConfigService)) {
      builder.withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask.class);
    }

    builder
        .withTask("bulkDestroyServerGroup", BulkDestroyServerGroupTask.class)
        .withTask("monitorServerGroups", MonitorKatoTask.class)
        .withTask("waitForDestroyedServerGroup", BulkWaitForDestroyedServerGroupTask.class);
  }

  @Override
  public String getName() {
    return this.getType();
  }
}
