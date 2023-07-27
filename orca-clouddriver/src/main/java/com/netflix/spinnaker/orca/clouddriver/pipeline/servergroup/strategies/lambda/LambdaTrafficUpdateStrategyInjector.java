/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.lambda;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LambdaTrafficUpdateStrategyInjector {

  private final LambdaSimpleDeploymentStrategy simpleStrat;
  private final LambdaWeightedDeploymentStrategy weightedStrat;
  private final LambdaBlueGreenDeploymentStrategy blueGreenStrat;

  private final Map<LambdaDeploymentStrategyEnum, BaseLambdaDeploymentStrategy> factoryMap =
      new HashMap<>();

  public LambdaTrafficUpdateStrategyInjector(
      LambdaSimpleDeploymentStrategy simpleStrat,
      LambdaWeightedDeploymentStrategy weightedStrat,
      LambdaBlueGreenDeploymentStrategy blueGreenStrat) {
    this.simpleStrat = simpleStrat;
    this.weightedStrat = weightedStrat;
    this.blueGreenStrat = blueGreenStrat;
  }

  public BaseLambdaDeploymentStrategy getStrategy(LambdaDeploymentStrategyEnum inp) {
    return factoryMap.get(inp);
  }

  @PostConstruct
  private void injectEnum() {
    factoryMap.put(LambdaDeploymentStrategyEnum.$BLUEGREEN, blueGreenStrat);
    factoryMap.put(LambdaDeploymentStrategyEnum.$WEIGHTED, weightedStrat);
    factoryMap.put(LambdaDeploymentStrategyEnum.$SIMPLE, simpleStrat);
  }
}
