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

package com.netflix.spinnaker.orca.clouddriver.tasks.instance

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class RegisterInstancesWithLoadBalancerTaskSpec extends Specification {
  @Subject task = new RegisterInstancesWithLoadBalancerTask()
  def stage = stage()
  def taskId = new TaskId(UUID.randomUUID().toString())

  def registerInstancesWithLoadBalancerConfig = [
    instanceIds      : ["some-instance-name"],
    loadBalancerNames: ["flapjack-frontend"],
    region           : "us-central1",
    credentials      : "test-account-name",
    cloudProvider    : "abc"
  ]

  def setup() {
    stage.context.putAll(registerInstancesWithLoadBalancerConfig)
  }

  def "creates a register instances with load balancer task based on job parameters"() {
    given:
      def operations
      task.kato = Mock(KatoService) {
        1 * requestOperations("abc", _) >> {
          operations = it[1]
          taskId
        }
      }

    when:
    task.execute(stage)

    then:
      operations.size() == 1
      with(operations[0].registerInstancesWithLoadBalancer) {
        it instanceof Map
        instanceIds == this.registerInstancesWithLoadBalancerConfig.instanceIds
        loadBalancerNames == this.registerInstancesWithLoadBalancerConfig.loadBalancerNames
        region == this.registerInstancesWithLoadBalancerConfig.region
        credentials == this.registerInstancesWithLoadBalancerConfig.credentials
        cloudProvider == this.registerInstancesWithLoadBalancerConfig.cloudProvider
      }
  }

  def "returns a success status with the kato task id"() {
    given:
      task.kato = Stub(KatoService) {
        requestOperations(*_) >> taskId
      }

    when:
    def result = task.execute(stage)

    then:
      result.status == ExecutionStatus.SUCCEEDED
    result.context."kato.last.task.id" == taskId
    result.context.interestingHealthProviderNames == ["LoadBalancer", "TargetGroup"]
  }
}
