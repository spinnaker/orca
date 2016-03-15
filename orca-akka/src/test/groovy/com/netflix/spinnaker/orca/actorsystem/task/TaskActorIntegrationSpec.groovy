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

import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function
import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import akka.testkit.TestKit
import akka.testkit.TestProbe
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.actorsystem.ActorSystemConfiguration
import com.netflix.spinnaker.orca.actorsystem.AkkaClusterConfiguration
import com.netflix.spinnaker.orca.actorsystem.ClusteredActorDefinition
import com.netflix.spinnaker.orca.actorsystem.stage.StageMessage
import com.netflix.spinnaker.orca.actorsystem.task.TaskMessage.ResumeTask
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Subject
import static akka.actor.ActorRef.noSender
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static java.util.concurrent.TimeUnit.SECONDS

class TaskActorIntegrationSpec extends Specification implements DataFixtures {

  public static final FiniteDuration ONE_SECOND = Duration.create(1, SECONDS)

  @AutoCleanup
  def applicationContext = new AnnotationConfigApplicationContext()
  def executionRepository = Stub(ExecutionRepository)
  @Subject
  ActorRef taskActor
  TestKit testKit
  ActorSystem actorSystem
  def task = Mock(Task)
  TestProbe stageActor

  def setup() {
    applicationContext.with {
      register(
        PropertyPlaceholderAutoConfiguration,
        ActorSystemConfiguration,
        TaskActorFactory,
        AkkaClusterConfiguration
      )
      beanFactory.registerSingleton("executionRepository", executionRepository)
      beanFactory.registerSingleton("mockTask", task)
      beanFactory.registerSingleton("stageActor", { stageActor.ref() } as Function<ActorContext, ActorRef>)
      refresh()

      actorSystem = getBean(ActorSystem)
      testKit = new TestKit(actorSystem)
      taskActor = createActorRef()
      stageActor = new TestProbe(actorSystem)

      testKit.watch(taskActor)
    }
  }

  def clean() {
    testKit.unwatch(taskActor)
  }

  def "runs task to completion"() {
    given:
    def pipeline = pipelineWithOneTask()

    and:
    executionRepository.retrievePipeline(pipeline.id) >> pipeline
    task.execute(_) >> new DefaultTaskResult(RUNNING) >> new DefaultTaskResult(SUCCEEDED)

    when:
    taskActor.tell(runFirstTask(pipeline), noSender())

    then:
    with(stageActor.expectMsgClass(StageMessage.TaskIncomplete)) {
      status() == RUNNING
    }
    with(stageActor.expectMsgClass(StageMessage.TaskComplete)) {
      status() == SUCCEEDED
    }
  }

  def "continues running a task after a restart"() {
    given:
    def pipeline = pipelineWithOneTask()

    and:
    executionRepository.retrievePipeline(pipeline.id) >> pipeline

    and:
    def allowTaskToComplete = new AtomicBoolean(false)
    task.execute(_) >> {
      new DefaultTaskResult(allowTaskToComplete.get() ? SUCCEEDED : RUNNING)
    }

    and:
    def command = runFirstTask(pipeline)
    println "sending to ${taskActor.path()}"
    taskActor.tell(command, noSender())
    with(stageActor.expectMsgClass(StageMessage.TaskIncomplete)) {
      status() == RUNNING
    }

    when:
    def newActorRef = killAndRestartActor()

    and:
    allowTaskToComplete.set(true)

    and:
    println "sending to ${newActorRef.path()}"
    newActorRef.tell(new ResumeTask(command.id()), noSender())

    then:
    with(stageActor.expectMsgClass(StageMessage.TaskComplete)) {
      status() == SUCCEEDED
    }
  }

  private ActorRef killAndRestartActor() {
    actorSystem.stop(taskActor)
    testKit.expectTerminated(taskActor, ONE_SECOND)
    def settings = ClusterShardingSettings.create(actorSystem)
    def clusterSharding = ClusterSharding.get(actorSystem)
    def actorDef = applicationContext.getBean(ClusteredActorDefinition)
    clusterSharding.start(
      actorDef.props().actorClass().simpleName,
      actorDef.props(),
      settings,
      actorDef.messageExtractor()
    )
    clusterSharding.shardRegion(TaskActor.simpleName)
  }

  private createActorRef() {
    def clusterSharding = applicationContext.getBean(ClusterSharding)
    clusterSharding.shardRegion(TaskActor.simpleName)
  }

}
