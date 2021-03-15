/*
 * Copyright 2020 THL A29 Limited, a Tencent company.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.tencentcloud;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TencentCloudServerGroupCreator implements ServerGroupCreator {

  @Override
  public List<Map> getOperations(StageExecution stage) {
    Map<String, Object> operation = new HashMap();

    if (stage.getContext().containsKey("cluster")) {
      operation.putAll((Map) stage.getContext().get("cluster"));
    } else {
      operation.putAll(stage.getContext());
    }

    return new ArrayList() {
      {
        add(
            new HashMap() {
              {
                put(OPERATION, operation);
              }
            });
      }
    };
  }

  @Override
  public boolean isKatoResultExpected() {
    return false;
  }

  @Override
  public String getCloudProvider() {
    return "tencentcloud";
  }

  @Override
  public Optional<String> getHealthProviderName() {
    return Optional.of("TencentCloud");
  }
}
