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

package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model

import com.fasterxml.jackson.annotation.JsonBackReference
import com.fasterxml.jackson.annotation.JsonManagedReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import groovy.transform.Canonical
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification

/**
 * DeploymentModelSpec.
 */
class DeploymentModelSpec extends Specification {
/* jackson foo:
  @Canonical
  static class Child {
    @JsonBackReference
    Parent p
    int age

    String getName() {
      return p.children.entrySet().findResult { e -> if (e.value.is(this)) return e.key else return null }
    }
  }

  @Canonical
  static class Parent {
    @JsonManagedReference
    Map<String, Child> children = [:]
  }

  def "it should serialize"() {
    given:
    def p = new Parent()
    p.children.jimmy = new Child(p: p, age: 3)
    p.children.billy = new Child(p: p, age: 5)

    when:
    def result = new ObjectMapper().writeValueAsString(p)

    then:
    result != null
    println result
  }

  def "it should deserialize"() {
    given:
    def json = '{"children":{"jimmy":{"age":3},"billy":{"age":5}}}'

    when:
    Parent p = new ObjectMapper().readValue(json, Parent)

    then:
    p.children.size() == 2
    p.children.jimmy.name == 'jimmy'
  }
*/

  def "should layer content"() {
    given:
    def models = [
        new DeploymentModel(clusters: [leaf: enhm([extends: 'extendo', someOtherAttribute: 'mustard', someExtendAttribute: 'bologna'])]), //highest precidence
        new DeploymentModel(clusters: [extendo: enhm([extends: 'base', someExtendAttribute: 'ham'])]),
        new DeploymentModel(clusters: [base: enhm([regions: ['us-east-1', 'us-west-2'], someBaseAttribute: 'bacon'])]), //lowest precidence
    ]

    def resolver = new ExtensibleContentResolver<DeploymentModel>(models)

    expect:
    resolver.resolve('leaf', {DeploymentModel dm -> dm.clusters }).get() == enhm([
        regions: ['us-east-1', 'us-west-2'],
        someBaseAttribute: 'bacon',
        someExtendAttribute: 'bologna',
        someOtherAttribute: 'mustard',
        extends: 'extendo'
    ])
  }

  def "should override lower layer content"() {
    given:
    def models = [
        new DeploymentModel(clusters: [leaf: enhm([extends: 'extendo', someOtherAttribute: 'mustard', someExtendAttribute: 'bologna']), base: enhm([regions: ['eu-west-1']])]), //highest precidence
        new DeploymentModel(clusters: [extendo: enhm([extends: 'base', someExtendAttribute: 'ham'])]),
        new DeploymentModel(clusters: [base: enhm([regions: ['us-east-1', 'us-west-2'], someBaseAttribute: 'bacon'])]), //lowest precidence
    ]

    def resolver = new ExtensibleContentResolver<DeploymentModel>(models)

    expect:
    resolver.resolve('leaf', {DeploymentModel dm -> dm.clusters }).get() == enhm([
        regions: ['eu-west-1'],
        someExtendAttribute: 'bologna',
        someOtherAttribute: 'mustard',
        extends: 'extendo'
    ])
  }

  def "should resolve a cluster"() {
    given:
    def models = [
        new DeploymentModel(clusters: [spinBase: enhm([requires: [resources: [cpu: "1", ram: "4gb", disk: "20gb"]]])])
    ]

    def dmResolver = new DeploymentModelResolver(models)

    when:
    def cluster = dmResolver.getCluster("spinBase")

    then:
    cluster != null
    cluster.requires.resources.cpu == "1"
  }

  def "should resolve a cluster with environment with account"() {
    given:
    def models = [
        new DeploymentModel(accounts: [test: enhm(
            name: "test",
            cloudProvider: "aws",
            regions: ["us-east-1": [
                name: "us-east-1",
                zones: ["us-east-1c", "us-east-1d"]
            ]]
        )]),
        new DeploymentModel(environments: [staging: enhm(account: "test", stack: "staging")]),
        new DeploymentModel(clusters: [spinBase: enhm([environment: "staging", requires: [resources: [cpu: "1", ram: "4gb", disk: "20gb"]]])])
    ]

    def dmResolver = new DeploymentModelResolver(models)

    when:
    def cluster = dmResolver.getCluster("spinBase")

    then:
    cluster != null
    cluster.requires.resources.cpu == "1"
    cluster.environment.stack == "staging"
    cluster.environment.account.cloudProvider == "aws"
  }

  def "should allow inline extension"() {
    def models = [
        new DeploymentModel(accounts: [test: enhm(
            name: "test",
            cloudProvider: "aws",
            regions: ["us-east-1": [
                name: "us-east-1",
                zones: ["us-east-1c", "us-east-1d"]
            ]]
        )]),
        new DeploymentModel(environments: [staging: enhm(account: "test", stack: "staging")]),
        new DeploymentModel(clusters: [spinBase: enhm([
            environment: [extends: "staging", "detail": "canary"],
            requires: [
                resources: [
                    cpu: "1",
                    ram: "4gb",
                    disk: "20gb"]]])])
    ]

    def dmResolver = new DeploymentModelResolver(models)

    when:
    def cluster = dmResolver.getCluster("spinBase")

    then:
    cluster != null
    cluster.requires.resources.cpu == "1"
    cluster.environment.stack == "staging"
    cluster.environment.account.cloudProvider == "aws"
    cluster.environment.detail == "canary"
  }

  def "should fail on unknown extends at resolve time"() {
    given:
    def models = [
        new DeploymentModel(clusters: [spinBase: enhm([ extends: 'unknown' ])])
    ]
    def dmResolver = new DeploymentModelResolver(models)

    when:
    dmResolver.getCluster("spinBase")

    then:
    thrown(NoSuchElementException)
  }

  def "should read yaml filearooni"() {

    given:
    def mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
    def models = new Yaml().loadAll(getClass().getResourceAsStream("/gate.yml")).collect {
      mapper.convertValue(it, DeploymentModel)
    }
    def resolver = new DeploymentModelResolver(models)

    when:
    def cluster = resolver.getCluster("gateMain")
    println mapper.writeValueAsString(cluster)
    cluster = resolver.getCluster("gateFailover")
    println mapper.writeValueAsString(cluster)

    then:
    true
  }

  private ExtensibleNamedHashMap enhm(Map<String, Object> content) {
    return new ExtensibleNamedHashMap(content)
  }
}
