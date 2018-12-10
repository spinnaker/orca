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

import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.AbstractWaitingForInstancesTask
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Component
@Slf4j
class WaitForRequiredInstancesDownTask extends AbstractWaitingForInstancesTask {
  @Override
  protected boolean hasSucceeded(Stage stage, Map serverGroup, List<Map> instances, Collection<String> interestingHealthProviderNames) {
    log.info "*** interestingHealthProviderNames=$interestingHealthProviderNames"

    if (interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty()) {
      return true
    }

    log.info "*** instances=$instances"

    def targetDesiredSize = instances.size()

    log.info "*** targetDesiredSize=$targetDesiredSize"

    // During a rolling red/black we want a percentage of instances to be disabled.
    def desiredPercentage = stage.context.desiredPercentage
    if (desiredPercentage != null) {
      def instancesToDisable = getInstancesToDisable(stage, instances)
      if (instancesToDisable) {
        /**
         * Ensure that any explicitly supplied (by clouddriver!) instance ids have been disabled.
         *
         * If no instance ids found, fall back to using `desiredPercentage` to calculate how many instances
         * should be disabled.
         */
        def instancesAreDisabled = instancesToDisable.every { instance ->
          return HealthHelper.someAreDownAndNoneAreUp(instance, interestingHealthProviderNames)
        }

        log.debug(
          "{} {}% of {}: {} (executionId: {})",
          instancesAreDisabled ? "Disabled" : "Disabling",
          desiredPercentage,
          serverGroup.name,
          instancesToDisable.collect { it.name }.join(", "),
          stage.execution.id
        )

        return instancesAreDisabled
      }

      Map capacity = (Map) serverGroup.capacity
      Integer percentage = (Integer) desiredPercentage
      targetDesiredSize = getDesiredInstanceCount(capacity, percentage)
    }

    // We need at least target instances to be disabled.
    return instances.count { instance ->
      return HealthHelper.someAreDownAndNoneAreUp(instance, interestingHealthProviderNames)
    } >= targetDesiredSize
  }

  static List<Map> getInstancesToDisable(Stage stage, List<Map> instances) {
    TaskId lastTaskId = stage.context."kato.last.task.id" as TaskId
    def katoTasks = stage.context."kato.tasks" as List<Map>
    def lastKatoTask = katoTasks.find { it.id.toString() == lastTaskId.id }

    if (lastKatoTask) {
      def resultObjects = lastKatoTask.resultObjects as List<Map>
      def instanceIdsToDisable = resultObjects.find {
        it.containsKey("instanceIdsToDisable")
      }?.instanceIdsToDisable ?: []

      return instances.findAll { instanceIdsToDisable.contains(it.name) }
    }

    return []
  }
}
