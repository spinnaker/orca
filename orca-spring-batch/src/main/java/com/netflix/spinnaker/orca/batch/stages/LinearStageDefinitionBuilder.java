/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.batch.stages;

import com.netflix.spinnaker.orca.batch.StageBuilder;
import com.netflix.spinnaker.orca.batch.StageBuilderProvider;
import com.netflix.spinnaker.orca.pipeline.LinearStage;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.batch.core.Step;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LinearStageDefinitionBuilder extends LinearStage {
  private final StageDefinitionBuilder delegate;
  private final StageBuilderProvider stageBuilderProvider;

  public LinearStageDefinitionBuilder(StageDefinitionBuilder delegate,
                                      StageBuilderProvider stageBuilderProvider) {
    super(delegate.getType());
    this.delegate = delegate;
    this.stageBuilderProvider = stageBuilderProvider;
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    Map<String, List<StageBuilder>> stageBuilders = stageBuilderProvider
      .all()
      .stream()
      .collect(Collectors.groupingBy(StageBuilder::getType));

    Collection<Stage> aroundStages = delegate.aroundStages(stage);
    aroundStages.forEach(s -> {
      switch(s.getSyntheticStageOwner()) {
        case STAGE_BEFORE:
          injectBefore(stage, s.getName(), stageBuilders.get(s.getType()).get(0), s.getContext());
          break;

        case STAGE_AFTER:
          injectAfter(stage, s.getName(), stageBuilders.get(s.getType()).get(0), s.getContext());
          break;
      }
    });

    return delegate
      .taskGraph((Stage<Execution>) stage)
      .stream()
      .map(taskDefinition -> buildStep(stage, taskDefinition.getName(), taskDefinition.getImplementingClass()))
      .collect(Collectors.toList());
  }

  @Override
  public Stage prepareStageForRestart(Stage stage) {
    return delegate.prepareStageForRestart(stage);
  }
}
