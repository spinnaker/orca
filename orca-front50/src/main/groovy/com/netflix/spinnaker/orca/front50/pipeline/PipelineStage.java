package com.netflix.spinnaker.orca.front50.pipeline;

import java.util.List;
import com.netflix.spinnaker.orca.batch.RestartableStage;
import com.netflix.spinnaker.orca.front50.tasks.MonitorPipelineTask;
import com.netflix.spinnaker.orca.front50.tasks.StartPipelineTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.Task;
import org.springframework.stereotype.Component;
import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED;

@Component
public class PipelineStage implements StageDefinitionBuilder, RestartableStage {

  public static final String PIPELINE_CONFIG_TYPE = StageDefinitionBuilderSupport.getType(PipelineStage.class);

  @Override
  public <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
    builder
      .withTask("startPipeline", StartPipelineTask.class)
      .withTask("monitorPipeline", MonitorPipelineTask.class);
  }

  @Override public Stage prepareStageForRestart(Stage stage) {
    stage = StageDefinitionBuilderSupport.prepareStageForRestart(stage);
    stage.setStartTime(null);
    stage.setEndTime(null);

    stage.getContext().remove("status");
    stage.getContext().remove("executionName");
    stage.getContext().remove("executionId");

    List<Task> tasks = stage.getTasks();
    tasks.forEach(task -> {
      task.setStartTime(null);
      task.setEndTime(null);
      task.setStatus(NOT_STARTED);
    });

    return stage;
  }
}
