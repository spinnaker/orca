/*
 * Copyright 2014 Netflix, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.retrofit.exceptions.RetrofitExceptionHandler
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import retrofit.RetrofitError

@Slf4j
abstract class AbstractInstancesCheckTask implements RetryableTask {
  long backoffPeriod = 5000
  long timeout = 7200000

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  abstract
  protected Map<String, List<String>> getServerGroups(Stage stage)

  abstract protected boolean hasSucceeded(Stage stage, Map serverGroup, List<Map> instances, Collection<String> interestingHealthProviderNames)

  @Override
  TaskResult execute(Stage stage) {
    String account = stage.context."account.name"
    if (stage.context.account && !account) {
      account = stage.context.account
    } else if (stage.context.credentials && !account) {
      account = stage.context.credentials
    }

    Map<String, List<String>> serverGroups = getServerGroups(stage)

    if (!serverGroups || !serverGroups?.values()?.flatten()) {
      return new DefaultTaskResult(ExecutionStatus.FAILED)
    }
    Names names = Names.parseName(serverGroups.values().flatten()[0])
    try {
      // TODO(ttomsu): Remove this when providerType is dead.
      def cloudProvider = stage.context.cloudProvider ?: stage.context.providerType ?: "aws"
      def response = oortService.getCluster(names.app, account, names.cluster, cloudProvider)

      if (response.status != 200) {
        return new DefaultTaskResult(ExecutionStatus.RUNNING)
      }
      def cluster = objectMapper.readValue(response.body.in().text, Map)
      if (!cluster || !cluster.serverGroups) {
        return new DefaultTaskResult(ExecutionStatus.RUNNING)
      }
      Map<String, Boolean> seenServerGroup = serverGroups.values().flatten().collectEntries { [(it): false] }
      for (Map serverGroup in cluster.serverGroups) {
        String region = serverGroup.region
        String name = serverGroup.name

        List instances = serverGroup.instances ?: []

        // Look across ASGs in Cluster for specified ones to exist.
        if (!serverGroups.containsKey(region) || !serverGroups[region].contains(name)) {
          continue
        }

        seenServerGroup[name] = true
        Collection<String> interestingHealthProviderNames = stage.context.interestingHealthProviderNames as Collection
        if (interestingHealthProviderNames == null) {
          interestingHealthProviderNames = stage.context?.appConfig?.interestingHealthProviderNames as Collection
        }
        def isComplete = hasSucceeded(stage, serverGroup, instances, interestingHealthProviderNames)
        if (!isComplete) {
          Map newContext = [:]
          if (seenServerGroup && !stage.context.capacitySnapshot) {
            newContext = [
              zeroDesiredCapacityCount: 0,
              capacitySnapshot: [
                minSize: serverGroup.asg.minSize,
                desiredCapacity: serverGroup.asg.desiredCapacity,
                maxSize: serverGroup.asg.maxSize
              ]
            ]
          }
          if (seenServerGroup) {
            if (serverGroup.asg.desiredCapacity == 0) {
              newContext.zeroDesiredCapacityCount = (stage.context.zeroDesiredCapacityCount ?: 0) + 1
            } else {
              newContext.zeroDesiredCapacityCount = 0
            }
          }
          return new DefaultTaskResult(ExecutionStatus.RUNNING, newContext)
        }
      }
      if (seenServerGroup.values().contains(false)) {
        new DefaultTaskResult(ExecutionStatus.RUNNING)
      } else {
        new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
      }
    } catch (RetrofitError e) {
      def retrofitErrorResponse = new RetrofitExceptionHandler().handle(stage.name, e)
      if (e.response?.status == 404) {
        return new DefaultTaskResult(ExecutionStatus.RUNNING)
      } else if (e.response?.status >= 500) {
        log.error("Unexpected retrofit error (${retrofitErrorResponse})")
        return new DefaultTaskResult(ExecutionStatus.RUNNING, [lastRetrofitException: retrofitErrorResponse])
      }

      throw e
    }
  }

}
