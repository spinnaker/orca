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

package com.netflix.spinnaker.orca.pipelinetemplate.model

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.orca.pipelinetemplate.model.impl.DefaultResolveContext
import spock.lang.Specification

/**
 * HierarchicalContentSpec.
 */
class HierarchicalContentSpec extends Specification {
    def mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

    def "should load one"() {
        given:
        def hc = new HierarchicalContent("foo", [a:'b'], null, null, null)

        when:
        def resolved = hc.resolve(ResolveContext.forIncludes([]), [:])

        then:
        resolved.content == [a: 'b']
    }

    def "should extend"() {
        given:
        def base = new HierarchicalContent("base", [a: 'b'], null, null, [new HierarchicalContent.Extend("parent")])
        def parent = new HierarchicalContent("parent", [c: 'd'], null, null, null);
        def ctx = new TestResolveContext(base, parent)

        when:
        def resolved = base.resolve(ctx, [:])

        then:
        resolved.content == [a: 'b', c: 'd']
    }

    def "will it blend"() {
        given:
        def typeref = new TypeReference<List<HierarchicalContent>>() {}
        List<HierarchicalContent> content = ['accounts', 'deploydefaults', 'gate'].collect {
            mapper.readValue(getClass().getResource("/${it}.json"), typeref)
        }.flatten()

        def ctx = new TestResolveContext(content)
        def gateMain = ctx.getContentByName("gate-main").orElseThrow({ new IllegalStateException() })

        when:
        def resolved = gateMain.resolve(ctx, [:])

        then:
        resolved.getContent() != null

        println mapper.writeValueAsString(resolved.getContent())
    }

    def "will it blend clouddriver edition"() {
        given:
        def typeref = new TypeReference<List<HierarchicalContent>>() {}
        List<HierarchicalContent> content = ['accounts', 'deploydefaults', 'clouddriver'].collect {
            mapper.readValue(getClass().getResource("/${it}.json"), typeref)
        }.flatten()

        def ctx = new TestResolveContext(content)
        def gateMain = ctx.getContentByName("clouddriver-prestaging-cache").orElseThrow({ new IllegalStateException() })

        when:
        def resolved = gateMain.resolve(ctx, [:])

        then:
        resolved.getContent() != null
        resolved.parameters
        resolved.parameters[0].name == 'redis'

        println mapper.writeValueAsString(resolved.getContent())
    }


    static class TestResolveContext extends DefaultResolveContext {
        public TestResolveContext(List<HierarchicalContent> content) {
            super(null, content)
        }
        public TestResolveContext(HierarchicalContent... content) {
            this(Arrays.asList(content));
        }
    }
}
