package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.tencent

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.kato.pipeline.support.AsgDescriptionSupport
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DeleteTencentScheduledActionTask implements Task {

  @Autowired
  KatoService kato

  @Override
  TaskResult execute(Stage stage) {
    def taskId = kato.requestOperations([[deleteTencentScheduledActionDescription: stage.context]])
        .toBlocking()
        .first()

    def deployServerGroups = AsgDescriptionSupport.convertAsgsToDeploymentTargets(stage.context.asgs)

    new TaskResult(ExecutionStatus.SUCCEEDED, [
        "notification.type"     : "deletetencentscheduledaction",
        "deploy.server.groups"  : deployServerGroups,
        "kato.last.task.id"     : taskId,
    ])
  }
}
