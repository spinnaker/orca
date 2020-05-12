/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.titus;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.ApplySourceServerGroupCapacityStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.CaptureSourceServerGroupCapacityTask;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.DeployStagePreProcessor;
import com.netflix.spinnaker.orca.kato.pipeline.strategy.Strategy;
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class TitusDeployStagePreProcessor implements DeployStagePreProcessor {

  private static final List<Strategy> ROLLING_STRATEGIES =
      Arrays.asList(Strategy.ROLLING_RED_BLACK, Strategy.MONITORED);

  private final ApplySourceServerGroupCapacityStage applySourceServerGroupSnapshotStage;

  public TitusDeployStagePreProcessor(
      ApplySourceServerGroupCapacityStage applySourceServerGroupSnapshotStage) {
    this.applySourceServerGroupSnapshotStage = applySourceServerGroupSnapshotStage;
  }

  @Override
  public boolean supports(StageExecution stage) {
    StageData stageData = stage.mapTo(StageData.class);
    return "titus".equals(stageData.getCloudProvider());
  }

  @Override
  public List<StepDefinition> additionalSteps(StageExecution stage) {
    StageData stageData = stage.mapTo(StageData.class);
    Strategy strategy = Strategy.fromStrategyKey(stageData.getStrategy());

    if (ROLLING_STRATEGIES.contains(strategy)) {
      return Collections.emptyList();
    }

    return Collections.singletonList(
        new StepDefinition(
            "snapshotSourceServerGroup", CaptureSourceServerGroupCapacityTask.class));
  }

  @Override
  public List<StageDefinition> afterStageDefinitions(StageExecution stage) {
    StageData stageData = stage.mapTo(StageData.class);

    Strategy strategy = Strategy.fromStrategyKey(stageData.getStrategy());
    if (!ROLLING_STRATEGIES.contains(strategy)) {
      StageDefinition restoreMinCap = new StageDefinition();
      restoreMinCap.name = "restoreMinCapacityFromSnapshot";
      restoreMinCap.stageDefinitionBuilder = applySourceServerGroupSnapshotStage;
      restoreMinCap.context = new HashMap<String, Object>();
      return Collections.singletonList(restoreMinCap);
    }

    return Collections.emptyList();
  }
}
