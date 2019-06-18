/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DeployManifestContext extends ManifestContext {
  @Nullable private List<Map<Object, Object>> manifests;

  private TrafficManagement trafficManagement = new TrafficManagement();

  @Data
  public static class TrafficManagement {
    private boolean enabled = false;
    private Options options = new Options();

    @Data
    public static class Options {
      private boolean enableTraffic = false;
      private List<String> services = Collections.emptyList();
      private ManifestStrategyType strategy = ManifestStrategyType.None;
    }

    public enum ManifestStrategyType {
      @JsonProperty("redblack")
      RedBlack,

      @JsonProperty("highlander")
      Highlander,

      @JsonProperty("none")
      None
    }
  }

  @Override
  public List<Map<Object, Object>> getManifest() {
    return manifests;
  }
}
