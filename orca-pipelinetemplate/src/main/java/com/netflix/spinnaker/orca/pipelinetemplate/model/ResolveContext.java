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

import com.netflix.spinnaker.orca.pipelinetemplate.model.impl.DefaultResolveContext;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * ResolveContext.
 */
public interface ResolveContext {

    static ResolveContext forIncludes(Collection<Include> includes) {
        return new DefaultResolveContext(includes);
    }

    class Include {
        private final String location;

        public Include(String location) {
            this.location = Objects.requireNonNull(location, "location");
        }

        public String getLocation() {
            return location;
        }
    }

    ResolveContext include(Collection<Include> include);
    Optional<HierarchicalContent> getContentByName(String name);
}
