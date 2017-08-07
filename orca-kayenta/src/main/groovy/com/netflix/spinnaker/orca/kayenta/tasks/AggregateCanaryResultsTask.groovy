/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.orca.kayenta.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kayenta.pipeline.RunCanaryPipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Slf4j
@Component
class AggregateCanaryResultsTask implements Task {

  @Override
  TaskResult execute(@Nonnull Stage stage) {
    String combinedCanaryResultStrategy = stage.context.canaryConfig.combinedCanaryResultStrategy
    List<Stage> runCanaryStages = stage.execution.stages.findAll { it.type == RunCanaryPipelineStage.STAGE_TYPE }
    List<Double> runCanaryScores = runCanaryStages.collect { (Double)it.context.canaryScore }
    // Took this value from mine.
    Double overallScore = -99

    // TODO(duftler): This is wrong. The combinedCanaryResultStrategy is not supposed to be used to combine overall
    // canary run scores. Remove this logic once we decide how we should actually combine them. Will probably end up
    // taking the latest score.
    if (runCanaryScores) {
      if (combinedCanaryResultStrategy == "AVERAGE") {
        overallScore = runCanaryScores.sum() / runCanaryScores.size()
      } else /* LOWEST */ {
        overallScore = runCanaryScores.min()
      }
    }

    // TODO(duftler): Consider score in context of specified thresholds.
    return new TaskResult(ExecutionStatus.SUCCEEDED, [canaryScores: runCanaryScores, overallScore: overallScore])
  }
}
