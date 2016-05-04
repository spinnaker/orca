package com.netflix.spinnaker.orca.echo.spring

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.OrchestrationStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class EchoNotifyingStageListenerSpec extends Specification {

  def echoService = Mock(EchoService)

  @Subject
  def echoListener = new EchoNotifyingStageListener(echoService)

  @Shared
  def pipelineStage = new PipelineStage(new Pipeline(), "test")

  @Shared
  def orchestrationStage = new OrchestrationStage(new Orchestration(), "test")

  def "triggers an event when a task step starts"() {
    given:
    def task = new DefaultTask(status: ExecutionStatus.NOT_STARTED)

    when:
    echoListener.beforeTask(null, pipelineStage, task)

    then:
    1 * echoService.recordEvent(_)
  }

  @Unroll
  def "triggers an event when a task completes"() {
    given:
    def task = new DefaultTask(name: taskName)

    when:
    echoListener.afterTask(null, stage, task, executionStatus, wasSuccessful)

    then:
    invocations * echoService.recordEvent(_)

    where:
    invocations | stage              | taskName   | executionStatus           | wasSuccessful
    0           | orchestrationStage | "stageEnd" | ExecutionStatus.RUNNING   | true
    1           | orchestrationStage | "stageEnd" | ExecutionStatus.STOPPED   | true
    1           | pipelineStage      | "xxx"      | ExecutionStatus.SUCCEEDED | true
    2           | pipelineStage      | "stageEnd" | ExecutionStatus.SUCCEEDED | true
    2           | pipelineStage      | "stageEnd" | ExecutionStatus.SUCCEEDED | false
  }

  @Unroll
  def "sends the correct data to echo when the task completes"() {
    given:
    def task = new DefaultTask(name: taskName)

    and:
    def message
    echoService.recordEvent(_) >> {
      message = it[0]
      return null
    }

    when:
    echoListener.afterTask(null, stage, task, executionStatus, wasSuccessful)

    then:
    message.details.source == "orca"
    message.details.application == pipelineStage.execution.application
    message.details.type == "orca:${type}:$echoMessage"
    message.details.type instanceof String
    message.content.standalone == standalone
    message.content.taskName == taskName

    where:
    stage              | taskName   | executionStatus           | wasSuccessful || echoMessage || type    || standalone
    orchestrationStage | "xxx"      | ExecutionStatus.STOPPED   | true          || "complete"  || "task"  || true
    pipelineStage      | "xxx"      | ExecutionStatus.SUCCEEDED | true          || "complete"  || "task"  || false
    pipelineStage      | "stageEnd" | ExecutionStatus.SUCCEEDED | true          || "complete"  || "stage" || false
    pipelineStage      | "stageEnd" | ExecutionStatus.SUCCEEDED | false         || "failed"    || "stage" || false
  }
}
