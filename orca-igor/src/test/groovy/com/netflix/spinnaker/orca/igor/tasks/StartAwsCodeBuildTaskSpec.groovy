/*
 * Copyright 2020 Amazon.com, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.igor.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.igor.IgorService
import com.netflix.spinnaker.orca.igor.model.AwsCodeBuildExecution
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification

class StartAwsCodeBuildTaskSpec extends Specification {
  def ACCOUNT = "codebuild-account"
  def PROJECT_NAME = "test"

  Execution execution = Mock(Execution)

  def "should start a build"() {
    given:
    def igorResponse = AwsCodeBuildExecution.builder().arn("arn").build()
    def stage = new Stage(execution, "awsCodeBuild", [account: ACCOUNT, projectName: PROJECT_NAME])

    def igorService = Mock(IgorService) {
      startAwsCodeBuild(stage.context.account as String, _ as Map<String, Object>) >> igorResponse
    }
    StartAwsCodeBuildTask task = new StartAwsCodeBuildTask(igorService)

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * igorService.startAwsCodeBuild(ACCOUNT, _) >> igorResponse
    result.status == ExecutionStatus.SUCCEEDED
    result.context.buildArn == igorResponse.arn
  }
}
