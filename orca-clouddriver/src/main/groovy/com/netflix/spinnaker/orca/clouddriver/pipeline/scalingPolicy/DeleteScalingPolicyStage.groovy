package com.netflix.spinnaker.orca.clouddriver.pipeline.scalingPolicy

import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Component
class DeleteScalingPolicyStage extends LinearStage {

  public static final String PIPELINE_CONFIG_TYPE = "deleteScalingPolicy"

  DeleteScalingPolicyStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    [
      buildStep(stage, "deleteScalingPolicy", DeleteScalingPolicyTask),
      buildStep(stage, "monitorUpsert", MonitorKatoTask),
    ]
  }
}

