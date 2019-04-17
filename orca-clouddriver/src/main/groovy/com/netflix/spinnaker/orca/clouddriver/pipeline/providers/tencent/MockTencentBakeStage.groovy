package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.tencent

import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component


@Slf4j
@Component
@CompileStatic
class MockTencentBakeStage implements StageDefinitionBuilder {

  public static final String PIPELINE_CONFIG_TYPE = "mocktencentbake"

  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder
      .withTask("mockTencentCreateBake", MockTencentCreateBakeTask)
  }

}
