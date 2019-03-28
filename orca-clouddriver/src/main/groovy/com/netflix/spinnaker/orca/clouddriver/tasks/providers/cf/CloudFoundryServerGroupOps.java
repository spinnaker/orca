/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;

import java.util.Base64;

public class CloudFoundryServerGroupOps {
  protected final ObjectMapper mapper;
  protected final ArtifactResolver artifactResolver;

  public CloudFoundryServerGroupOps(ObjectMapper mapper, ArtifactResolver artifactResolver) {
    this.mapper = mapper;
    this.artifactResolver = artifactResolver;
  }

  protected Artifact getManifestArtifact(Stage stage,
                                         Object input) {
    Manifest manifest = mapper.convertValue(input, Manifest.class);
    if (manifest.getDirect() != null) {
      return Artifact.builder()
        .name("manifest")
        .type("embedded/base64")
        .artifactAccount("embedded-artifact")
        .reference(Base64.getEncoder().encodeToString(manifest.getDirect().toManifestYml().getBytes()))
        .build();
    }

    Artifact artifact = artifactResolver.getBoundArtifactForStage(stage, manifest.getArtifactId(), manifest.getArtifact());
    if(artifact == null) {
      throw new IllegalArgumentException("Unable to bind the manifest artifact");
    }

    return artifact;
  }
}
