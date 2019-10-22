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

package com.netflix.spinnaker.orca.bakery.tasks.manifests;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.Getter;

@Getter
public class BakeManifestContext {
  private final List<CreateBakeManifestTask.InputArtifact> inputArtifacts;
  private final List<ExpectedArtifact> expectedArtifacts;
  private final Map<String, Object> overrides;
  private final Boolean evaluateOverrideExpressions;
  private final String templateRenderer;
  private final String outputName;
  private final String namespace;
  private final Boolean rawOverrides;
  // There does not seem to be a way to auto-generate a constructor using our current version of
  // Lombok (1.16.20) that
  // Jackson can use to deserialize.
  public BakeManifestContext(
      @Nullable @JsonProperty("inputArtifacts")
          List<CreateBakeManifestTask.InputArtifact> inputArtifacts,
      @JsonProperty("expectedArtifacts") List<ExpectedArtifact> expectedArtifacts,
      @JsonProperty("overrides") Map<String, Object> overrides,
      @JsonProperty("evaluateOverrideExpressions") Boolean evaluateOverrideExpressions,
      @JsonProperty("templateRenderer") String templateRenderer,
      @JsonProperty("outputName") String outputName,
      @JsonProperty("namespace") String namespace,
      @Nullable @JsonProperty("inputArtifact") CreateBakeManifestTask.InputArtifact inputArtifact,
      @JsonProperty("rawOverrides") Boolean rawOverrides) {
    this.inputArtifacts = Optional.of(inputArtifacts).orElse(new ArrayList<>());
    // Kustomize stage configs provide a single input artifact
    if (this.inputArtifacts.isEmpty() && inputArtifact != null) {
      this.inputArtifacts.add(inputArtifact);
    }
    this.expectedArtifacts = Optional.of(expectedArtifacts).orElse(new ArrayList<>());
    this.overrides = overrides;
    this.evaluateOverrideExpressions = evaluateOverrideExpressions;
    this.templateRenderer = templateRenderer;
    this.outputName = outputName;
    this.namespace = namespace;
    this.rawOverrides = rawOverrides;
  }
}
