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
import com.fasterxml.jackson.core.type.TypeReference;
import com.netflix.spinnaker.orca.pipelinetemplate.model.HierarchicalContent;
import com.netflix.spinnaker.orca.pipelinetemplate.model.ParameterizedContent;
import com.netflix.spinnaker.orca.pipelinetemplate.model.ResolveContext;
import com.netflix.spinnaker.orca.pipelinetemplate.model.impl.DefaultResolveContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PipelineBindings.
 */
public class PipelineBindings {
    private final List<String> pipelineTemplateIncludes;
    private final List<String> deploymentTargetIncludes;
    private final List<PipelineBinding> pipelineBindings;

    @JsonCreator
    public PipelineBindings(@JsonProperty("pipelineTemplateIncludes") List<String> pipelineTemplateIncludes,
                            @JsonProperty("deploymentTargetIncludes") List<String> deploymentTargetIncludes,
                            @JsonProperty("pipelineBindings") List<PipelineBinding> pipelineBindings) {
        this.pipelineTemplateIncludes = Collections.unmodifiableList(Objects.requireNonNull(pipelineTemplateIncludes));
        this.deploymentTargetIncludes = Collections.unmodifiableList(Objects.requireNonNull(deploymentTargetIncludes));
        this.pipelineBindings = Collections.unmodifiableList(Objects.requireNonNull(pipelineBindings));
    }

    private final JsonResourceLoader<PipelineTemplate> pipelineTemplateLoader = new JsonResourceLoader<>(new TypeReference<List<PipelineTemplate>>() {});
    private final JsonResourceLoader<HierarchicalContent> deploymentTargetLoader = new JsonResourceLoader<>(new TypeReference<List<HierarchicalContent>>() {});

    public Map<String, Object> getPipelineExecution(String bindingName) {
        PipelineBinding binding = pipelineBindings.stream()
                .filter(pb -> pb.getPipelineTemplateName().equals(bindingName))
                .findFirst()
                .orElseThrow(NoSuchElementException::new);

        List<PipelineTemplate> loaded = pipelineTemplateLoader.load(pipelineTemplateIncludes);
        PipelineTemplate template = loaded.stream()
                .filter(tmpl -> tmpl.getName().equals(binding.getPipelineTemplateName()))
                .findFirst()
                .orElseThrow(NoSuchElementException::new);

        Map<String, HierarchicalContent> deploymentTargets = deploymentTargetLoader.load(deploymentTargetIncludes)
                .stream()
                .collect(Collectors.toMap(HierarchicalContent::getName, Function.identity()));

        Set<String> requiredTargets = binding.getDeploymentTargetBindings()
                .stream()
                .flatMap(dtb -> dtb.getTargets().stream())
                .map(PipelineBinding.DeploymentTargetMapping::getDeploymentTargetName)
                .collect(Collectors.toSet());

        requiredTargets.removeAll(deploymentTargets.keySet());

        if (!requiredTargets.isEmpty()) {
            throw new NoSuchElementException("Missing deployment targets " + requiredTargets);
        }

        ResolveContext ctx = new DefaultResolveContext(null, new ArrayList<HierarchicalContent>(deploymentTargets.values()));
        Map<String, List<ParameterizedContent>> resolvedDeploymentTargets = new LinkedHashMap<>();
        binding.getDeploymentTargetBindings().forEach(dtb -> {
            List<ParameterizedContent> resolved = dtb.getTargets()
                    .stream()
                    .map(dtm -> deploymentTargets.get(dtm.getDeploymentTargetName()).resolve(ctx, dtm.getParameters()))
                    .collect(Collectors.toList());
            resolvedDeploymentTargets.put(dtb.getName(), resolved);
        });

        return template.evaluate(resolvedDeploymentTargets, binding.getPipelineExtensions());
    }
}
