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

package com.netflix.spinnaker.orca.restart

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.SpringBatchConfiguration
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.config.OrcaPersistenceConfiguration
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.test.JobCompletionListener
import com.netflix.spinnaker.orca.test.TestConfiguration
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.JobRegistry
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Specification
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED

class PipelineRestartingSpec extends Specification {

  @AutoCleanup("destroy")
  def applicationContext = new AnnotationConfigApplicationContext()
  @Autowired ThreadPoolTaskExecutor taskExecutor
  @Autowired PipelineStarter pipelineStarter
  @Autowired ObjectMapper mapper
  @Autowired JobRegistry jobRegistry
  @Autowired JobExplorer jobExplorer
  @Autowired ExecutionRepository repository
  @Autowired JobCompletionListener jobCompletionListener

  def task1 = Mock(Task)
  def task2 = Mock(Task)

  def setup() {
    def testStage = new AutowiredTestStage("test", task1, task2)
    applicationContext.with {
      register(EmbeddedRedisConfiguration, JesqueConfiguration,
               BatchTestConfiguration, SpringBatchConfiguration, OrcaConfiguration, OrcaPersistenceConfiguration,
               JobCompletionListener, TestConfiguration)
      beanFactory.registerSingleton("testStage", testStage)
      refresh()

      beanFactory.autowireBean(testStage)
      beanFactory.autowireBean(this)
    }
    testStage.applicationContext = applicationContext
  }

  def cleanup() {
    applicationContext.destroy()
  }

  @Ignore
  def "if a pipeline restarts it resumes from where it left off"() {
    given:
    task1.execute(_) >> new DefaultTaskResult(SUCCEEDED)
    task2.execute(_) >> new DefaultTaskResult(RUNNING)

    and:
    def pipeline = pipelineStarter.start(pipelineConfigFor("test"))
//    jobCompletionListener.await()

    and:
    taskExecutor.shutdown()
    taskExecutor.initialize()

    when:
    jobCompletionListener.reset()
    pipelineStarter.resume(pipeline)
    jobCompletionListener.await()

    then:
    1 * task2.execute(_) >> new DefaultTaskResult(SUCCEEDED)
    0 * task1.execute(_)
  }

  def "a previously run pipeline can be restarted and completed tasks are skipped"() {
    given:
    def pipeline = pipelineStarter.create(mapper.readValue(pipelineConfigFor("test"), Map))
    pipeline.stages[0].tasks << new DefaultTask(id: 2, name: "task1", status: SUCCEEDED,
                                                startTime: System.currentTimeMillis(),
                                                endTime: System.currentTimeMillis())
    pipeline.stages[0].tasks << new DefaultTask(id: 3, name: "task2", status: RUNNING,
                                                startTime: System.currentTimeMillis())
    repository.store(pipeline)

    when:
    pipelineStarter.resume(pipeline)
    jobCompletionListener.await()

    then:
    repository.retrievePipeline(pipeline.id).status.toString() == SUCCEEDED.name()

    then:
    0 * task1.execute(_)
    1 * task2.execute(_) >> new DefaultTaskResult(SUCCEEDED)
  }

  private String pipelineConfigFor(String... stages) {
    def config = [
      application: "app",
      name       : "my-pipeline",
      stages     : stages.collect { [type: it] }
    ]
    mapper.writeValueAsString(config)
  }

  @CompileStatic
  static class AutowiredTestStage extends LinearStage {

    private final List<Task> tasks = []

    AutowiredTestStage(String name, Task... tasks) {
      super(name)
      this.tasks.addAll tasks
    }

    @Override
    public List<Step> buildSteps(Stage stage) {
      def i = 1
      tasks.collect { Task task ->
        buildStep(stage, "task${i++}", task)
      }
    }
  }
}
