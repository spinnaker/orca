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

package com.netflix.spinnaker.orca.mahe.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.mahe.MaheService
import com.netflix.spinnaker.orca.mahe.pipeline.CreatePropertyStage
import com.netflix.spinnaker.orca.mahe.pipeline.MonitorCreatePropertyStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.Specification

/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class CreatePropertiesTaskSpec extends Specification {

  MaheService maheService = Mock(MaheService)
  ObjectMapper mapper = new ObjectMapper()

  CreatePropertiesTask task = new CreatePropertiesTask(maheService: maheService, mapper: mapper)

  def "assemble the property list from the context"() {
    given:
    def pipeline = new Pipeline(application: 'foo')
    def scope = createScope()
    def property = createProperty()

    def stage = createPropertiesStage(pipeline, scope, property)

    when:
    List properties = task.assemblePersistedPropertyListFromContext(stage.context)

    then: "this is what the property payload the is sent to MAHE needs to look like"
    properties.size() == 1
    with(properties.first().property) {
      key == property.key
      value == property.value
      email == 'test@netflix.com'
      sourceOfUpdate == 'spinnaker'
      constraints == property.constraints
      cmcTicket == 'cmcTicket'
      description == property.description
      env == scope.env
      region == scope.region
      stack == scope.stack
      cluster == scope.cluster
      appId == scope.appIdList.first()
    }
  }

  def "create a single new persistent property"() {
    given:
    def pipeline = new Pipeline(application: 'foo')
    def property = createProperty()
    def createPropertiesStage = createPropertiesStage(pipeline, createScope(), property)
    pipeline.stages.addAll([createPropertiesStage, createMonitorStage(pipeline)])


    when:
    def results = task.execute(createPropertiesStage)

    then:
    1 * maheService.upsertProperty(_) >> { Map res ->
      def json = mapper.writeValueAsString([propertyId: 'propertyId'])
      new Response("http://mahe", 200, "OK", [], new TypedByteArray('application/json', json.bytes))
    }

    then:

    with(results.stageOutputs) {
      propertyIdList.size() == 1
      propertyIdList.contains(propertyId: 'propertyId')
    }
  }

  def "delete single persisted properties"() {
    given:
    def pipeline = new Pipeline(application: 'foo')
    def scope = createScope()
    def propertyId = '123propertyId'
    def property = createProperty(propertyId)
    def propertiesStage = createPropertiesStage(pipeline, scope, property)
    propertiesStage.context["delete"] = true
    pipeline.stages.addAll([propertiesStage, createMonitorStage(pipeline)])

    when:
    def results = task.execute(propertiesStage)

    then:
    1 * maheService.deleteProperty(property.propertyId, 'delete', scope.env) >> { def res ->
      def json = mapper.writeValueAsString([propertyId: 'propertyId'])
      new Response("http://mahe", 200, "OK", [], new TypedByteArray('application/json', json.bytes))
    }

    then:
    with(results.stageOutputs) {
      propertyIdList.size() == 1
      propertyIdList.contains(propertyId: 'propertyId')
    }

  }

  def "create multiple new persistent property"() {
    given:
    def pipeline = new Pipeline(application: 'foo')
    def parentStageId = UUID.randomUUID().toString()


    def properties = [
      [key: "foo", value: 'bar'],
      [key: "foo1", value: 'baz']
    ]

    def createPropertiesStage = new PipelineStage(pipeline, CreatePropertyStage.PIPELINE_CONFIG_TYPE, [
      scope              : createScope(),
      persistedProperties: properties,
      email              : 'foo@netflix.com',
      cmcTicket          : 'newCMCTiix'
    ])

    createPropertiesStage.parentStageId = parentStageId
    def monitorCreatePropertyStage = new PipelineStage(pipeline, MonitorCreatePropertyStage.PIPELINE_CONFIG_TYPE)

    pipeline.stages.addAll([createPropertiesStage, monitorCreatePropertyStage])

    Map captured

    when:
    def results = task.execute(createPropertiesStage)

    then:

    2 * maheService.upsertProperty(_) >> { Map res ->
      captured = res
      String propId = "${res.property.key}|${res.property.value}"
      def json = mapper.writeValueAsString([propertyId: propId])
      new Response("http://mahe", 200, "OK", [], new TypedByteArray('application/json', json.bytes))
    }

    then:
    with(results.stageOutputs) {
      propertyIdList.size() == 2
      propertyIdList.contains(propertyId: "${properties[0].key}|${properties[0].value}".toString())
      propertyIdList.contains(propertyId: "${properties[1].key}|${properties[1].value}".toString())
    }
  }


  def createPropertiesStage(pipeline, scope, property) {
    def context = [
      parentStageId: UUID.randomUUID().toString(),
      scope              : scope,
      persistedProperties: [property],
      email              : 'test@netflix.com',
      cmcTicket          : 'cmcTicket'
    ]
    new PipelineStage(pipeline, CreatePropertyStage.PIPELINE_CONFIG_TYPE, context)
  }

  def createMonitorStage(pipeline) {
    new PipelineStage(pipeline, MonitorCreatePropertyStage.PIPELINE_CONFIG_TYPE)
  }

  def createScope() {
    [
      env      : "test",
      appIdList: ["foo"],
      region   : "us-west-1",
      stack    : "main",
      cluster  : "foo-main",
    ]
  }

  def createProperty(propertyId) {
    def property = [key: "foo", value: 'bar', constraints: 'none']
    property['propertyId'] = propertyId ?: null
    property
  }
}
