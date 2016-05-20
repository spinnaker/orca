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

package com.netflix.spinnaker.orca.clouddriver.pipeline.cluster

import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractClusterWideClouddriverTask
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractWaitForClusterWideClouddriverTask
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.ShrinkClusterTask
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.WaitForClusterShrinkTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class ShrinkClusterStage extends AbstractClusterWideClouddriverOperationStage {
  @Autowired
  DisableClusterStage disableClusterStage

  @Override
  Class<? extends AbstractClusterWideClouddriverTask> getClusterOperationTask() {
    ShrinkClusterTask
  }

  @Override
  Class<? extends AbstractWaitForClusterWideClouddriverTask> getWaitForTask() {
    WaitForClusterShrinkTask
  }

  @Override
  def <T extends Execution> List<Stage<T>> aroundStages(Stage<T> parentStage) {
    if (parentStage.context.allowDeleteActive == true) {
      //TODO(cfieber) Remvove the stage.context.cloudProvider check once proper discovery has been added to titus
      if (!parentStage.context.cloudProvider || parentStage.context.cloudProvider != 'titan') {
        def context = parentStage.context + [
          remainingEnabledServerGroups: parentStage.context.shrinkToSize,
          preferLargerOverNewer       : parentStage.context.retainLargerOverNewer,
          continueIfClusterNotFound   : parentStage.context.shrinkToSize == 0
        ]
        return [
          StageDefinitionBuilder.StageDefinitionBuilderSupport.newStage(
            parentStage.execution, disableClusterStage.type, "disableCluster", context, parentStage, Stage.SyntheticStageOwner.STAGE_BEFORE
          )
        ]
      }
    }
    return []
  }
}
