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

import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.echo.pipeline.ManualJudgmentStage.Notification
import static com.netflix.spinnaker.orca.echo.pipeline.ManualJudgmentStage.WaitForManualJudgmentTask

class ManualJudgmentStageSpec extends Specification {

  EchoService echoService = Mock(EchoService)

  FiatPermissionEvaluator fpe = Mock(FiatPermissionEvaluator)

  @Unroll
  void "should return execution status based on judgmentStatus"() {
    given:
    def task = new WaitForManualJudgmentTask(Optional.of(echoService), Optional.of(fpe))

    when:
    def result = task.execute(new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", context))

    then:
    result.status == expectedStatus

    where:
    context                      || expectedStatus
    [:]                          || ExecutionStatus.RUNNING
    [judgmentStatus: "continue", isAuthorized: true] || ExecutionStatus.SUCCEEDED
    [judgmentStatus: "Continue", isAuthorized: true] || ExecutionStatus.SUCCEEDED
    [judgmentStatus: "stop", isAuthorized: true]     || ExecutionStatus.TERMINAL
    [judgmentStatus: "STOP", isAuthorized: true]     || ExecutionStatus.TERMINAL
    [judgmentStatus: "unknown", isAuthorized: true]  || ExecutionStatus.RUNNING
  }

  @Unroll
  void "should return execution status based on authorizedGroups"() {
    given:
    def task = new WaitForManualJudgmentTask(Optional.of(echoService), Optional.of(fpe))

    when:
    def result = task.execute(new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", context))

    then:
    1 * fpe.getPermission('abc@somedomain.io') >> {
      new UserPermission().addResources([new Role('foo'), new Role('baz')]).view
    }
    result.status == expectedStatus

    where:
    context                      || expectedStatus
    [judgmentStatus: "continue", isAuthorized: false, username: "abc@somedomain.io",
     stageRoles: ['foo'], permissions: [WRITE: ["foo"],READ: ["foo","baz"], EXECUTE: ["foo"]]] || ExecutionStatus.SUCCEEDED
    [judgmentStatus: "Continue", isAuthorized: false, username: "abc@somedomain.io",
     stageRoles: ['foo'], permissions: [WRITE: ["foo"],READ: ["foo","baz"], EXECUTE: ["foo"]]] || ExecutionStatus.SUCCEEDED
    [judgmentStatus: "stop", isAuthorized: false, username: "abc@somedomain.io",
     stageRoles: ['foo'], permissions: [WRITE: ["foo"],READ: ["foo","baz"], EXECUTE: ["foo"]]] || ExecutionStatus.TERMINAL
    [judgmentStatus: "STOP", isAuthorized: false, username: "abc@somedomain.io",
     stageRoles: ['foo'], permissions: [WRITE: ["foo"],READ: ["foo","baz"],EXECUTE: ["foo"]]] || ExecutionStatus.TERMINAL
    [judgmentStatus: "Continue", isAuthorized: false, username: "abc@somedomain.io",
     stageRoles: ['baz'], permissions: [WRITE: ["foo"],READ: ["foo","baz"], EXECUTE: ["foo"]]] || ExecutionStatus.RUNNING
    [judgmentStatus: "Stop", isAuthorized: false, username: "abc@somedomain.io",
     stageRoles: ['baz'], permissions: [WRITE: ["foo"],READ: ["foo","baz"], EXECUTE: ["foo"]]] || ExecutionStatus.RUNNING
  }

  void "should only send notifications for supported types"() {
    given:
    def task = new WaitForManualJudgmentTask(Optional.of(echoService), Optional.of(fpe))

    when:
    def result = task.execute(new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [notifications: [
      new Notification(type: "email", address: "test@netflix.com"),
      new Notification(type: "hipchat", address: "Hipchat Channel"),
      new Notification(type: "sms", address: "11122223333"),
      new Notification(type: "unknown", address: "unknown"),
      new Notification(type: "pubsub", publisherName: "foobar")
    ]]))

    then:
    result.status == ExecutionStatus.RUNNING
    result.context.notifications.findAll {
      it.lastNotifiedByNotificationState["manualJudgment"]
    }*.type == ["email", "hipchat", "sms", "pubsub"]
  }

  @Unroll
  void "if deprecated notification configuration is in use, only send notifications for awaiting judgment state"() {
    given:
    def task = new WaitForManualJudgmentTask(Optional.of(echoService), Optional.of(fpe))

    when:
    def result = task.execute(new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
      sendNotifications: sendNotifications,
      notifications: [
        new Notification(type: "email", address: "test@netflix.com", when: [ notificationState ])
      ],
      isAuthorized: true,
      judgmentStatus: judgmentStatus
    ]))

    then:
    result.status == executionStatus
    if (sent) result.context.notifications?.getAt(0)?.lastNotifiedByNotificationState?.containsKey(notificationState)

    where:
    sendNotifications | notificationState        | judgmentStatus | executionStatus           || sent
    true              | "manualJudgment"         | null           | ExecutionStatus.RUNNING   || true
    false             | "manualJudgment"         | null           | ExecutionStatus.RUNNING   || true
    true              | "manualJudgmentContinue" | "continue"     | ExecutionStatus.SUCCEEDED || true
    false             | "manualJudgmentContinue" | "continue"     | ExecutionStatus.SUCCEEDED || false
    true              | "manualJudgmentStop"     | "stop"         | ExecutionStatus.TERMINAL  || true
    false             | "manualJudgmentStop"     | "stop"         | ExecutionStatus.TERMINAL  || false
  }

  @Unroll
  void "should notify if `notifyEveryMs` duration has been exceeded for the specified notification state"() {
    expect:
    notification.shouldNotify(notificationState, now) == shouldNotify

    where:
    notification                                                                  | notificationState        | now             || shouldNotify
    new Notification()                                                            | "manualJudgment"         | new Date()      || true
    new Notification(
      lastNotifiedByNotificationState: [("manualJudgment"): new Date(1)])         | "manualJudgment"         | new Date()      || false
    new Notification(
      lastNotifiedByNotificationState: [("manualJudgment"): new Date(1)])         | "manualJudgmentContinue" | new Date()      || true
    new Notification(
      lastNotifiedByNotificationState: [("manualJudgment"): new Date(1),
                                        ("manualJudgmentContinue"): new Date(1)]) | "manualJudgmentContinue" | new Date()      || false
    new Notification(
      lastNotifiedByNotificationState: [("manualJudgment"): new Date(1)],
      notifyEveryMs: 60000)                                                       | "manualJudgment"         | new Date(60001) || true
  }

  @Unroll
  void "should update `lastNotified` whenever a notification is sent"() {
    given:
    def echoService = Mock(EchoService)
    def notification = new Notification(type: "sms", address: "111-222-3333")

    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "")
    stage.execution.id = "ID"
    stage.execution.application = "APPLICATION"

    when:
    notification.notify(echoService, stage, notificationState)

    then:
    notification.lastNotifiedByNotificationState[notificationState] != null

    1 * echoService.create({ EchoService.Notification n ->
      assert n.notificationType == EchoService.Notification.Type.SMS
      assert n.to == ["111-222-3333"]
      assert n.templateGroup == notificationState.toString()
      assert n.severity == EchoService.Notification.Severity.HIGH

      assert n.source.executionId == "ID"
      assert n.source.executionType == "pipeline"
      assert n.source.application == "APPLICATION"
      true
    } as EchoService.Notification)
    0 * _

    where:
    notificationState << [ "manualJudgment", "manualJudgmentContinue", "manualJudgmentStop" ]
  }

  @Unroll
  void "should retain unknown fields in the notification context"() {
    given:
    def task = new WaitForManualJudgmentTask(Optional.of(echoService), Optional.of(fpe))

    def slackNotification = new Notification(type: "slack")
    slackNotification.setOther("customMessage", "hello slack")

    def emailNotification = new Notification(type: "email")
    emailNotification.setOther("customSubject", "hello email")

    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
        notifications: [
            slackNotification,
            emailNotification
        ]
    ])

    when:
    def result = task.execute(stage)

    then:
    result.context.notifications?.get(0)?.other?.customMessage == "hello slack"
    result.context.notifications?.get(1)?.other?.customSubject == "hello email"
  }

  @Unroll
  void "should return modified authentication context"() {
    given:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
      judgmentStatus                : judgmentStatus,
      propagateAuthenticationContext: propagateAuthenticationContext
    ])
    stage.lastModified = new StageExecution.LastModifiedDetails(user: "modifiedUser", allowedAccounts: ["group1"])

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
}
