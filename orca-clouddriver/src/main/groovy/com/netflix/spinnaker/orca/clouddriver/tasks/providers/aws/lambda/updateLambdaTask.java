package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class updateLambdaTask extends AbstractCloudProviderAwareTask implements Task {

  @Autowired
  KatoService katoService;

  public static final String TASK_NAME = "updateLambdaFunctionConfiguration";

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    String cloudProvider = getCloudProvider(stage);

    Map<String, Object> task = new HashMap<>(stage.getContext());

    Map<String, Map> operation =
      new ImmutableMap.Builder<String, Map>().put(TASK_NAME, task).build();

    TaskId taskId =
      katoService
        .requestOperations(cloudProvider, Collections.singletonList(operation))
        .toBlocking()
        .first();

    Map<String, Object> context =
      new ImmutableMap.Builder<String, Object>()
        .put("kato.result.expected", true)
        .put("kato.last.task.id", taskId)
        .build();

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(context).build();
  }

}
