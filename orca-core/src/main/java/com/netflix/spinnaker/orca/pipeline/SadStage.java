package com.netflix.spinnaker.orca.pipeline;

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.tasks.SadNoOpTask;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class SadStage implements StageDefinitionBuilder {
  public static String STAGE_TYPE = "sad";

  @Override
  public void taskGraph(@NotNull StageExecution stage, @NotNull TaskNode.Builder builder) {
    builder.withTask("sadNoOp", SadNoOpTask.class);
  }
}
