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
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.appengine

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.loadbalancer.UpsertLoadBalancerForceRefreshTask
import com.netflix.spinnaker.orca.clouddriver.tasks.loadbalancer.UpsertLoadBalancerResultObjectExtrapolationTask
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.appengine.UpsertAppEngineLoadBalancersTask
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Component
class UpsertAppEngineLoadBalancersStage implements StageDefinitionBuilder {
  @Override
  void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    builder
      .withTask("upsertLoadBalancers", UpsertAppEngineLoadBalancersTask)
      .withTask("monitorUpsert", MonitorKatoTask)
      .withTask("extrapolateUpsertResult", UpsertLoadBalancerResultObjectExtrapolationTask)
      .withTask("forceCacheRefresh", UpsertLoadBalancerForceRefreshTask)
  }
}
