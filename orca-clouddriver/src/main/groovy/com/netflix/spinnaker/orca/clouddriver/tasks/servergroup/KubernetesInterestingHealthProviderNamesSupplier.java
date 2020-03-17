/*
 * Copyright 2017 Armory, Inc.
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
package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class KubernetesInterestingHealthProviderNamesSupplier
    implements InterestingHealthProviderNamesSupplier {

  private static final String KUBERNETES = "kubernetes";

  private static final List<String> SUPPORTED_STAGES = Arrays.asList("disablecluster");

  public boolean supports(String cloudProvider, StageExecution stage) {
    if (!cloudProvider.equals(KUBERNETES)) {
      return false;
    }

    return SUPPORTED_STAGES.contains(stage.getType().toLowerCase());
  }

  public List<String> process(String cloudProvider, StageExecution stage) {
    return Arrays.asList("KubernetesService");
  }
}
