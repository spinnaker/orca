/*
 * Copyright 2015 Netflix, Inc.
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


package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.rollback.ExplicitRollback
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.rollback.PreviousImageRollback
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.rollback.Rollback
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.rollback.RollbackCluster
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.rollback.TestRollback
import com.netflix.spinnaker.orca.clouddriver.utils.LockNameHelper
import com.netflix.spinnaker.orca.locks.LockContext
import com.netflix.spinnaker.orca.locks.LockableStageSupport
import com.netflix.spinnaker.orca.locks.LockingConfigurationProperties
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.stereotype.Component

@Component
class RollbackServerGroupStage implements StageDefinitionBuilder {
  public static final String PIPELINE_CONFIG_TYPE = "rollbackServerGroup"

  @Autowired
  AutowireCapableBeanFactory autowireCapableBeanFactory

  @Autowired
  LockingConfigurationProperties lockingConfiguration

  @Override
  List<Stage> aroundStages(Stage stage) {
    def stageData = stage.mapTo(StageData)

    if (!stageData.rollbackType) {
      throw new IllegalStateException("Missing `rollbackType` (execution: ${stage.execution.id})")
    }

    def rollback = stage.mapTo("/rollbackContext", stageData.rollbackType.implementationClass) as Rollback
    autowireCapableBeanFactory.autowireBean(rollback)
    List<Stage> stages = []
    int insertionIndex = 0
    if (LockableStageSupport.useLocking(stage, lockingConfiguration)) {
      RollbackCluster rollbackCluster = rollback.getRollbackCluster(stage)
      if (rollbackCluster) {
        insertionIndex = 1
        String lockName = LockNameHelper.buildClusterLockName(rollbackCluster.cloudProvider, rollbackCluster.credentials, rollbackCluster.cluster, rollbackCluster.region)
        LockContext lockContext = LockableStageSupport.buildLockContext(stage, lockName)
        stages.add(LockableStageSupport.buildAcquireLockStage(stage, lockContext))
        stages.add(LockableStageSupport.buildReleaseLockStage(stage, lockContext))
      }
    }
    stages.addAll(insertionIndex, rollback.buildStages(stage))
    return stages
  }

  static enum RollbackType {
    EXPLICIT(ExplicitRollback),
    PREVIOUS_IMAGE(PreviousImageRollback),
    TEST(TestRollback)

    final Class implementationClass

    RollbackType(Class<? extends Rollback> implementationClass) {
      this.implementationClass = implementationClass
    }
  }

  static class StageData {
    RollbackType rollbackType
  }
}
