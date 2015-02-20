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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.batch.StageStatusPropagationListener
import com.netflix.spinnaker.orca.batch.StageTaskPropagationListener
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus


import static com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.batch.lifecycle.AbstractBatchLifecycleSpec
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.job.builder.JobBuilder


import static com.netflix.spinnaker.orca.batch.PipelineInitializerTasklet.initializationStep

class ParallelStageSpec extends AbstractBatchLifecycleSpec {
  static {
    System.setProperty("multiThread", "true")
  }

  def task = new ResultRecordingCallbackTask()

  void "should run stages in parallel"() {
    def sleeps = [5, 2]
    task.callback = {
      def wait = sleeps.remove(0)
      println "waiting: $wait"
      sleep(wait*1000)
      wait
      new DefaultTaskResult(SUCCEEDED)
    }
    taskExecutor.waitForTasksToCompleteOnShutdown = true
    taskExecutor.awaitTerminationSeconds = 60

    given:
    launchJob()
    taskExecutor.shutdown()

    expect:
    task.results == [2, 5]

  }

  void "should fail pipeline if one stage fails"() {
    def sleeps = [5, 2]
    task.callback = {
      def wait = sleeps.remove(0)
      println "waiting: $wait"
      sleep(wait*1000)
      new DefaultTaskResult(sleeps ? SUCCEEDED : TERMINAL)
    }
    taskExecutor.waitForTasksToCompleteOnShutdown = true
    taskExecutor.awaitTerminationSeconds = 60

    given:
    def jobExecution = launchJob()
    taskExecutor.shutdown()

    expect:
    jobExecution.exitStatus == ExitStatus.STOPPED
    executionRepository.retrievePipeline(pipeline.id).status == TERMINAL
  }

  def listeners = [new StageStatusPropagationListener(executionRepository),
                   new StageTaskPropagationListener(executionRepository)]

  @Override
  Pipeline createPipeline() {
    Pipeline.builder().withStage("stage2", "parallel", [stages:[[type:"test"],[type:"test"]]]).build()
  }

  @Override
  protected Job configureJob(JobBuilder jobBuilder) {
    def stage = pipeline.namedStage("stage2")
    def builder = jobBuilder.flow(initializationStep(steps, pipeline))
    def stageBuilder = new TestStageBuilder(steps, new TaskTaskletAdapter(executionRepository, []))
    def parallelStage = new ParallelStage(stageBuilders: [stageBuilder])
    parallelStage.steps = steps
    parallelStage.taskTaskletAdapter = new TaskTaskletAdapter(executionRepository, [])
    parallelStage.taskListeners = listeners
    parallelStage.build(builder, stage).build().build()
  }

  static class ResultRecordingCallbackTask implements Task {
    Closure callback
    def results = []

    @Override
    TaskResult execute(Stage stage) {
      def result = callback.call()
      results << results
      result
    }
  }

  class TestStageBuilder extends LinearStage {

    TestStageBuilder(StepBuilderFactory steps, TaskTaskletAdapter adapter) {
      super("test")
      setSteps(steps)
      setTaskTaskletAdapter(adapter)
      setTaskListeners(listeners)
    }

    @Override
    protected List<Step> buildSteps(Stage stage) {
      [
        buildStep(stage, "task1", task)
      ]
    }
  }

}
