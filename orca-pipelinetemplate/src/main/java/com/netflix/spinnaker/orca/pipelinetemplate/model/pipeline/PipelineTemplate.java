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
import com.netflix.spinnaker.orca.pipelinetemplate.model.ParameterizedContent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * PipelineTemplate.
 */
public class PipelineTemplate {
    private final String name;
    private final List<String> deploymentTargets;
    private final Map<String, Object> pipeline;

    @JsonCreator
    public PipelineTemplate(@JsonProperty("name") String name,
                            @JsonProperty("deploymentTargets") List<String> deploymentTargets,
                            @JsonProperty("pipeline") Map<String, Object> pipeline) {
        this.name = Objects.requireNonNull(name, "name");
        this.deploymentTargets = deploymentTargets == null ? Collections.emptyList() : Collections.unmodifiableList(deploymentTargets);
        this.pipeline = Collections.unmodifiableMap(Objects.requireNonNull(pipeline, "pipeline"));
    }

    public String getName() {
        return name;
    }

    public List<String> getDeploymentTargets() {
        return deploymentTargets;
    }

    public Map<String, Object> getPipeline() {
        return pipeline;
    }

    public Map<String, Object> evaluate(Map<String, List<ParameterizedContent>> deploymentTargets, Map<String, Object> pipelineExtensions) {
        Set<String> requiredTargets = new HashSet<>(deploymentTargets.keySet());
        requiredTargets.removeAll(deploymentTargets.keySet());
        if (!requiredTargets.isEmpty()) {
            throw new IllegalArgumentException("Missing deployment target bindings for: " + requiredTargets);
        }

        if (!(pipelineExtensions == null || pipelineExtensions.isEmpty())) {
            throw new UnsupportedOperationException("pipelineExtension support not implemented");
        }

        TemplateFunctions functions = new TemplateFunctions(deploymentTargets);

        return parameterizeMap(getPipeline(), functions);
    }

    static Pattern function = Pattern.compile("\\#(\\p{Alpha}+)\\(\\'(\\p{Alpha}+)\\'\\)");

    static class TemplateFunctions {
        private Map<String, List<ParameterizedContent>> deploymentTargets;

        public TemplateFunctions(Map<String, List<ParameterizedContent>> deploymentTargets) {
            this.deploymentTargets = Collections.unmodifiableMap(Objects.requireNonNull(deploymentTargets));
        }

        public String clusterName(String target) {
            Set<String> clusterNames = getTarget(target).map(t -> {
                StringBuilder sb = new StringBuilder(t.get("application").toString());
                boolean stack = t.containsKey("stack");
                if (stack) {
                    sb.append("-").append(t.get("stack").toString());
                }
                if (t.containsKey("freeFormDetails")) {
                    if (!stack) {
                        sb.append("-");
                    }
                    sb.append("-").append(t.get("freeFormDetails").toString());
                }
                return sb.toString();
            }).collect(Collectors.toSet());

            return singleResult(clusterNames, target, "clusterName");
        }

        public String application(String target) {
            Set<String> applications = getTarget(target).map(t -> t.get("application").toString()).collect(Collectors.toSet());
            return singleResult(applications, target, "application");
        }

        public Collection<String> regions(String target) {
            return getTarget(target).flatMap(t -> ((Map<String, ?>) t.get("availabilityZones")).keySet().stream()).collect(Collectors.toSet());
        }

        public List<Map<String, Object>> clusters(String target) {
            return getTarget(target).collect(Collectors.toList());
        }

        public String accountName(String target) {
            Set<String> accountNames = getTarget(target).map(t -> t.get("account").toString()).collect(Collectors.toSet());
            return singleResult(accountNames, target, "accountName");
        }

        private Stream<Map<String, Object>> getTarget(String target) {
            if (!deploymentTargets.containsKey(target)) {
                throw new NoSuchElementException("No deploymentTarget named " + target);
            }
            return deploymentTargets.get(target).stream().map(ParameterizedContent::getContent);
        }

        private <T> T singleResult(Set<T> results, String target, String type) {
            if (results.size() != 1) {
                throw new IllegalStateException("Expected exactly 1 " + type + " for target " + target + " but found " + results);
            }
            return results.iterator().next();
        }

        Object invoke(Matcher m) {
            String method = m.group(1);
            String target = m.group(2);
            try {
                return getClass().getMethod(method, String.class).invoke(this, target);
            } catch (Exception ex) {
                throw new IllegalStateException("failed to invoke #" + method + "('" + target + "')");
            }
        }
    }

    //so C&P from ParameterizedContent...
    private static Object parameterizeObject(Object src, TemplateFunctions tf) {
        if (src instanceof String) {
            return parameterizeString((String) src, tf);
        } else if (src instanceof Collection) {
            return parameterizeCollection((Collection<Object>) src, tf);
        } else if (src instanceof Map) {
            return parameterizeMap((Map<String, Object>) src, tf);
        }

        return src;
    }

    private static Object parameterizeString(String src, TemplateFunctions tf) {
        String result = src;
        while (true) {
            Matcher m = function.matcher(result);
            if (m.matches()) {
                return tf.invoke(m);
            } else {
                if (!m.find()) {
                    //TODO-cf why?
                    return result.isEmpty() ? null : result;
                } else {
                    String replacement = tf.invoke(m).toString();
                    m.reset();
                    if (replacement == null) {
                        replacement = "";
                    }
                    result = m.replaceFirst(replacement);
                }
            }
        }
    }

    private static Collection<Object> parameterizeCollection(Collection<Object> src, TemplateFunctions tf) {
        Collection<Object> result = new ArrayList<>();
        for (Object o : src) {
            Object parameterized = parameterizeObject(o, tf);
            //if a String expanded to a List, add the items to this list instead of creating a nested list
            if (parameterized instanceof Collection && !(o instanceof Collection)) {
                result.addAll((Collection) parameterized);
            } else {
                result.add(parameterized);
            }
        }

        return result;
    }

    static Map<String, Object> parameterizeMap(Map<String, Object> src, TemplateFunctions tf) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : src.entrySet()) {
            Object parameterized = parameterizeObject(entry.getValue(), tf);
            if (parameterized != null) {
                result.put(entry.getKey(), parameterized);
            }
        }
        return result;
    }

}
