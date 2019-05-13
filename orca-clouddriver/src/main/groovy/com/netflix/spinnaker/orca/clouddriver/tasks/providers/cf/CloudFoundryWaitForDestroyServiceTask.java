/*
 *  Copyright 2019 Pivotal, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.servicebroker.AbstractWaitForServiceTask;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CloudFoundryWaitForDestroyServiceTask extends AbstractWaitForServiceTask {
  @Autowired
  public CloudFoundryWaitForDestroyServiceTask(OortService oortService) {
    super(oortService);
  }

  protected ExecutionStatus oortStatusToTaskStatus(Map m) {
    return Optional.ofNullable(m)
        .map(
            myMap -> {
              String state = Optional.ofNullable(myMap.get("status")).orElse("").toString();
              switch (state) {
                case "FAILED":
                  return ExecutionStatus.TERMINAL;
                case "IN_PROGRESS":
                default:
                  return ExecutionStatus.RUNNING;
              }
            })
        .orElse(ExecutionStatus.SUCCEEDED);
  }
}
