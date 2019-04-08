package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.tencent;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
@Data
public class MockTencentCreateBakeTask implements RetryableTask {

  private long backoffPeriod = 30000;
  private long timeout = 300000;

  @Override
  public TaskResult execute(Stage stage) {
    Map<String, String> deploymentDetail = new HashMap<>();
    deploymentDetail.put("qimageId", "img-oikl1tzv");
    deploymentDetail.put("ami", "img-oikl1tzv");
    deploymentDetail.put("region", "ap-guangzhou");
    deploymentDetail.put("cloudProvider", "tencent");

    // new TaskResult(ExecutionStatus.SUCCEEDED, [:], globalContext)
    return TaskResult.builder(ExecutionStatus.SUCCEEDED)
        .context("deploymentDetail", deploymentDetail)
        .build();
  }
}
