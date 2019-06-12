/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Data;

/** Common model for Deploy and Patch Manifest stage contexts. */
@Data
public abstract class ManifestContext {
  private Source source;

  private String manifestArtifactId;
  private Artifact manifestArtifact;
  private String manifestArtifactAccount;

  private List<String> requiredArtifactIds;
  private List<BindArtifact> requiredArtifacts;

  private boolean skipExpressionEvaluation = false;

  public enum Source {
    @JsonProperty("text")
    Text,

    @JsonProperty("artifact")
    Artifact
  }

  @Data
  public static class BindArtifact {
    @Nullable private String expectedArtifactId;

    @Nullable private Artifact artifact;
  }

  /**
   * @return A manifest provided as direct text input in the stage definition. Deploy and Patch
   *     Manifest stages have differently named model elements describing this one concept, so for
   *     backwards compatibility we must map their individual model elements to use them generally.
   */
  @Nullable
  public abstract String getRawManifest();
}
