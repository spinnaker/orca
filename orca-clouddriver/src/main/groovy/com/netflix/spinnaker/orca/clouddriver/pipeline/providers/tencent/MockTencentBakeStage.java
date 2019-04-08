package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.tencent;

import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MockTencentBakeStage implements StageDefinitionBuilder {

  public static final String PIPELINE_CONFIG_TYPE = "mocktencentbake";

  @Override
  public void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder.withTask("mockTencentCreateBake", MockTencentCreateBakeTask.class);
  }
}
