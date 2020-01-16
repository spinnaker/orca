package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.tencent;

import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.stereotype.Component;

@Component
public class DeleteTencentScheduledActionStage implements StageDefinitionBuilder {
  @Override
  public void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder
        .withTask("deleteTencentScheduledAction", DeleteTencentScheduledActionTask.class)
        .withTask("monitorUpsert", MonitorKatoTask.class)
        .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask.class);
  }
}
