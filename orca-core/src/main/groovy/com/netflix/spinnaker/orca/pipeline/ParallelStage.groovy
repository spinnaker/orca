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
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.FlowBuilder
import org.springframework.batch.core.job.builder.JobFlowBuilder
import org.springframework.batch.core.job.flow.Flow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.stereotype.Component

@Component
class ParallelStage extends StageBuilder {
  static final String MAYO_CONFIG_TYPE = "parallel"

  @Autowired
  List<LinearStage> stageBuilders

  ParallelStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  JobFlowBuilder build(JobFlowBuilder jobBuilder, Stage stage) {
    def stageData = stage.mapTo(ParallelStageData)
    List<Flow> flows = []
    for (config in stageData.stages) {
      def stageBuilder = findBuilder(config.type as String)
      def nextContext = new HashMap(config)
      def nextStage = newStage(stage.execution, config.type as String, config.name as String, nextContext, stage, null)
      stage.execution.stages.add(nextStage)
      def flowBuilder = new FlowBuilder<Flow>(config.name as String)
      stageBuilder.buildSteps(nextStage).eachWithIndex { Step entry, int i ->
        if (i == 0) {
          flowBuilder.from(entry)
        } else {
          flowBuilder.next(entry)
        }
      }
      flows << flowBuilder.end()
    }
    jobBuilder
      .split(new SimpleAsyncTaskExecutor())
      .add(flows as Flow[])
      .next(buildStep(stage, "finishParallel", new NoopTask()))
  }

  private LinearStage findBuilder(String type) {
    for (stageBuilder in stageBuilders) {
      if (type == stageBuilder.type) {
        return stageBuilder
      }
    }
    throw new RuntimeException("No stage builder found for [${type}]")
  }

  static class ParallelStageData {
    List<Map<String, Object>> stages
  }

  static class NoopTask implements Task {
    TaskResult execute(Stage stage) {
      new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
    }
  }
}
