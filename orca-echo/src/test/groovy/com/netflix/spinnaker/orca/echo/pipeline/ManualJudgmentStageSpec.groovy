/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.echo.pipeline

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.model.AbstractStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Unroll

import static ManualJudgmentStage.*

class ManualJudgmentStageSpec extends Specification {
  @Unroll
  void "should return execution status based on judgmentStatus"() {
    given:
    def task = new WaitForManualJudgmentTask()

    when:
    def result = task.execute(new PipelineStage(new Pipeline(), "", context))

    then:
    result.status == expectedStatus
    result.stageOutputs.isEmpty()

    where:
    context                      || expectedStatus
    [:]                          || ExecutionStatus.RUNNING
    [judgmentStatus: "continue"] || ExecutionStatus.SUCCEEDED
    [judgmentStatus: "Continue"] || ExecutionStatus.SUCCEEDED
    [judgmentStatus: "stop"]     || ExecutionStatus.TERMINAL
    [judgmentStatus: "STOP"]     || ExecutionStatus.TERMINAL
    [judgmentStatus: "unknown"]  || ExecutionStatus.RUNNING
  }

  void "should only send notifications for supported types"() {
    given:
    def task = new WaitForManualJudgmentTask(echoService: Mock(EchoService))

    when:
    def result = task.execute(new PipelineStage(new Pipeline(), "", [notifications: [
      new Notification(type: "email", address: "test@netflix.com"),
      new Notification(type: "hipchat", address: "Hipchat Channel"),
      new Notification(type: "sms", address: "11122223333"),
      new Notification(type: "unknown", address: "unknown")
    ]]))

    then:
    result.status == ExecutionStatus.RUNNING
    result.stageOutputs.notifications.findAll { it.lastNotified }*.type == ["email", "hipchat", "sms"]
  }

  @Unroll
  void "should notify if `notifyEveryMs` duration has been exceeded"() {
    expect:
    notification.shouldNotify(now) == shouldNotify

    where:
    notification                                                      | now             || shouldNotify
    new Notification()                                                | new Date()      || true
    new Notification(lastNotified: new Date(1))                       | new Date()      || false
    new Notification(lastNotified: new Date(1), notifyEveryMs: 60000) | new Date(60001) || true
  }

  void "should update `lastNotified` whenever a notification is sent"() {
    given:
    def echoService = Mock(EchoService)
    def notification = new Notification(type: "sms", address: "111-222-3333")

    def stage = new PipelineStage(new Pipeline(), "")
    stage.execution.id = "ID"
    stage.execution.application = "APPLICATION"

    when:
    notification.notify(echoService, stage)

    then:
    notification.lastNotified != null

    1 * echoService.create({ EchoService.Notification n ->
      assert n.notificationType == EchoService.Notification.Type.SMS
      assert n.to == ["111-222-3333"]
      assert n.templateGroup == "manualJudgment"
      assert n.severity == EchoService.Notification.Severity.HIGH

      assert n.source.executionId == "ID"
      assert n.source.executionType == "pipeline"
      assert n.source.application == "APPLICATION"
      true
    } as EchoService.Notification)
    0 * _
  }

  @Unroll
  void "should return modified authentication context"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "", [
      judgmentStatus                : judgmentStatus,
      propagateAuthenticationContext: propagateAuthenticationContext
    ])
    stage.lastModified = new AbstractStage.LastModifiedDetails(user: "modifiedUser", allowedAccounts: ["group1"])

    when:
    def authenticatedUser = new ManualJudgmentStage().authenticatedUser(stage)

    then:
    authenticatedUser.isPresent() == isPresent
    !isPresent || (authenticatedUser.get().username == "modifiedUser" && authenticatedUser.get().allowedAccounts == ["group1"])

    where:
    judgmentStatus | propagateAuthenticationContext || isPresent
    "continue"     | true                           || true
    "ContinuE"     | true                           || true
    "continue"     | false                          || false
    "stop"         | true                           || false
    "stop"         | false                          || false
    ""             | true                           || false
    null           | true                           || false
  }

  void "should send notifications with parsed expressions"() {
    given:
    def echoService = Mock(EchoService)
    def task = new WaitForManualJudgmentTask(echoService: echoService)

    when:
    def result = task.execute(new PipelineStage(new Pipeline(), "", [
      notifications: [new Notification(type: "email", address: "test@netflix.com")],
      instructions: '${replaceMe}',
      replaceMe: 'newValue'
    ]))

    then:
    1 * echoService.create({ EchoService.Notification n ->
      assert n.additionalContext.instructions == 'newValue'
    } as EchoService.Notification)
    0 * _
  }
}
