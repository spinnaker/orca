/*
 * Copyright 2020 Grab Holdings, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Subject

class DisableInstancesTaskSpec extends Specification {
  @Subject task = new DisableInstancesTask()

  def taskId = new TaskId(UUID.randomUUID().toString())

  def "should disable instance in discovery and load balancer by default"() {
    setup:
    def operations
    def disableInstancesConfig = [
        serverGroupName : "myservice-stg",
        region          : "us-east-1",
        instanceIds     : ["i-06f362428ec48c873"],
        credentials     : "main"
    ]
    def stage = new StageExecutionImpl(type: "whatever")
    stage.context.putAll(disableInstancesConfig)
    task.trafficGuard = Mock(TrafficGuard)
    task.katoService = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations = it[0]
        taskId
      }
    }

    when:
    task.execute(stage)

    then:
    operations.size == 2
    with(operations[0].disableInstancesInDiscovery) {
      it instanceof Map
      instanceIds == disableInstancesConfig.instanceIds
    }
    with(operations[1].deregisterInstancesFromLoadBalancer) {
      it instanceof Map
      instanceIds == disableInstancesConfig.instanceIds
    }
  }

  def "should only deregister from load balancer if interestingHealthProviderNames is explicitly defined"() {
    setup:
    def operations
    def disableInstancesConfig = [
        serverGroupName : "myservice-stg",
        region          : "us-east-1",
        instanceIds     : ["i-06f362428ec48c873"],
        credentials     : "main",
        interestingHealthProviderNames: ["LoadBalancer"]
    ]
    def stage = new StageExecutionImpl(type: "whatever")
    stage.context.putAll(disableInstancesConfig)
    task.trafficGuard = Mock(TrafficGuard)
    task.katoService = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations = it[0]
        taskId
      }
    }

    when:
    task.execute(stage)

    then:
    operations.size == 1
    with(operations[0].deregisterInstancesFromLoadBalancer) {
      it instanceof Map
      instanceIds == disableInstancesConfig.instanceIds
    }
  }
}
