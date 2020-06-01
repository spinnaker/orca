/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Component
class DisableInstancesTask implements CloudProviderAware, Task {

  @Autowired KatoService katoService

  @Autowired
  TrafficGuard trafficGuard

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {

    String cloudProvider = getCloudProvider(stage)
    String account = getCredentials(stage)

    def serverGroupName = stage.context.serverGroupName ?: stage.context.asgName

    trafficGuard.verifyInstanceTermination(
      serverGroupName,
      MonikerHelper.monikerFromStage(stage, serverGroupName),
      stage.context.instanceIds as List<String>,
      account,
      Location.region(stage.context.region as String),
      cloudProvider,
      "Disabling the requested instances in")

    def actions = [[disableInstancesInDiscovery: stage.context], [deregisterInstancesFromLoadBalancer: stage.context]]
    def taskId = katoService.requestOperations(actions)

    TaskResult.builder(ExecutionStatus.SUCCEEDED).context([
      "notification.type"           : 'disableinstances',
      "kato.last.task.id"           : taskId,
      interestingHealthProviderNames: HealthHelper.getInterestingHealthProviderNames(stage, ["Discovery", "LoadBalancer"])
    ]).build()
  }
}
