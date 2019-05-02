/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.kubernetes;

import com.netflix.spinnaker.orca.clouddriver.tasks.job.JobRunner;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Data
public class KubernetesJobRunner implements JobRunner {
  public boolean katoResultExpected = false;
  public String cloudProvider = "kubernetes";

  public ArtifactResolver artifactResolver;

  public KubernetesJobRunner(ArtifactResolver artifactResolver) {
    this.artifactResolver = artifactResolver;
  }

  public List<Map> getOperations(Stage stage) {
    Map<String, Object> operation = new HashMap<>();

    if (stage.getContext().containsKey("cluster")) {
      operation.putAll((Map) stage.getContext().get("cluster"));
    } else {
      operation.putAll(stage.getContext());
    }

    KubernetesContainerFinder.populateFromStage(operation, stage, artifactResolver);

    Map<String, Object> task = new HashMap<>();
    task.put(OPERATION, operation);
    return Collections.singletonList(task);
  }

  public Map<String, Object> getAdditionalOutputs(Stage stage, List<Map> operations) {
    return new HashMap<>();
  }
}
