/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.ForceCacheRefreshAware
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupLinearStageSupport
import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForUpInstancesTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.EnableServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class EnableServerGroupStage extends TargetServerGroupLinearStageSupport implements ForceCacheRefreshAware {

  public static final String PIPELINE_CONFIG_TYPE = "enableServerGroup"

  private final DynamicConfigService dynamicConfigService;

  @Autowired
  EnableServerGroupStage(DynamicConfigService dynamicConfigService) {
    this.dynamicConfigService = dynamicConfigService
  }

  @Override
  protected void taskGraphInternal(StageExecution stage, TaskNode.Builder builder) {
    builder
      .withTask("determineHealthProviders", DetermineHealthProvidersTask)
      .withTask("enableServerGroup", EnableServerGroupTask)
      .withTask("monitorServerGroup", MonitorKatoTask)
      .withTask("waitForUpInstances", WaitForUpInstancesTask)

    if (isForceCacheRefreshEnabled(dynamicConfigService)) {
      builder.withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
    }
  }
}
