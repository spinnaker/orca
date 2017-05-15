/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.orca.extensionpoint.pipeline.PipelinePreprocessor
import com.netflix.spinnaker.orca.pipelinetemplate.loader.FileTemplateSchemeLoader
import com.netflix.spinnaker.orca.pipelinetemplate.loader.TemplateLoader
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.JinjaRenderer
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer
import org.unitils.reflectionassert.ReflectionComparatorMode
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static org.unitils.reflectionassert.ReflectionAssert.assertReflectionEquals

class PipelineTemplatePipelinePreprocessorSpec extends Specification {

  ObjectMapper objectMapper = new ObjectMapper()

  TemplateLoader templateLoader = new TemplateLoader([new FileTemplateSchemeLoader(objectMapper)])

  Renderer renderer = new JinjaRenderer(objectMapper)

  Registry registry = Mock() {
    clock() >> Mock(Clock) {
      monotonicTime() >> 0L
    }
    timer(_) >> Mock(Timer)
  }

  @Subject
  PipelinePreprocessor subject = new PipelineTemplatePipelinePreprocessor(
    objectMapper,
    templateLoader,
    renderer,
    registry
  )

  def 'should ignore non-templated pipeline requests'() {
    given:
    def request = [
      type: 'not-interested'
    ]

    when:
    def result = subject.process(request)

    then:
    result == [
      type: 'not-interested'
    ]
    0 * _
  }

  def 'should process simple template'() {
    given:
    def request = createTemplateRequest('simple-001.yml', [
      regions: ['us-east-1', 'us-west-2']
    ], [
      [
        id: 'wait',
        type: 'wait',
        dependsOn: ['tagImage'],
        config: [waitTime: 5]
      ],
      [
        id: 'injectedWait',
        type: 'wait',
        inject: [
          first: true
        ],
        config: [
          waitTime: 5
        ]
      ]
    ])

    when:
    def result = subject.process(request)

    then:
    def expected = [
      id: null,
      application: 'myapp',
      name: 'Unnamed Execution',
      keepWaitingPipelines: false,
      limitConcurrent: true,
      parallel: true,
      notifications: [],
      stages: [
        [
          id: null,
          refId: 'injectedWait',
          requisiteStageRefIds: [],
          type: 'wait',
          name: 'injectedWait',
          waitTime: 5
        ],
        [
          id: null,
          refId: 'bake',
          type: 'bake',
          name: 'Bake',
          requisiteStageRefIds: ['injectedWait'],
          regions: ['us-east-1', 'us-west-2'],
          package: 'myapp-package',
          baseOs: 'trusty',
          vmType: 'hvm',
          storeType: 'ebs',
          baseLabel: 'release'
        ],
        [
          id: null,
          refId: 'tagImage',
          type: 'tagImage',
          name: 'Tag Image',
          requisiteStageRefIds: ['bake'],
          tags: [
            stack: 'test'
          ]
        ],
        [
          id: null,
          refId: 'wait',
          type: 'wait',
          name: 'wait',
          requisiteStageRefIds: ['tagImage'],
          waitTime: 5
        ]
      ]
    ]
    assertReflectionEquals(expected, result, ReflectionComparatorMode.IGNORE_DEFAULTS)
  }

  def 'should render jackson mapping exceptions'() {
    when:
    def result = subject.process(createTemplateRequest('invalid-template-001.yml', [:], [], true))

    then:
    noExceptionThrown()
    result.errors != null
    result.errors*.message.contains("failed loading template")
  }

  @Unroll
  def 'should not render falsy conditional stages'() {
    when:
    def result = subject.process(createTemplateRequest('conditional-stages-001.yml', [includeWait: includeWait]))

    then:
    result.stages*.name == expectedStageNames

    where:
    includeWait || expectedStageNames
    true        || ['wait', 'conditionalWait']
    false       || ['wait']
  }

  @Unroll
  def 'should preserve children stages of conditional stage'() {
    when:
    def result = subject.process(createTemplateRequest('conditional-stages-with-children-001.yml', [includeWait: includeWait]))

    then:
    result.stages*.name == expectedStageNames

    and:
    result.stages.find { it.name == 'childOfConditionalStage' }.requisiteStageRefIds == childStageRequisiteRefIds as Set

    where:
    includeWait || childStageRequisiteRefIds  || expectedStageNames
    true        || ['conditionalWait']        || ['wait', 'conditionalWait', 'childOfConditionalStage']
    false       || ['wait']                   || ['wait', 'childOfConditionalStage']
  }


  Map<String, Object> createTemplateRequest(String templatePath, Map<String, Object> variables = [:], List<Map<String, Object>> stages = [], boolean plan = false) {
    return [
      type: 'templatedPipeline',
      trigger: [
        type: "jenkins",
        master: "master",
        job: "job",
        buildNumber: 1111
      ],
      config: [
        schema: '1',
        id: 'myTemplate',
        pipeline: [
          application: 'myapp',
          template: [
            source: getClass().getResource("/templates/${templatePath}").toURI()
          ],
          variables: variables
        ],
        stages: stages
      ],
      plan: plan
    ]
  }
}
