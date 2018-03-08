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

package com.netflix.spinnaker.orca.clouddriver.pipeline.cluster;

import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractClusterWideClouddriverTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractWaitForClusterWideClouddriverTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.ShrinkClusterTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.WaitForClusterShrinkTask;
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE;

@Component
public class ShrinkClusterStage extends AbstractClusterWideClouddriverOperationStage {
  @Autowired
  DisableClusterStage disableClusterStage;

  @Override
  public Class<? extends AbstractClusterWideClouddriverTask> getClusterOperationTask() {
    return ShrinkClusterTask.class;
  }

  @Override
  public Class<? extends AbstractWaitForClusterWideClouddriverTask> getWaitForTask() {
    return WaitForClusterShrinkTask.class;
  }

  @Override
  public void beforeStages(
    @Nonnull Stage parent,
    @Nonnull StageGraphBuilder builder
  ) {
    if (parent.getContext().get("allowDeleteActive").equals(true)) {
      Map<String, Object> context = new HashMap<>(parent.getContext());
      context.put("remainingEnabledServerGroups", parent.getContext().get("shrinkToSize"));
      context.put("preferLargerOverNewer", parent.getContext().get("retainLargerOverNewer"));
      context.put("continueIfClusterNotFound", parent.getContext().get("shrinkToSize").equals(0));

      // We don't want the key propagated if interestingHealthProviderNames isn't defined, since this prevents
      // health providers from the stage's 'determineHealthProviders' task to be added to the context.
      if (parent.getContext().get("interestingHealthProviderNames") != null) {
        context.put("interestingHealthProviderNames", parent.getContext().get("interestingHealthProviderNames"));
      }

      builder.add((it) -> {
        it.setType(disableClusterStage.getType());
        it.setName("disableCluster");
        it.setContext(context);
        it.setSyntheticStageOwner(STAGE_BEFORE);
      });
    }
  }
}
