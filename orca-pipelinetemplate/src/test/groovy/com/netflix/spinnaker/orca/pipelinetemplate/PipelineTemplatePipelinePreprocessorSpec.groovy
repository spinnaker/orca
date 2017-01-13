/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification

/**
 * PipelineTemplatePipelinePreprocessorSpec.
 */
class PipelineTemplatePipelinePreprocessorSpec extends Specification {

  def "should expand template in classpath"() {
    given:
    def request = [
        type: 'templatedPipeline',
        id: id,
        application: application,
        pipelineConfigurationUri: 'bindings.json',
        pipelineExecutionName: 'dcdtest'
    ]

    when:
    def result = new PipelineTemplatePipelinePreprocessor().process(request)

    then:
    result.id == id
    result.application == application
    result.stages[0].type == 'wait'

    where:
    id = UUID.randomUUID().toString()
    application = 'foo'
  }
}
