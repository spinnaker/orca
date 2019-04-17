package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.tencent

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.stereotype.Component

@Component
@CompileStatic
class MockTencentCreateBakeTask implements RetryableTask {

  long backoffPeriod = 30000
  long timeout = 300000

  @Override
  TaskResult execute(Stage stage) {
    def deploymentDetails = [
      "imageId": "img-oikl1tzv",
      "ami": "img-oikl1tzv",
      "region": "ap-guangzhou",
      "cloudProvider": "tencent"
    ]

    def globalContext = [
      "deploymentDetails": [deploymentDetails]
    ]
    //new TaskResult(ExecutionStatus.SUCCEEDED, [:], globalContext)
    new TaskResult(ExecutionStatus.SUCCEEDED, globalContext, globalContext)
  }
}
