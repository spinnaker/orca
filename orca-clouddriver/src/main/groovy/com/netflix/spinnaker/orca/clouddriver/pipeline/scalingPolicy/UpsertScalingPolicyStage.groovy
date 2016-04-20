package com.netflix.spinnaker.orca.clouddriver.pipeline.scalingPolicy

import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Component
class UpsertScalingPolicyStage extends LinearStage {

  public static final String PIPELINE_CONFIG_TYPE = "upsertScalingPolicy"

  UpsertScalingPolicyStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    [
      buildStep(stage, "upsertScalingPolicy", UpsertScalingPolicyTask),
      buildStep(stage, "monitorUpsert", MonitorKatoTask),
      buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask),
      buildStep(stage, "waitForUpsertedScalingPolicy", WaitFor),
    ]
  }
}

