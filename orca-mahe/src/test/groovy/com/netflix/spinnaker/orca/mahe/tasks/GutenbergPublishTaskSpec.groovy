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

package com.netflix.spinnaker.orca.mahe.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.mahe.MaheService
import com.netflix.spinnaker.orca.mahe.pipeline.GutenbergPublishStage
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class GutenbergPublishTaskSpec extends Specification {
  MaheService maheService = Mock(MaheService)
  ObjectMapper objectMapper = new ObjectMapper()

  @Subject
  GutenbergPublishTask task = new GutenbergPublishTask(maheService: maheService, mapper: objectMapper)

  def "should successfully publish to a topic"() {
    given:
    def gutenbergPublishStage = gutenbergStage()
    String env = gutenbergPublishStage.context.env
    String topic = (gutenbergPublishStage.context.dataPointer as Map).topic

    and:
    def successResponse = [
      gutenbergResult: [
        result: "ok",
        dataVersion: "1509841606249"
      ],
      origin: [
        user: "test@netflix.com",
        source: "SPINNAKER"
      ],
      type: "com.netflix.spinnaker.mahe.gutenberg.api.Publish",
      topic: topic,
      timestamp: 1509661428586,
      environment: "test"
    ]

    when:
    def result = task.execute(gutenbergPublishStage)

    then:
    1 * maheService.gutenbergPublish(env, gutenbergPublishStage.context) >>
      new Response('', 200, 'OK', [], new TypedString(objectMapper.writeValueAsString(successResponse)))
  }

  def gutenbergStage() {
    return stage {
      type = GutenbergPublishStage.PIPELINE_CONFIG_TYPE
      name = "Gutenberg Direct publish"
      context = [
        env: "test",
        dataPointer: [
          topic: "testTopic",
          version: "0000",
          repo: "direct",
          hasMetaData: false,
          hasContent: true,
          dataString: "{\n \"hello\" : \"world\"\n}"
        ],
        chunking: [
          cass: [
            chunkingThreshold: "1048576",
            chunkSize: "262144"
          ],
          s3: [
            chunkingThreshold: "1048576",
            chunkSize: "262144"
          ]
        ],
        fastPropAttrs: [
          serverId: "",
          asg: "",
          ami: "",
          cluster: "",
          appId: "testapp",
          stack: "",
          zone: "",
          region: "us-east-1",
          ttl: ""
        ],
        s2id: [
          autogen1: ""
        ],
        purgePolicy: [
          minNumOfVersions: "10",
          maxNumOfVersions: "10",
        ],
        stringText: "ewogICAiaGVsbG8iIDogIndvcmxkIgp9",
        contentSource: "stringText",
        updatedBy: "test@netflix.com"
      ]
    }
  }
}
