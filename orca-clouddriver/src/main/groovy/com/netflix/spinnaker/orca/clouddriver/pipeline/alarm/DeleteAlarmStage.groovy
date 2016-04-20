package com.netflix.spinnaker.orca.clouddriver.pipeline.alarm

import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Component
class DeleteAlarmStage extends LinearStage {

  public static final String PIPELINE_CONFIG_TYPE = "deleteAlarm"

  DeleteAlarmStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    [
      buildStep(stage, "deleteAlarm", DeleteAlarmTask),
      buildStep(stage, "monitorUpsert", MonitorKatoTask),
    ]
  }
}
