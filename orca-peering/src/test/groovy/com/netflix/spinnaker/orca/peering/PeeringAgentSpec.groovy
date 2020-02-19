/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.orca.peering

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE

class PeeringAgentSpec extends Specification {
  MySqlRawAccess src = Mock(MySqlRawAccess)
  MySqlRawAccess dest = Mock(MySqlRawAccess)
  PeeringMetrics metrics = Mock(PeeringMetrics)
  ExecutionCopier copier = Mock(ExecutionCopier)
  DynamicConfigService dynamicConfigService = Mock(DynamicConfigService)
  def clockDrift = 100

  PeeringAgent constructPeeringAgent() {
    return new PeeringAgent(
        "peeredId",
        1000,
        clockDrift,
        src,
        dest,
        dynamicConfigService,
        metrics,
        copier,
        Mock(NotificationClusterLock)
    )
  }

  def "respects dynamic enabled prop"() {
    given:
    def peeringAgent = constructPeeringAgent()

    when: 'disabled globally'
    peeringAgent.tick()

    then:
    1 * dynamicConfigService.isEnabled("pollers.peering", true) >> {
      return false
    }
    0 * dynamicConfigService.isEnabled("pollers.peering.peeredId", true) >> {
      return false
    }
    0 * src.getCompletedExecutionIds(_, _, _)

    when: 'disabled for a given agent only'
    peeringAgent.tick()

    then:
    1 * dynamicConfigService.isEnabled("pollers.peering", true) >> {
      return true
    }
    1 * dynamicConfigService.isEnabled("pollers.peering.peeredId", true) >> {
      return false
    }
    0 * src.getCompletedExecutionIds(_, _, _)
    0 * dest.getCompletedExecutionIds(_, _, _)
  }

  @Unroll
  def "correctly computes the execution diff for completed #executionType"() {
    def peeringAgent = constructPeeringAgent()
    peeringAgent.completedPipelinesMostRecentUpdatedTime = 1
    peeringAgent.completedOrchestrationsMostRecentUpdatedTime = 2
    dynamicConfigService.isEnabled(_, _) >> { return true }

    def callCount = (int)Math.signum(toDelete.size() + toCopy.size())
    when:
    peeringAgent.peerCompletedExecutions(executionType)

    then:
    1 * src.getCompletedExecutionIds(executionType, "peeredId", mostRecentTimeStamp) >> srcKeys
    1 * dest.getCompletedExecutionIds(executionType, "peeredId", mostRecentTimeStamp) >> destKeys

    callCount * dest.deleteExecutions(executionType, toDelete)
    callCount * metrics.incrementNumDeleted(executionType, toDelete.size())

    callCount * copier.copyInParallel(executionType, toCopy, ExecutionState.COMPLETED) >>
        new ExecutionCopier.MigrationChunkResult(30, 2, false)

    if (executionType == PIPELINE) {
      peeringAgent.completedPipelinesMostRecentUpdatedTime == srcKeys.max { it.updated_at }?.updated_at ?: 1
      peeringAgent.completedOrchestrationsMostRecentUpdatedTime == 2
    } else {
      peeringAgent.completedPipelinesMostRecentUpdatedTime == 1
      peeringAgent.completedOrchestrationsMostRecentUpdatedTime == srcKeys.max { it.updated_at }?.updated_at ?: 2
    }

    where:
    // Note: since the logic for executions and orchestrations should be the same, it's overkill to have the same set of tests for each
    // but it's easy so why not?
    executionType | mostRecentTimeStamp | srcKeys                                          | destKeys                                         || toDelete              | toCopy
    PIPELINE      | 1                   | [key("ID1", 10), key("ID2", 20), key("ID3", 30)] | [key("ID1", 10), key("ID2", 10), key("ID4", 10)] || ["ID4"]               | ["ID2", "ID3"]
    PIPELINE      | 1                   | [key("ID1", 10), key("ID2", 20), key("ID3", 30)] | [key("ID1", 10), key("ID2", 20), key("ID3", 30)] || []                    | []
    PIPELINE      | 1                   | []                                               | [key("ID1", 10), key("ID2", 20), key("ID3", 30)] || ["ID1", "ID2", "ID3"] | []
    PIPELINE      | 1                   | [key("ID1", 10), key("ID2", 20), key("ID3", 30)] | []                                               || []                    | ["ID1", "ID2", "ID3"]

    ORCHESTRATION | 2                   | [key("ID1", 10), key("ID2", 20), key("ID3", 30)] | [key("ID1", 10), key("ID2", 10), key("ID4", 10)] || ["ID4"]               | ["ID2", "ID3"]
    ORCHESTRATION | 2                   | [key("ID1", 10), key("ID2", 20), key("ID3", 30)] | [key("ID1", 10), key("ID2", 20), key("ID3", 30)] || []                    | []
    ORCHESTRATION | 2                   | []                                               | [key("ID1", 10), key("ID2", 20), key("ID3", 30)] || ["ID1", "ID2", "ID3"] | []
    ORCHESTRATION | 2                   | [key("ID1", 10), key("ID2", 20), key("ID3", 30)] | []                                               || []                    | ["ID1", "ID2", "ID3"]
  }

  def "copies all running executions of #executionType"() {
    given:
    def peeringAgent = constructPeeringAgent()
    dynamicConfigService.isEnabled(_, _) >> { return true }

    when:
    peeringAgent.peerActiveExecutions(executionType)

    then:
    1 * src.getActiveExecutionIds(executionType, "peeredId") >> activeIds
    copyCallCount * copier.copyInParallel(executionType, activeIds, ExecutionState.ACTIVE) >>
        new ExecutionCopier.MigrationChunkResult(30, 2, false)

    where:
    executionType | activeIds       | copyCallCount
    PIPELINE      | []              | 0
    PIPELINE      | ["ID1"]         | 1
    PIPELINE      | ["ID1", "ID4"]  | 1
    ORCHESTRATION | []              | 0
    ORCHESTRATION | ["ID1"]         | 1
    ORCHESTRATION | ["ID1", "ID4"]  | 1
  }

  private static def key(id, updatedat) {
    return new SqlRawAccess.ExecutionDiffKey(id, updatedat)
  }
}
