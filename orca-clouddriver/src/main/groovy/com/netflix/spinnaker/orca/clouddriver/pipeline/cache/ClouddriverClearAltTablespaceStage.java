package com.netflix.spinnaker.orca.clouddriver.pipeline.cache;

import com.netflix.spinnaker.orca.clouddriver.tasks.cache.ClouddriverClearAltTablespaceTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class ClouddriverClearAltTablespaceStage implements StageDefinitionBuilder {

  @Override
  public void taskGraph(@NotNull StageExecutionImpl stage, @NotNull TaskNode.Builder builder) {
    builder.withTask("clouddriverClearAltTablespace", ClouddriverClearAltTablespaceTask.class);
  }
}
