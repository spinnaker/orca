/*
 *  Copyright 2019 Pivotal, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver;
import com.netflix.spinnaker.orca.clouddriver.tasks.job.JobRunner;
import groovy.util.logging.Slf4j;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Data
public class CloudFoundryJobRunner implements JobRunner {
  private final boolean katoResultExpected = false;
  private final String cloudProvider = "cloudfoundry";
  private final TargetServerGroupResolver resolver;

  @Override
  public List<Map> getOperations(StageExecution stage) {
    Map<String, Object> stageContext = stage.getContext();
    String targetServerGroup = (String) stageContext.get("target");
    String accountName = (String) stageContext.get("credentials");
    String region = (String) stageContext.get("region");

    List<TargetServerGroup> resolvedServerGroups = resolver.resolve(stage);
    checkArgument(
        resolvedServerGroups.size() > 0,
        "Could not find a target server group '%s' for account '%s' in region '%s'",
        targetServerGroup,
        accountName,
        region);
    checkState(
        resolvedServerGroups.size() == 1,
        "Found multiple target server groups '%s' for account '%s' in region '%s'",
        targetServerGroup,
        accountName,
        region);

    TargetServerGroup serverGroup = resolvedServerGroups.get(0);

    ImmutableMap.Builder<String, Object> operationContext =
        ImmutableMap.<String, Object>builder()
            .put("region", region)
            .put("credentials", accountName)
            .put("jobName", stageContext.get("jobName"))
            .put("serverGroupName", serverGroup.getName())
            .put("command", stageContext.get("command"))
            .put("moniker", serverGroup.getMoniker());

    return Collections.singletonList(
        ImmutableMap.<String, Object>builder().put(OPERATION, operationContext.build()).build());
  }

  @Override
  public Map<String, Object> getAdditionalOutputs(StageExecution stage, List<Map> operations) {
    return Collections.emptyMap();
  }
}
