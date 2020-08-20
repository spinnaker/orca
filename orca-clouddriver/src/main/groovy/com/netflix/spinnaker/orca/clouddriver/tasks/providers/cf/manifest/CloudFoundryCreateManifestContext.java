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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf.manifest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import lombok.Getter;

import java.util.List;

@Getter
public class CloudFoundryCreateManifestContext {

    private final CloudFoundryCreateManifestTask.InputArtifact manifestTemplate;
    private final List<CloudFoundryCreateManifestTask.InputArtifact> varsArtifacts;
    private final ExpectedArtifact expectedArtifact;
    private final String outputName;
    private final String templateRenderer = "CF";


    public CloudFoundryCreateManifestContext(
            @JsonProperty("manifestTemplate") CloudFoundryCreateManifestTask.InputArtifact manifestTemplate,
            @JsonProperty("varsArtifacts") List<CloudFoundryCreateManifestTask.InputArtifact> varsArtifacts,
            @JsonProperty("expectedArtifact") ExpectedArtifact expectedArtifact,
            @JsonProperty("outputName") String outputName) {
        this.manifestTemplate = manifestTemplate;
        this.varsArtifacts = varsArtifacts;
        this.expectedArtifact = expectedArtifact;
        this.outputName = outputName;
    }

}
