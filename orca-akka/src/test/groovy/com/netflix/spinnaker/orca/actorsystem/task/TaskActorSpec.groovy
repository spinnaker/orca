/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.actorsystem.task

import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.sharding.ShardRegion.Passivate
import akka.testkit.TestActorRef
import akka.testkit.TestProbe
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.actorsystem.stage.StageMessage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.context.support.StaticApplicationContext
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.BlockingVariable
import static akka.actor.ActorRef.noSender
import static akka.actor.SupervisorStrategy.Stop$.MODULE$ as Stop
import static com.netflix.spinnaker.orca.ExecutionStatus.*
import static java.util.concurrent.TimeUnit.SECONDS

class TaskActorSpec extends Specification implements DataFixtures {

  public static final FiniteDuration ONE_SECOND = Duration.create(1, SECONDS)

  def actorSystem = ActorSystem.create(getClass().simpleName)
  @AutoCleanup
  def applicationContext = new StaticApplicationContext()
  def executionRepository = Mock(ExecutionRepository)
  def stageActor = new TestProbe(actorSystem)
  def props = Props.create(TaskActor, {
    new TaskActor(applicationContext, executionRepository, { stageActor.ref() })
  })
  def parent = new TestProbe(actorSystem)
  def taskActor = new TestActorRef(actorSystem, props, parent.ref(), "TaskActor")
  def task = Stub(Task)

  def setup() {
    sleep 100 // TODO: figure out why this is necessary
    applicationContext.beanFactory.registerSingleton("whateverTask", task)
  }

  def cleanup() {
    Await.result(actorSystem.terminate(), ONE_SECOND)
  }

  def "executes a task when told to"() {
    given:
    def pipeline = pipelineWithOneTask()
    executionRepository.retrievePipeline(pipeline.id) >> pipeline

    and:
    task.execute(_) >> new DefaultTaskResult(SUCCEEDED)

    when:
    taskActor.tell(runFirstTask(pipeline), noSender())

    then:
    with(stageActor.expectMsgClass(StageMessage.TaskComplete)) {
      status() == SUCCEEDED
    }
  }

  @Unroll
  def "shuts down actor after task is completes with #finalStatus status"() {
    given:
    def pipeline = pipelineWithOneTask()
    executionRepository.retrievePipeline(pipeline.id) >> pipeline

    and:
    task.execute(_) >> new DefaultTaskResult(finalStatus)

    when:
    def cmd = runFirstTask(pipeline)
    taskActor.tell(cmd, noSender())

    then:
    with(stageActor.expectMsgClass(StageMessage.TaskComplete)) {
      status() == finalStatus
    }

    and:
    parent.expectMsg(new Passivate(Stop))

    where:
    finalStatus << ExecutionStatus.values().findAll { it.complete }
  }

  def "does not run the task if the execution has been cancelled"() {
    given:
    def pipeline = pipelineWithOneTask()
    pipeline.canceled = true
    executionRepository.retrievePipeline(pipeline.id) >> pipeline

    when:
    def cmd = runFirstTask(pipeline)
    taskActor.tell(cmd, noSender())

    then:
    stageActor.expectMsgClass(StageMessage.TaskCancelled)

    and:
    0 * task.execute(_)

    and:
    parent.expectMsg(new Passivate(Stop))
  }

  @Unroll
  def "does not run the task if the task status is already #taskStatus"() {
    given:
    def pipeline = pipelineWithOneTask()
    pipeline.stages[0].tasks[0].status = taskStatus
    executionRepository.retrievePipeline(pipeline.id) >> pipeline

    when:
    def cmd = runFirstTask(pipeline)
    taskActor.tell(cmd, noSender())

    then:
    with(stageActor.expectMsgClass(StageMessage.TaskSkipped)) {
      status() == taskStatus
    }

    and:
    0 * task.execute(_)

    and:
    parent.expectMsg(new Passivate(Stop))

    where:
    taskStatus << ExecutionStatus.values().findAll { it.complete || it.halt }
  }

  @Unroll
  def "changes a TERMINAL status to #expectedStatus if failPipeline is #failPipeline in the stage context"() {
    given:
    def pipeline = pipelineWithOneTask()
    if (failPipeline != null) {
      pipeline.stages[0].context.failPipeline = failPipeline
    }
    executionRepository.retrievePipeline(pipeline.id) >> pipeline

    and:
    task.execute(_) >> new DefaultTaskResult(TERMINAL)

    when:
    def cmd = runFirstTask(pipeline)
    taskActor.tell(cmd, noSender())

    then:
    with(stageActor.expectMsgClass(StageMessage.TaskComplete)) {
      status() == expectedStatus
    }

    and:
    parent.expectMsg(new Passivate(Stop))

    where:
    failPipeline | expectedStatus
    true         | TERMINAL
    false        | STOPPED
    null         | TERMINAL
  }

  @Unroll
  def "does not cancel the pipeline if the task status is #taskStatus"() {
    given:
    def pipeline = pipelineWithOneTask()
    executionRepository.retrievePipeline(pipeline.id) >> pipeline

    and:
    task.execute(_) >> new DefaultTaskResult(taskStatus)

    when:
    def cmd = runFirstTask(pipeline)
    taskActor.tell(cmd, noSender())

    then:
    with(stageActor.expectMsgClass(StageMessage.TaskStatusUpdate)) {
      status() == taskStatus
    }

    and:
    0 * executionRepository.cancel(_)

    where:
    taskStatus << ExecutionStatus.values() - TERMINAL
  }

  @Unroll
  def "cancels the pipeline if the task status is #taskStatus"() {
    given:
    def pipeline = pipelineWithOneTask()
    executionRepository.retrievePipeline(pipeline.id) >> pipeline

    and:
    def cancelledId = new BlockingVariable<String>()
    executionRepository.cancel(_) >> { String it ->
      cancelledId.set(it)
    }

    and:
    task.execute(_) >> new DefaultTaskResult(taskStatus)

    when:
    def cmd = runFirstTask(pipeline)
    taskActor.tell(cmd, noSender())

    then:
    with(stageActor.expectMsgClass(StageMessage.TaskComplete)) {
      status() == taskStatus
    }

    and:
    cancelledId.get() == pipeline.id

    where:
    taskStatus = TERMINAL
  }

  @Unroll
  def "does not cancel the pipeline if the task status is #taskStatus but failPipeline is false"() {
    given:
    def pipeline = pipelineWithOneTask()
    pipeline.stages[0].context.failPipeline = false
    executionRepository.retrievePipeline(pipeline.id) >> pipeline

    and:
    task.execute(_) >> new DefaultTaskResult(taskStatus)

    when:
    def cmd = runFirstTask(pipeline)
    taskActor.tell(cmd, noSender())

    then:
    with(stageActor.expectMsgClass(StageMessage.TaskComplete)) {
      status() == STOPPED
    }

    and:
    0 * executionRepository.cancel(_)

    where:
    taskStatus = TERMINAL
    expectedStatus = STOPPED
  }

  @Unroll
  def "saves any task output to the stage context if the result is #taskStatus"() {
    given:
    def pipeline = pipelineWithOneTask()
    def stage = pipeline.stages.first()
    executionRepository.retrievePipeline(pipeline.id) >> pipeline

    and:
    def storedStage = new BlockingVariable<Stage>()
    executionRepository.storeStage(_) >> { Stage it ->
      storedStage.set(it)
    }

    and:
    task.execute(_) >> new DefaultTaskResult(taskStatus, [foo: "bar"])

    when:
    taskActor.tell(runFirstTask(pipeline), noSender())

    then:
    with(stageActor.expectMsgClass(StageMessage.TaskStatusUpdate)) {
      status() == taskStatus
    }

    and:
    with(storedStage.get()) {
      id == stage.id
      context.foo == "bar"
    }

    where:
    taskStatus << ExecutionStatus.values()
  }

}
