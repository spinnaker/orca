package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws;

import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.updateLambdaTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Component
public class UpdateLambdaStage implements StageDefinitionBuilder {
  public static final String PIPELINE_CONFIG_TYPE = "UpdateLambda";

  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder.withTask(updateLambdaTask.TASK_NAME, updateLambdaTask.class);
  }
}
