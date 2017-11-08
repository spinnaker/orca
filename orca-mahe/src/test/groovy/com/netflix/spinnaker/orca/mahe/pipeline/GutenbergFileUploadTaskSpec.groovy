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

package com.netflix.spinnaker.orca.mahe.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.mahe.MaheService
import retrofit.client.Response
import retrofit.mime.TypedFile
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class GutenbergFileUploadTaskSpec extends Specification {
  MaheService maheService = Mock(MaheService)
  FileLoader fileLoader = Mock(LocalFileLoader)
  ObjectMapper objectMapper = new ObjectMapper()

  @Subject
  GutenbergFileUploadTask task = new GutenbergFileUploadTask(
    maheService: maheService,
    mapper: objectMapper,
    fileLoaders: [fileLoader]
  )

  def "should successfully upload a file when publishing to a topic"() {
    given:
    def gutenbergPublishStage = gutenbergStage()
    def env = gutenbergPublishStage.context.env
    def topic = (gutenbergPublishStage.context.dataPointer as Map).topic

    and:
    1 * fileLoader.supports(_ as URI,_ as String) >> true
    1 * fileLoader.load(_ as URI) >> new File("tmpFile")

    def successResponse = [
      gutenbergResult: [
        result: "ok",
        s3BucketName: "netflix.s3.genpop.test",
        s3ObjectName: "HermesExplorer/TmpUploadFile/file_17962f02-2c7d-4d4b-bc83-a4b6a2d6ead9",
        msg : "file is successfully uploaded"
      ],
      origin: [
        user: "test@netflix.com",
        source: "SPINNAKER"
      ],
      type: "com.netflix.spinnaker.mahe.gutenberg.api.Upload",
      topic: "testTopic",
      timestamp: 1509661428586,
      environment: "test",
      metadata: [
        UploadStats: [
          size: 22,
          duration: "PT3.209S",
          filename: "helloworld.json"
        ]
      ]
    ]

    when:
    def result = task.execute(gutenbergPublishStage)

    then:
    1 * maheService.gutenbergFileUpload(env as String, topic as String ,_ as TypedFile) >>
      new Response('', 200, 'OK', [], new TypedString(objectMapper.writeValueAsString(successResponse)))
    result.context.s3BucketName == successResponse.gutenbergResult.s3BucketName
    result.context.s3ObjectName == successResponse.gutenbergResult.s3ObjectName
  }

  def "should fail upload on a non 200 status code"() {
    given:
    def gutenbergPublishStage = gutenbergStage()
    def env = gutenbergPublishStage.context.env
    def topic = (gutenbergPublishStage.context.dataPointer as Map).topic

    and:
    1 * fileLoader.supports(_ as URI,_ as String) >> true
    1 * fileLoader.load(_ as URI) >> new File("tmpFile")
    1 * maheService.gutenbergFileUpload(env as String, topic as String ,_ as TypedFile) >>
      new Response('', 500, 'Busted', [], new TypedString(""))

    when:
    task.execute(gutenbergPublishStage)

    then:
    thrown(GutenbergFileUploadTask.GutenbergFileUploadException)
  }

  def "should fail upload on unsupported uri schemes"() {
    given:
    def gutenbergPublishStage = gutenbergStage()

    and:
    1 * fileLoader.supports(_ as URI,_ as String) >> false

    when:
    task.execute(gutenbergPublishStage)

    then:
    thrown(GutenbergFileUploadTask.GutenbergFileUploadException)
    0 * fileLoader.load(_ as URI)
    0 * maheService.gutenbergFileUpload(_ as String, _ as String ,_ as TypedFile)
  }

  def gutenbergStage() {
    return stage {
      type = GutenbergPublishStage.PIPELINE_CONFIG_TYPE
      name = "Gutenberg File Upload"
      context = [
        env: "test",
        dataPointer: [
          topic: "testTopic",
          version: "0000",
          repo: "s3",
          bucket: "test-s3-bucket",
          oject: "test/test.json",
          hasMetaData: true,
          hasContent: true
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
        metaData: [
          expireTime: "9999-12-31T23:59:59.999Z",
          compressAlgo: "none",
          comment:""
        ],
        source: "file:///test.json",
        contentSource: "localFile",
        updatedBy: "test@netflix.com"
      ]
    }
  }
}
