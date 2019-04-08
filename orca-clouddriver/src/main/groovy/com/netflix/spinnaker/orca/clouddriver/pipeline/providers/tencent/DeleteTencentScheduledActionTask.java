package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.tencent;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.kato.pipeline.support.AsgDescriptionSupport;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class DeleteTencentScheduledActionTask implements Task {

  @Autowired private KatoService kato;

  @Override
  public TaskResult execute(Stage stage) {
    TaskId taskId =
        kato.requestOperations(
                new ArrayList<Map<String, Map>>() {
                  {
                    add(
                        new HashMap() {
                          {
                            put("deleteTencentScheduledActionDescription", stage.getContext());
                          }
                        });
                  }
                })
            .toBlocking()
            .first();

    Map deployServerGroups =
        AsgDescriptionSupport.convertAsgsToDeploymentTargets(
            (List<Map<String, String>>) stage.getContext().get("asgs"));

    return TaskResult.builder(ExecutionStatus.SUCCEEDED)
        .context("notification.type", "deletetencentscheduledaction")
        .context("deploy.server.groups", deployServerGroups)
        .context("kato.last.task.id", taskId)
        .build();
  }
}
