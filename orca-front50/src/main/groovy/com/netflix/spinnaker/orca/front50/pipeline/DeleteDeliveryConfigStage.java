package com.netflix.spinnaker.orca.front50.pipeline;

import com.netflix.spinnaker.orca.api.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.StageExecution;
import com.netflix.spinnaker.orca.api.TaskNode;
import com.netflix.spinnaker.orca.front50.tasks.DeleteDeliveryConfigTask;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Component
public class DeleteDeliveryConfigStage implements StageDefinitionBuilder {
  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    builder.withTask("deleteDeliveryConfig", DeleteDeliveryConfigTask.class);
  }
}
