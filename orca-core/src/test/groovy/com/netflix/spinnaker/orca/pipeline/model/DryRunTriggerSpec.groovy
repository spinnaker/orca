/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.model

class DryRunTriggerSpec extends AbstractTriggerSpec<DryRunTrigger> {
  @Override
  protected Class<DryRunTrigger> getType() {
    DryRunTrigger
  }

  @Override
  protected String getTriggerJson() {
    '''
{
  "type": "dryrun",
  "lastSuccessfulExecution": {
    "type": "PIPELINE",
    "id": "848449c9-b152-4cd6-b22c-bd88d619df77",
    "application": "fletch_test",
    "name": "Chained 1",
    "buildTime": 1513102084081,
    "canceled": false,
    "canceledBy": null,
    "cancellationReason": null,
    "limitConcurrent": true,
    "keepWaitingPipelines": false,
    "stages": [
      {
        "id": "ea50d669-2a0f-4801-9f89-779a135e5693",
        "refId": "1",
        "type": "wait",
        "name": "Wait",
        "startTime": 1513102084163,
        "endTime": 1513102099340,
        "status": "SUCCEEDED",
        "context": {
          "waitTime": 1,
          "stageDetails": {
            "name": "Wait",
            "type": "wait",
            "startTime": 1513102084163,
            "isSynthetic": false,
            "endTime": 1513102099340
          },
          "waitTaskState": {}
        },
        "outputs": {},
        "tasks": [
          {
            "id": "1",
            "implementingClass": "com.netflix.spinnaker.orca.pipeline.tasks.WaitTask",
            "name": "wait",
            "startTime": 1513102084205,
            "endTime": 1513102099313,
            "status": "SUCCEEDED",
            "stageStart": true,
            "stageEnd": true,
            "loopStart": false,
            "loopEnd": false
          }
        ],
        "syntheticStageOwner": null,
        "parentStageId": null,
        "requisiteStageRefIds": [],
        "scheduledTime": null,
        "lastModified": null
      }
    ],
    "startTime": 1513102084130,
    "endTime": 1513102099392,
    "status": "SUCCEEDED",
    "authentication": {
      "user": "fzlem@netflix.com",
      "allowedAccounts": [
        "test",
        "prod"
      ]
    },
    "paused": null,
    "executionEngine": "v3",
    "origin": "deck",
    "trigger": {
      "type": "manual",
      "user": "fzlem@netflix.com",
      "parameters": {},
      "notifications": []
    },
    "description": null,
    "pipelineConfigId": "241a8418-8649-4f61-bbd1-128bedaef658",
    "notifications": [],
    "initialConfig": {}
  }
}
'''
  }
}
