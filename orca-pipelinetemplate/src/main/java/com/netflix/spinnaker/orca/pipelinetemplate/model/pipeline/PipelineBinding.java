/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate.model.pipeline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * PipelineBinding.
 */
public class PipelineBinding {
    public static class DeploymentTargetMapping {
        private final String name;
        private final Map<String, Object> parameters;

        public DeploymentTargetMapping(String name) {
            this(name, null);
        }

        @JsonCreator
        public DeploymentTargetMapping(@JsonProperty("name") String name,
                                       @JsonProperty("parameters") Map<String, Object> parameters) {
            this.name = Objects.requireNonNull(name);
            this.parameters = parameters == null ? Collections.emptyMap() : Collections.unmodifiableMap(parameters);
        }

        public String getDeploymentTargetName() {
            return name;
        }

        public Map<String, Object> getParameters() {
            return parameters;
        }
    }

    public static class DeploymentTargetBinding {
        private final String name;
        private final List<DeploymentTargetMapping> targets;

        @JsonCreator
        public DeploymentTargetBinding(@JsonProperty("name") String name,
                                       @JsonProperty("targets") List<DeploymentTargetMapping> targets) {
            this.name = Objects.requireNonNull(name);
            this.targets = Collections.unmodifiableList(Objects.requireNonNull(targets));
        }

        public String getName() {
            return name;
        }

        public List<DeploymentTargetMapping> getTargets() {
            return targets;
        }
    }



    private final String pipelineTemplateName;
    private final List<DeploymentTargetBinding> deploymentTargetBindings;
    private final Map<String, Object> pipelineExtensions;

    public PipelineBinding(String pipelineTemplateName) {
        this(pipelineTemplateName, null, null);
    }

    @JsonCreator
    public PipelineBinding(@JsonProperty("pipelineTemplateName") String pipelineTemplateName,
                           @JsonProperty("deploymentTargets") List<DeploymentTargetBinding> deploymentTargetBindings,
                           @JsonProperty("piplineExtensions") Map<String, Object> pipelineExtensions) {
        this.pipelineTemplateName = Objects.requireNonNull(pipelineTemplateName);
        this.deploymentTargetBindings = deploymentTargetBindings == null ? Collections.emptyList() : Collections.unmodifiableList(deploymentTargetBindings);
        this.pipelineExtensions = pipelineExtensions == null ? Collections.emptyMap() : Collections.unmodifiableMap(pipelineExtensions);
    }

    public String getPipelineTemplateName() {
        return pipelineTemplateName;
    }

    public List<DeploymentTargetBinding> getDeploymentTargetBindings() {
        return deploymentTargetBindings;
    }

    public Map<String, Object> getPipelineExtensions() {
        return pipelineExtensions;
    }
}
