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

package com.netflix.spinnaker.orca.pipelinetemplate.model.impl;

import com.netflix.spinnaker.orca.pipelinetemplate.model.HierarchicalContent;
import com.netflix.spinnaker.orca.pipelinetemplate.model.ResolveContext;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DefaultResolveContext.
 */
public class DefaultResolveContext implements ResolveContext {

    static List<HierarchicalContent> loadIncludes(Collection<Include> includes) {
        //TODO-cfieber
        return Collections.emptyList();
    }

    public DefaultResolveContext() {
        this(Collections.emptySet());
    }

    public DefaultResolveContext(Collection<Include> includes) {
        this(null, includes);
    }

    public DefaultResolveContext(ResolveContext parent, Collection<Include> includes) {
        this(parent, loadIncludes(includes));
    }

    public DefaultResolveContext(ResolveContext parent, List<HierarchicalContent> content) {
        this.parent = parent;
        this.contentByName = content == null ? Collections.emptyMap() : Collections.unmodifiableMap(mapByName(content));
    }

    private static Map<String, HierarchicalContent> mapByName(List<HierarchicalContent> contents) {
        Map<String, HierarchicalContent> mappedContent = new LinkedHashMap<>();
        for (HierarchicalContent content : contents) {
            mappedContent.put(content.getName(), content);
        }
        return mappedContent;
    }


    private final ResolveContext parent;
    private final Map<String, HierarchicalContent> contentByName;


    @Override
    public ResolveContext include(Collection<Include> includes) {
        if (includes == null || includes.isEmpty()) {
            return this;
        }
        return new DefaultResolveContext(this, includes);
    }

    @Override
    public Optional<HierarchicalContent> getContentByName(String name) {
        return Optional.ofNullable(
                Optional.ofNullable(parent)
                        .flatMap(rc -> rc.getContentByName(name))
                        .orElse(contentByName.get(name)));
    }
}
