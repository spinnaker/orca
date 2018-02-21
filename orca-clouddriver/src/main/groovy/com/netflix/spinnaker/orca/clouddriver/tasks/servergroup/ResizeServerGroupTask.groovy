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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy.OptionalConfiguration
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4j
class ResizeServerGroupTask extends AbstractServerGroupTask {
  String serverGroupAction = ResizeServerGroupStage.TYPE

  @Autowired
  List<ResizeStrategy> resizeStrategies

  @Autowired
  TrafficGuard trafficGuard

  Map getAdditionalContext(Stage stage, Map operation) {
    [capacity: operation.capacity]
  }

  /**
   * Track the _original_ capacity of the server group being resized in case it needs to be subsequently restored.
   */
  @Override
  Map<String, Object> getAdditionalOutputs(Stage stage, Map operation) {
    def originalCapacityKey = "originalCapacity.${operation.serverGroupName}".toString()
    def originalCapacity = stage.context.get(originalCapacityKey)

    def originalCapacityValue = originalCapacity ?: operation.originalCapacity
    if (!originalCapacityValue) {
      return [:]
    }

    return [
      (originalCapacityKey) : originalCapacityValue
    ]
  }

  Map convert(Stage stage) {
    Map operation = super.convert(stage)
    String serverGroupName = operation.serverGroupName
    String cloudProvider = getCloudProvider(stage)
    String account = getCredentials(stage)
    Location location = TargetServerGroup.Support.locationFromOperation(operation)
    def resizeConfig = stage.mapTo(OptionalConfiguration)

    ResizeStrategy strategy = resizeStrategies.find { it.handles(resizeConfig.actualAction) }
    if (!strategy) {
      throw new IllegalStateException("$resizeConfig.actualAction not implemented")
    }

    ResizeStrategy.CapacitySet capacitySet = strategy.capacityForOperation(
      stage, account, serverGroupName, cloudProvider, location, resizeConfig
    )
    operation.capacity = [
      min: capacitySet.target.min,
      desired: capacitySet.target.desired,
      max: capacitySet.target.max
    ]
    operation.originalCapacity = capacitySet.original

    return operation
  }

  @Override
  void validateClusterStatus(Map operation, Moniker moniker) {
    if (operation.capacity.desired == 0) {
      trafficGuard.verifyTrafficRemoval(operation.serverGroupName as String,
        moniker,
        getCredentials(operation),
        getLocation(operation),
        getCloudProvider(operation), "Removal of all instances in ")
    }
  }
}
