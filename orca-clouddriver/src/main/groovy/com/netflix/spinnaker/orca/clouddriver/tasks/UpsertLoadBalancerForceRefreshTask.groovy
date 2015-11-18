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

package com.netflix.spinnaker.orca.clouddriver.tasks

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
public class UpsertLoadBalancerForceRefreshTask implements Task {
  static final String REFRESH_TYPE = "LoadBalancer"

  @Autowired
  OortService oort

  @Override
  TaskResult execute(Stage stage) {
    stage.context.targets.each { Map target ->
      target.availabilityZones.keySet().each { String region ->
        oort.forceCacheUpdate(
          REFRESH_TYPE, [loadBalancerName: target.name, region: region, account: target.credentials]
        )
      }
    }

    new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
  }
}
