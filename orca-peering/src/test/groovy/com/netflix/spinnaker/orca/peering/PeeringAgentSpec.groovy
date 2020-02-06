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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.test.model.ExecutionBuilder
import org.jooq.DSLContext
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.ExecutionStatus.COMPLETED
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE

class PeeringAgentSpec extends Specification {
  @Subject
  def peeringAgent = new PeeringAgent(
      Mock(DSLContext),
      "peeredPoolName",
      "peeredId",
      42,
      Mock(NotificationClusterLock)
  )

  SqlDbRawAccess src = Stub(SqlDbRawAccess)
  SqlDbRawAccess dest = Stub(SqlDbRawAccess)

  def setupSpec() {
    peeringAgent.srcDB = src
    peeringAgent.destDB = dest
  }

  @Unroll
  def "should copy new #status (completed) #executionType"() {
    given:
    1 * src.getCompletedExecutionIds(executionType, "peeredId") >> ["a", "b", "c"]
    1 * dest.getCompletedExecutionIds(executionType, "peeredId") >> ["a", "b"]

    when:
    peeringAgent.tick()

    then:
    1 * dest.copyExecutionsWithIds(executionType, ["c"]) // TODO: missing API?
    // TODO: check the stages are being copied too

    where:
    executionType | status
    PIPELINE      | COMPLETED
    ORCHESTRATION | COMPLETED
  }

  @Unroll
  def "should copy new #status (not completed) #executionType"() {
    given:
    // TODO: are running/completed/not started etc really treated differently?
    1 * src.getAllExecutionIds(executionType, "peeredId") >> ["a", "b", "c"]
    1 * dest.getAllExecutionIds(executionType, "peeredId") >> ["a", "b"]

    when:
    peeringAgent.tick()

    then:
    1 * dest.copyExecutionsWithIds(executionType, ["c"]) // TODO: missing API?
    // TODO: check the stages are being copied too

    where:
    executionType | status
    PIPELINE      | RUNNING
    ORCHESTRATION | RUNNING
  }

  @Unroll
  def "should propagate deletions of #status #executionType"() {
    given:

    1 * src.getCompletedExecutionIds(executionType, "peeredId") >> ["b", "c"]
    1 * dest.getCompletedExecutionIds(executionType, "peeredId") >> ["a", "b", "c"]

    when:
    peeringAgent.tick()

    then:
    1 * dest.deleteExecutions(executionType, ["a"])
    // TODO: check the stages are being deleted too

    where:
    executionType | status
    PIPELINE      | COMPLETED
    ORCHESTRATION | COMPLETED
  }


}
