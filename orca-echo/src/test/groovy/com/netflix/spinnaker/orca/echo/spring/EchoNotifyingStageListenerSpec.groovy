package com.netflix.spinnaker.orca.echo.spring

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.model.*
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static org.hamcrest.Matchers.containsInAnyOrder
import static spock.util.matcher.HamcrestSupport.expect

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

  def "triggers an event when a stage starts"() {
    given:
    def task = new DefaultTask(stageStart: true)

    and:
    def events = []
    echoService.recordEvent(_) >> { events << it[0]; null }

    when:
    echoListener.beforeTask(null, pipelineStage, task)

    then:
    events.size() == 2
    expect events.details.type, containsInAnyOrder("orca:task:starting", "orca:stage:starting")
  }

  def "triggers an event when a task starts"() {
    given:
    def task = new DefaultTask(stageStart: false)

    and:
    def events = []
    echoService.recordEvent(_) >> { events << it[0]; null }

    when:
    echoListener.beforeTask(null, pipelineStage, task)

    then:
    events.size() == 1
    events.details.type == ["orca:task:starting"]
  }

  @Unroll
  def "triggers an event when a task completes"() {
    given:
    def task = new DefaultTask(name: taskName, stageEnd: isEnd)

    when:
    echoListener.afterTask(null, stage, task, executionStatus, wasSuccessful)

    then:
    invocations * echoService.recordEvent(_)

    where:
    invocations | stage              | taskName   | executionStatus           | wasSuccessful | isEnd
    0           | orchestrationStage | "stageEnd" | ExecutionStatus.RUNNING   | true          | false
    1           | orchestrationStage | "stageEnd" | ExecutionStatus.STOPPED   | true          | false
    1           | pipelineStage      | "xxx"      | ExecutionStatus.SUCCEEDED | true          | false
    2           | pipelineStage      | "stageEnd" | ExecutionStatus.SUCCEEDED | true          | false
    2           | pipelineStage      | "stageEnd" | ExecutionStatus.SUCCEEDED | false         | false
    2           | pipelineStage      | "xxx"      | ExecutionStatus.SUCCEEDED | true          | true
    1           | orchestrationStage | "xxx"      | ExecutionStatus.SUCCEEDED | true          | true
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
