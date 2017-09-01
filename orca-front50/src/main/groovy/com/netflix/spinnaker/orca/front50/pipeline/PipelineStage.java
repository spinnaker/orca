package com.netflix.spinnaker.orca.front50.pipeline;

import java.util.List;
import com.netflix.spinnaker.orca.CancellableStage;
import com.netflix.spinnaker.orca.RestartableStage;
import com.netflix.spinnaker.orca.front50.tasks.MonitorPipelineTask;
import com.netflix.spinnaker.orca.front50.tasks.StartPipelineTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.Task;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED;
import static java.lang.String.format;
import static java.util.Collections.emptyMap;

@Component
public class PipelineStage implements StageDefinitionBuilder, RestartableStage, CancellableStage {

  private final Logger log = LoggerFactory.getLogger(getClass());

  public static final String PIPELINE_CONFIG_TYPE = StageDefinitionBuilder.getType(PipelineStage.class);

  @Autowired
  ExecutionRepository executionRepository;

  @Override
  public <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
    builder
      .withTask("startPipeline", StartPipelineTask.class);

    if (!stage.getContext().getOrDefault("waitForCompletion", "true").toString().toLowerCase().equals("false")) {
      builder.withTask("monitorPipeline", MonitorPipelineTask.class);
  }
  }

  @Override
  public void prepareStageForRestart(Stage stage) {
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
  }

  @Override
  public CancellableStage.Result cancel(Stage stage) {
    log.info(format("Cancelling stage (stageId: %s, executionId: %s, context: %s)", stage.getId(), stage.getExecution().getId(), stage.getContext()));

    try {
      String executionId = (String) stage.getContext().get("executionId");
      if (executionId != null) {
        // flag the child pipeline as canceled (actual cancellation will happen asynchronously)
        executionRepository.cancel(executionId, "parent pipeline", null);
      }
    } catch (Exception e) {
      log.error("Failed to cancel stage (stageId: ${stage.id}, executionId: ${stage.execution.id}), e: ${e.message}", e);
    }

    return new CancellableStage.Result(stage, emptyMap());
  }
}
