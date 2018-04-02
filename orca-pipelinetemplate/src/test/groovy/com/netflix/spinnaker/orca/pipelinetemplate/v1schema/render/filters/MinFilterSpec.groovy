/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.filters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.DefaultRenderContext
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.JinjaRenderer
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer
import spock.lang.Specification
import spock.lang.Unroll

class MinFilterSpec extends Specification {

  Renderer renderer = new JinjaRenderer(new ObjectMapper(), Mock(Front50Service), [])

  @Unroll
  def "should return max number"() {
    given:
    RenderContext context = new DefaultRenderContext("myapp", Mock(PipelineTemplate), [:])
    context.variables.put("subject", subject)


    when:
    def result = renderer.render("{{ subject|min }}", context)

    then:
    result == expected

    where:
    subject         || expected
    [-4, 1, 5]      || "-4"
    [0.0, 1.4, 5.5] || "0.0"
    ["1", "2", "3"] || "1"
  }

  @Unroll
  def "should throw on invalid input"() {
    given:
    RenderContext context = new DefaultRenderContext("myapp", Mock(PipelineTemplate), [:])
    context.variables.put("subject", subject)

    when:
    renderer.render("{{ subject|min }}", context)

    then:
    thrown(expected)

    where:
    subject    || expected
    "a"        || TemplateRenderException
    ["a": "b"] || TemplateRenderException
  }
}
