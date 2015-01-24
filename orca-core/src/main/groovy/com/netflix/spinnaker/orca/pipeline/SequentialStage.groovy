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
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class SequentialStage extends LinearStage {
  static final String MAYO_CONFIG_TYPE = "sequential"

  @Autowired(required = false)
  List<LinearStage> stageBuilders

  SequentialStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  protected List<Step> buildSteps(Stage stage) {
    def stageData = stage.mapTo(SequentialStageData)
    stageData.stages.eachWithIndex { Map<String, Object> config, int i ->
      def type = config.remove("type") as String
      def stageBuilder = findBuilder(type)
      injectAfter(stage, config.name as String, stageBuilder, config)
    }
    [buildStep(stage, "finishSequential", { new DefaultTaskResult(ExecutionStatus.SUCCEEDED) })]
  }

  private LinearStage findBuilder(String type) {
    for (stageBuilder in stageBuilders) {
      if (type == stageBuilder.type) {
        return stageBuilder
      }
    }
    throw new RuntimeException("No stage builder found for [${type}]")
  }

  static class SequentialStageData {
    List<Map<String, Object>> stages
  }

}
