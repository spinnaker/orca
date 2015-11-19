/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.batch.lifecycle

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.ExecutionPropagationListener
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import static com.netflix.spinnaker.orca.batch.PipelineInitializerTasklet.initializationStep

class ExecutionCancellationSpec extends AbstractBatchLifecycleSpec {
  def startTask = Mock(Task)
  def endTask = Mock(Task)
  def detailsTask = Stub(Task) {
    execute(_) >> { new DefaultTaskResult(ExecutionStatus.SUCCEEDED ) }
  }

  void "should cancel a pipeline and not invoke subsequent tasks"() {
    given:
    startTask.execute(_) >> {
      pipeline.canceled = true
      executionRepository.store(pipeline)
      new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
    }

    when:
    def jobExecution = launchJob()

    then:
    0 * endTask.execute(_)

    and:
    jobExecution.exitStatus == ExitStatus.STOPPED

    when:
    pipeline = executionRepository.retrievePipeline(pipeline.id)

    then:
    pipeline.status == ExecutionStatus.CANCELED
    pipeline.stages[0].status == ExecutionStatus.CANCELED
  }

  @Override
  Pipeline createPipeline() {
    Pipeline.builder().withStage("cancel").build()
  }

  @Override
  protected Job configureJob(JobBuilder jobBuilder) {
    def stage = pipeline.namedStage("cancel")
    def builder = jobBuilder
      .listener(new ExecutionPropagationListener(executionRepository, true, true))
      .flow(initializationStep(steps, pipeline))
    def stageBuilder = new CancellationStageBuilder(
      steps: steps,
      taskTaskletAdapter: new TaskTaskletAdapter(executionRepository, [])
    )
    stageBuilder.applicationContext = applicationContext
    stageBuilder.build(builder, stage).build().build()
  }

  class CancellationStageBuilder extends LinearStage {

    CancellationStageBuilder() {
      super("cancel")
    }

    @Override
    public List<Step> buildSteps(Stage stage) {
      def step1 = buildStep(stage, "startTask", startTask)
      def step2 = buildStep(stage, "endTask", endTask)
      [step1, step2]
    }

    protected Step buildStep(Stage stage, String taskName, Class task) {
      buildStep(stage, taskName, detailsTask)
    }
  }
}
