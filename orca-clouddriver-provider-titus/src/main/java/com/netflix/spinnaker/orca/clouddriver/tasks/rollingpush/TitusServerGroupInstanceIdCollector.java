/*
 * Copyright 2020 Netflix, Inc.
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
 */
package com.netflix.spinnaker.orca.clouddriver.tasks.rollingpush;

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.orca.kato.tasks.rollingpush.ServerGroupInstanceIdCollector;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
@NonnullByDefault
public class TitusServerGroupInstanceIdCollector implements ServerGroupInstanceIdCollector {
  @Override
  public boolean supports(String cloudProvider) {
    return "titus".equalsIgnoreCase(cloudProvider);
  }

  @Override
  public List<String> collect(List<Map<String, Object>> serverGroupInstances) {
    return serverGroupInstances.stream()
        .map(i -> (String) i.get("id"))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Override
  public String one(Map<String, Object> serverGroupInstance) {
    return (String) serverGroupInstance.get("id");
  }
}
