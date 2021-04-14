/*
 *
 *  * Copyright 2021 Amazon.com, Inc. or its affiliates.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License")
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package com.amazon.spinnaker.orca.aws.lambda.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import lombok.*;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
public class LambdaDefinition extends LinkedHashMap {

  public String getFunctionArn() {
    return (String) this.get("functionArn");
  }

  public Map<String, String> getRevisions() {
    return (Map<String, String>) this.get("revisions");
  }

  public String getRevisionId() {
    return (String) this.get("revisionId");
  }

  public List<EventSourceMappingConfiguration> getEventSourceMappings() {
    List<EventSourceMappingConfiguration> ans = new ArrayList<EventSourceMappingConfiguration>();
    List<LinkedHashMap> xx = (List<LinkedHashMap>) this.get("eventSourceMappings");
    if (xx == null) {
      return ans;
    }
    try {
      for (LinkedHashMap y : xx) {
        String jsonString = objectMapper.writeValueAsString(y);
        EventSourceMappingConfiguration answer =
            objectMapper.readValue(jsonString, EventSourceMappingConfiguration.class);
        ans.add(answer);
      }
      return ans;
    } catch (Exception e) {
      return ans;
    }
  }

  static ObjectMapper objectMapper = new ObjectMapper();
}
