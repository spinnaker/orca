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
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.titus;

import com.google.common.base.Strings;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.job.JobRunner;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class TitusJobRunner implements JobRunner {

  private static final List<String> DEFAULT_SECURITY_GROUPS =
      Arrays.asList("nf-infrastructure", "nf-datacenter");

  @Override
  public List<Map> getOperations(StageExecution stage) {
    Map<String, Object> operation = new HashMap<>(stage.getContext());

    if (stage.getContext().containsKey("cluster")) {
      operation.putAll((Map<String, Object>) stage.getContext().get("cluster"));
    }
    operation.put("cloudProvider", getCloudProvider());

    Optional.ofNullable(stage.getExecution().getAuthentication())
        .map(PipelineExecution.AuthenticationDetails::getUser)
        .ifPresent(u -> operation.put("user", u));

    operation.put("securityGroups", operation.getOrDefault("securityGroups", new ArrayList<>()));

    addAllNonEmpty((List<String>) operation.get("securityGroups"), DEFAULT_SECURITY_GROUPS);

    return null;
  }

  @Override
  public Map<String, Object> getAdditionalOutputs(StageExecution stage, List<Map> operations) {
    return Optional.ofNullable(stage.getContext().get("cluster"))
        .map(it -> ((Map<String, Object>) it).get("application"))
        .map(app -> Collections.singletonMap("application", app))
        .orElseGet(HashMap::new);
  }

  @Override
  public boolean isKatoResultExpected() {
    return false;
  }

  @Override
  public String getCloudProvider() {
    return "titus";
  }

  private static void addAllNonEmpty(List<String> baseList, List<String> listToBeAdded) {
    if (listToBeAdded != null && !listToBeAdded.isEmpty()) {
      listToBeAdded.forEach(
          item -> {
            if (!Strings.isNullOrEmpty(item)) {
              baseList.add(item);
            }
          });
    }
  }
}
