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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JsonResourceLoader.
 */
public class JsonResourceLoader<T> {

    private final TypeReference<List<T>> typeref;
    private final ObjectMapper mapper = new ObjectMapper();

    public JsonResourceLoader(TypeReference<List<T>> typeref) {
        this.typeref = typeref;
    }

    public List<T> load(String... resources) {
        return load(Arrays.asList(resources));
    }

    public List<T> load(Collection<String> resources) {

        List<T> results = resources.stream()
                .map(this::getResourceUrl)
                .flatMap(this::loadUrl)
                .collect(Collectors.toList());

        return results;
    }

    private URL getResourceUrl(String resource) {
        String abs = resource.startsWith("/") ? resource : "/" + resource;
        return getClass().getResource(abs);
    }

    private Stream<T> loadUrl(URL url) {
        try {
            List<T> values = mapper.readValue(url, typeref);
            return values.stream();
        } catch (IOException ioe) {
            throw new RuntimeException("JSON parsing failed", ioe);
        }
    }
}
