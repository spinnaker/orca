/*
 *
 *  * Copyright 2021 Amazon.com, Inc. or its affiliates.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License")
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package com.amazon.spinnaker.orca.aws.lambda.traffic;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TrafficUpdateStrategyInjector {
  private static Logger logger = LoggerFactory.getLogger(TrafficUpdateStrategyInjector.class);

  @Autowired private SimpleDeploymentStrategy simpleStrat;
  @Autowired private WeightedDeploymentStrategy weightedStrat;
  @Autowired private BlueGreenDeploymentStrategy blueGreenStrat;

  private Map<DeploymentStrategyEnum, BaseDeploymentStrategy> factoryMap = new HashMap<>();

  public TrafficUpdateStrategyInjector() {
    logger.debug("Start strategy injector");
  }

  public BaseDeploymentStrategy getStrategy(DeploymentStrategyEnum inp) {
    return factoryMap.get(inp);
  }

  @PostConstruct
  private void injectEnum() {
    factoryMap.put(DeploymentStrategyEnum.$BLUEGREEN, blueGreenStrat);
    factoryMap.put(DeploymentStrategyEnum.$WEIGHTED, weightedStrat);
    factoryMap.put(DeploymentStrategyEnum.$SIMPLE, simpleStrat);
  }
}
