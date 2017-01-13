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

package com.netflix.spinnaker.orca.pipelinetemplate.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.orca.pipelinetemplate.model.impl.MapMerge;
import com.netflix.spinnaker.orca.pipelinetemplate.model.ResolveContext.Include;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HierarchicalContent.
 */
public class HierarchicalContent extends ParameterizedContent {
    public static class Extend {
        private final String name;
        private final Map<String, Object> parameters;

        public Extend(String name) {
            this(name, null);
        }

        @JsonCreator
        public Extend(@JsonProperty("name") String name,
                      @JsonProperty("parameters") Map<String, Object> parameters) {
            this.name = Objects.requireNonNull(name);
            this.parameters = parameters == null ? Collections.emptyMap() : Collections.unmodifiableMap(parameters);
        }

        public String getName() {
            return name;
        }

        public Map<String, Object> getParameters() {
            return parameters;
        }
    }

    private final List<Include> include;
    private final List<Extend> extend;

    public HierarchicalContent(String name) {
        this(name, null, null, null, null);
    }

    @JsonCreator
    public HierarchicalContent(@JsonProperty("name") String name,
                               @JsonProperty("content") Map<String, Object> content,
                               @JsonProperty("parameters") List<Parameter> parameters,
                               @JsonProperty("include") List<Include> include,
                               @JsonProperty("extend") List<Extend> extend) {
        super(name, content, parameters);
        this.include = include == null ? Collections.emptyList() : Collections.unmodifiableList(include);
        this.extend = extend == null ? Collections.emptyList() : Collections.unmodifiableList(extend);
    }

    public ParameterizedContent resolve(ResolveContext ctx, Map<String, Object> parameters) {
        ResolveContext resolveContext = ctx.include(include);
        ParameterizedContent extended = extend.stream().map(e -> {
            HierarchicalContent content = resolveContext.getContentByName(e.name).orElseThrow(NoSuchElementException::new);

            Map<String, Object> extendParams = getExtendParameters(e, content, parameters);
            //fill in any e.parameters from supplied parameters
            return content.resolve(resolveContext, extendParams);
        }).reduce(new ParameterizedContent(getName()), (a, b) -> new ParameterizedContent(getName(), MapMerge.merge(a.getContent(), b.getContent()), null));

        FilteredParameters filtered = filterParametersForContent(this, parameters);
        Map<String, Object> parameterizedContent = parameterize(getContent(), filtered.matchingParameters);
        return new ParameterizedContent(getName(), MapMerge.merge(extended.getContent(), parameterizedContent), filtered.missingParameters);
    }

    static class FilteredParameters {
        Map<String, Object> matchingParameters;
        List<Parameter> missingParameters;

        public FilteredParameters(Map<String, Object> matchingParameters) {
            this(matchingParameters, null);
        }

        public FilteredParameters(Map<String, Object> matchingParameters, List<Parameter> missingParameters) {
            this.matchingParameters = matchingParameters == null ? Collections.emptyMap() : Collections.unmodifiableMap(matchingParameters);
            this.missingParameters = missingParameters == null ? Collections.emptyList() : Collections.unmodifiableList(missingParameters);
        }
    }

    static FilteredParameters filterParametersForContent(ParameterizedContent content, Map<String, Object> parameters) {
        Map<String, Object> params = new LinkedHashMap<>(parameters);
        Set<String> contentParameterNames = content.getParameters()
                .stream()
                .map(Parameter::getName)
                .collect(Collectors.toSet());
        params.keySet().retainAll(contentParameterNames);

        if (params.size() != contentParameterNames.size()) {
            Set<String> missing = new LinkedHashSet<>(contentParameterNames);
            missing.removeAll(params.keySet());
            List<Parameter> missingParams = content.getParameters()
                    .stream()
                    .filter(p -> missing.contains(p.getName()))
                    .collect(Collectors.toList());
            return new FilteredParameters(params, missingParams);
        }

        return new FilteredParameters(params);
    }

    static Map<String, Object> getExtendParameters(Extend extend, ParameterizedContent content, Map<String, Object> inputParameters) {
        FilteredParameters filtered = filterParametersForContent(content, extend.getParameters());
        if (!filtered.missingParameters.isEmpty()) {
            throw new IllegalStateException("missing parameters " + filtered.missingParameters);
        }
        return parameterize(filtered.matchingParameters, inputParameters);
    }
}
