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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ParameterizedContent.
 */
public class ParameterizedContent extends NamedContent {
    public static class Parameter {
        private final String name;
        //description, default value, data type, etc etc

        public Parameter(String name) {
            this.name = Objects.requireNonNull(name);
        }

        public String getName() {
            return name;
        }
    }

    private final List<Parameter> parameters;

    public ParameterizedContent(String name) {
        this(name, null, null);
    }

    public ParameterizedContent(String name, Map<String, Object> content, List<Parameter> parameters) {
        super(name, content);
        this.parameters = parameters == null ? Collections.emptyList() : Collections.unmodifiableList(parameters);
    }

    public List<Parameter> getParameters() {
        return parameters;
    }

    static Map<Pattern, Object> compile(Map<String, Object> parameters) {
        Map<Pattern, Object> compiled = new LinkedHashMap<>(parameters.size());
        for (Map.Entry<String, Object> param : parameters.entrySet()) {
            Pattern pattern = Pattern.compile(Pattern.quote("{{" + param.getKey() + "}}"));
            compiled.put(pattern, param.getValue());
        }
        return compiled;
    }


    static Map<String, Object> parameterize(Map<String, Object> src, Map<String, Object> parameters) {
        return parameterizeMap(src, compile(parameters));
    }

    private static Object parameterizeObject(Object src, Map<Pattern, Object> parameters) {
        if (src instanceof String) {
            return parameterizeString((String) src, parameters);
        } else if (src instanceof Collection) {
            return parameterizeCollection((Collection<Object>) src, parameters);
        } else if (src instanceof Map) {
            return parameterizeMap((Map<String, Object>) src, parameters);
        }

        return src;
    }

    private static Object parameterizeString(String src, Map<Pattern, Object> parameters) {
        String result = src;
        for (Map.Entry<Pattern, Object> param : parameters.entrySet()) {
            Matcher matcher = param.getKey().matcher(result);
            if (matcher.matches()) {
                return param.getValue();
            }

            if (matcher.find()) {
                matcher.reset();
                String replacement = param.getValue() == null ? "" : param.getValue().toString();
                result = matcher.replaceAll(replacement);
            }
        }

        //TODO-cf why?
        if (result.isEmpty()) {
            return null;
        }

        return result;
    }

    private static Collection<Object> parameterizeCollection(Collection<Object> src, Map<Pattern, Object> parameters) {
        Collection<Object> result = new ArrayList<>();
        for (Object o : src) {
            Object parameterized = parameterizeObject(o, parameters);
            //if a String expanded to a List, add the items to this list instead of creating a nested list
            if (parameterized instanceof Collection && !(o instanceof Collection)) {
                result.addAll((Collection) parameterized);
            } else {
                result.add(parameterized);
            }
        }

        return result;
    }

    static Map<String, Object> parameterizeMap(Map<String, Object> src, Map<Pattern, Object> parameters) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : src.entrySet()) {
            Object parameterized = parameterizeObject(entry.getValue(), parameters);
            if (parameterized != null) {
                result.put(entry.getKey(), parameterized);
            }
        }
        return result;
    }
}
