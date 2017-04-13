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

package com.netflix.spinnaker.orca.pipeline.util

import com.netflix.spinnaker.orca.ExecutionStatus
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ContextParameterProcessorSpec extends Specification {

  @Subject ContextParameterProcessor contextParameterProcessor = new ContextParameterProcessor()

  @Unroll
  def "should #processAttributes"() {
    given:
    def source = ['test': sourceValue]
    def context = ['testArray': ['good', ['arrayVal': 'bad'], [['one': 'two']]], replaceMe: 'newValue', 'h1': [h1: 'h1Val'], hierarchy: [h2: 'hierarchyValue', h3: [h4: 'h4Val']],
                   replaceTest: 'stack-with-hyphens', withUpperCase: 'baconBacon']

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.test == expectedValue

    where:
    processAttributes                           | sourceValue                                                 | expectedValue
    'leave strings alone'                       | 'just a string'                                             | 'just a string'
    'transform simple properties'               | '${replaceMe}'                                              | 'newValue'
    'transform properties with dots'            | '${h1.h1}'                                                  | 'h1Val'
    'transform embedded properties'             | '${replaceMe} and ${replaceMe}'                             | 'newValue and newValue'
    'transform hierarchical values'             | '${hierarchy.h2}'                                           | 'hierarchyValue'
    'transform nested hierarchical values'      | '${hierarchy.h3.h4}'                                        | 'h4Val'
    'leave unresolvable values'                 | '${notResolvable}'                                          | '${notResolvable}'
    'get a value in an array'                   | '${testArray[0]}'                                           | 'good'
    'get a value in an map within an array'     | '${testArray[1].arrayVal}'                                  | 'bad'
    'get a value in an array within an array'   | '${testArray[2][0].one}'                                    | 'two'
    'support SPEL expression'                   | '${ h1.h1 == "h1Val" }'                                     | true
    'support SPEL defaults'                     | '${ h1.h2  ?: 60 }'                                         | 60
    'support SPEL string methods no args'       | '${ withUpperCase.toLowerCase() }'                          | 'baconbacon'
    'support SPEL string methods with args'     | '${ replaceTest.replaceAll("-","") }'                       | 'stackwithhyphens'
    'make any string alphanumerical for deploy' | '${ #alphanumerical(replaceTest) }'                         | 'stackwithhyphens'
    'make any string alphanumerical for deploy' | '''${#readJson('{ "newValue":"two" }')[#root.replaceMe]}''' | 'two' // [#root.parameters.cluster]
  }

  @Unroll
  def "should restrict fromUrl requests #desc"() {
    given:
    def source = ['test': '${ #fromUrl(\'' + theTest + '\')}']

    when:
    def result = contextParameterProcessor.process(source, [:], true)

    then:
    result.test == source.test

    where:
    theTest                   | desc
    'file:///etc/passwd'      | 'file scheme'
    'http://169.254.169.254/' | 'link local'
    'http://127.0.0.1/'       | 'localhost'
    'http://localhost/'       | 'localhost by name'
    //successful case: 'http://captive.apple.com/' | 'this should work'
  }

  @Unroll
  def "should not System.exit"() {
    when:

    def result = contextParameterProcessor.process([test: testCase], [:], true)

    then:
    //the failure scenario for this test case is the VM halting...
    true

    where:
    testCase                                  | desc
    '${T(java.lang.System).exit(1)}'          | 'System.exit'
    '${T(java.lang.Runtime).runtime.exit(1)}' | 'Runtime.getRuntime.exit'


  }

  @Unroll
  def "should not allow bad type #desc"() {
    given:
    def source = [test: testCase]

    when:
    def result = contextParameterProcessor.process(source, [:], true)

    then:
    //ensure we failed to interpret the expression and left it as is
    result.test == source.test

    where:
    testCase                                                            | desc
    '${ new java.net.URL(\'http://google.com\').openConnection() }'     | 'URL'
    '${T(java.lang.Boolean).forName(\'java.net.URI\').getSimpleName()}' | 'forName'
  }

  @Unroll
  def "should not allow bad method #desc"() {
    given:
    def source = [test: testCase]

    when:
    def result = contextParameterProcessor.process(source, [:], true)

    then:
    //ensure we failed to interpret the expression and left it as is
    result.test == source.test

    where:
    testCase                                                            | desc
    '${ new java.lang.Integer(1).wait(100000) }'                        | 'wait'
    '${ new java.lang.Integer(1).getClass().getSimpleName() }'          | 'getClass'
  }

  @Unroll
  def "when allowUnknownKeys is #allowUnknownKeys it #desc"() {
    given:
    def source = [test: sourceValue]
    def context = [exists: 'yay', isempty: '', isnull: null]

    when:
    def result = contextParameterProcessor.process(source, context, allowUnknownKeys)

    then:
    result.test == expectedValue

    where:
    desc                                      | sourceValue              | expectedValue            | allowUnknownKeys
    'should blank out null'                   | '${noexists}-foo'        | '-foo'                   | true
    'should leave alone non existing'         | '${noexists}-foo'        | '${noexists}-foo'        | false
    'should handle elvis'                     | '${noexists ?: "bacon"}' | 'bacon'                  | true
    'should leave elvis expression untouched' | '${noexists ?: "bacon"}' | '${noexists ?: "bacon"}' | false
    'should work with empty existing key'     | '${isempty ?: "bacon"}'  | 'bacon'                  | true
    'should work with empty existing key'     | '${isempty ?: "bacon"}'  | 'bacon'                  | false
    'should work with null existing key'      | '${isnull ?: "bacon"}'   | 'bacon'                  | true
    'should work with null existing key'      | '${isnull ?: "bacon"}'   | 'bacon'                  | false

  }

  def "should replace the keys in a map"() {
    given:
    def source = ['${replaceMe}': 'somevalue', '${replaceMe}again': ['cats': 'dogs']]

    when:
    def result = contextParameterProcessor.process(source, [replaceMe: 'newVal'], true)

    then:
    result.newVal == 'somevalue'
    result.newValagain?.cats == 'dogs'
  }

  def "should be able to swap out a SPEL expression of a string with other types"() {
    def source = ['test': ['k1': '${var1}', 'k2': '${map1}']]
    def context = [var1: 17, map1: [map1key: 'map1val']]

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.test.k1 instanceof Integer
    result.test.k1 == 17
    result.test.k2 instanceof Map
    result.test.k2.map1key == 'map1val'

  }

  def "should process elements of source map correctly"() {

    given:
    def source = ['test': '${h1}', 'nest': ['nest2': ['${h1}', '${h1}']], 'clusters': ['${h1}', '${h2}'], 'intval': 4]
    def context = ['h1': 'h1val', 'h2': 'h2val']

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.test == 'h1val'
    result.clusters == ['h1val', 'h2val']
    result.intval == 4
    result.nest.nest2 == ['h1val', 'h1val']
  }

  @Unroll
  def "correctly compute scmInfo attribute"() {

    given:
    def source = ['branch': '${scmInfo.branch}']

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.branch == expectedBranch

    where:
    context                                                                                                 | expectedBranch
    [:]                                                                                                     | '${scmInfo.branch}'
    [trigger: [buildInfo: [scm: [[branch: 'branch1']]]]]                                                    | 'branch1'
    [trigger: [buildInfo: [scm: [[branch: 'branch1'], [branch: 'master']]]]]                                | 'branch1'
    [trigger: [buildInfo: [scm: [[branch: 'develop'], [branch: 'master']]]]]                                | 'develop'
    [trigger: [buildInfo: [scm: [[branch: 'jenkinsBranch']]]], buildInfo: [scm: [[branch: 'buildBranch']]]] | 'buildBranch'
  }

  def "ignores deployment details that have not yet ran"() {

    given:
    def source = ['deployed': '${deployedServerGroups}']
    def context = [execution: execution]

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.deployed == '${deployedServerGroups}'

    where:
    execution = [
      "stages": [
        [
          "type"         : "deploy",
          "name"         : "Deploy in us-east-1",
          "context"      : [
            "capacity"           : [
              "desired": 1,
              "max"    : 1,
              "min"    : 1
            ],
            "deploy.account.name": "test",
            "stack"              : "test",
            "strategy"           : "highlander",
            "subnetType"         : "internal",
            "suspendedProcesses" : [],
            "terminationPolicies": [
              "Default"
            ],
            "type"               : "linearDeploy"
          ],
          "parentStageId": "dca27ddd-ce7d-42a0-a1db-5b43c6b2f0c7",
        ]
      ]
    ]

  }

  def "is able to parse deployment details correctly from execution"() {

    given:
    def source = ['deployed': '${deployedServerGroups}']
    def context = [execution: execution]

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.deployed.size == 2
    result.deployed.serverGroup == ['flex-test-v043', 'flex-prestaging-v011']
    result.deployed.region == ['us-east-1', 'us-west-1']
    result.deployed[0].ami == 'ami-06362b6e'

    where:
    execution = [
      "context": [
        "deploymentDetails": [
          [
            "ami"      : "ami-06362b6e",
            "amiSuffix": "201505150627",
            "baseLabel": "candidate",
            "baseOs"   : "ubuntu",
            "package"  : "flex",
            "region"   : "us-east-1",
            "storeType": "ebs",
            "vmType"   : "pv"
          ],
          [
            "ami"      : "ami-f759b7b3",
            "amiSuffix": "201505150627",
            "baseLabel": "candidate",
            "baseOs"   : "ubuntu",
            "package"  : "flex",
            "region"   : "us-west-1",
            "storeType": "ebs",
            "vmType"   : "pv"
          ]
        ]
      ],
      "stages" : [
        [
          "status"       : ExecutionStatus.SUCCEEDED,
          "type"         : "deploy",
          "name"         : "Deploy in us-east-1",
          "context"      : [
            "account"             : "test",
            "application"         : "flex",
            "availabilityZones"   : [
              "us-east-1": [
                "us-east-1c",
                "us-east-1d",
                "us-east-1e"
              ]
            ],
            "capacity"            : [
              "desired": 1,
              "max"    : 1,
              "min"    : 1
            ],
            "deploy.account.name" : "test",
            "deploy.server.groups": [
              "us-east-1": [
                "flex-test-v043"
              ]
            ],
            "stack"               : "test",
            "strategy"            : "highlander",
            "subnetType"          : "internal",
            "suspendedProcesses"  : [],
            "terminationPolicies" : [
              "Default"
            ],
            "type"                : "linearDeploy"
          ],
          "parentStageId": "dca27ddd-ce7d-42a0-a1db-5b43c6b2f0c7",
        ],
        [
          "id"     : "dca27ddd-ce7d-42a0-a1db-5b43c6b2f0c7-2-destroyAsg",
          "type"   : "destroyAsg",
          "name"   : "destroyAsg",
          "context": [
          ]
        ],
        [
          "id"           : "68ad3566-4857-4c76-839e-f4afc14410c5-1-Deployinuswest1",
          "type"         : "deploy",
          "name"         : "Deploy in us-west-1",
          "startTime"    : 1431672074613,
          "endTime"      : 1431672487124,
          "status"       : ExecutionStatus.SUCCEEDED,
          "context"      : [
            "account"             : "prod",
            "application"         : "flex",
            "availabilityZones"   : [
              "us-west-1": [
                "us-west-1a",
                "us-west-1c"
              ]
            ],
            "capacity"            : [
              "desired": 1,
              "max"    : 1,
              "min"    : 1
            ],
            "cooldown"            : 10,
            "deploy.account.name" : "prod",
            "deploy.server.groups": [
              "us-west-1": [
                "flex-prestaging-v011"
              ]
            ],
            "keyPair"             : "nf-prod-keypair-a",
            "loadBalancers"       : [
              "flex-prestaging-frontend"
            ],
            "provider"            : "aws",
            "securityGroups"      : [
              "sg-d2c3dfbe",
              "sg-d3c3dfbf"
            ],
            "stack"               : "prestaging",
            "strategy"            : "highlander",
            "subnetType"          : "internal",
            "suspendedProcesses"  : [],
            "terminationPolicies" : [
              "Default"
            ],
            "type"                : "linearDeploy"
          ],
          "parentStageId": "68ad3566-4857-4c76-839e-f4afc14410c5",
          "scheduledTime": 0
        ]
      ]
    ]
  }

  def 'helper method to convert objects into json'() {

    given:
    def source = ['json': '${#toJson( map )}']
    def context = [map: [["v1": "k1"], ["v2": "k2"]]]

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.json == '[{"v1":"k1"},{"v2":"k2"}]'

  }

  @Unroll
  def 'helper method to convert Strings into Integers'() {
    given:
    def source = [intParam: '${#toInt( str )}']
    def context = [str: str]

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.intParam instanceof Integer
    result.intParam == intParam

    where:
    str | intParam
    '0' | 0
    '1' | 1
  }

  @Unroll
  def 'helper method to convert Strings into Floats'() {
    given:
    def source = [floatParam: '${#toFloat( str )}']
    def context = [str: str]

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.floatParam instanceof Float
    result.floatParam == floatParam

    where:
    str   | floatParam
    '7'   | 7f
    '7.5' | 7.5f
  }


  @Unroll
  def 'helper method to convert Strings into Booleans'() {
    given:
    def source = [booleanParam: '${#toBoolean( str )}']
    def context = [str: str]

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.booleanParam instanceof Boolean
    result.booleanParam == booleanParam

    where:
    str     | booleanParam
    'true'  | true
    'false' | false
    null    | false
  }

  @Unroll
  def 'json reader returns a list if the item passed starts with a ['() {
    expect:
    expectedClass.isInstance(ContextUtilities.readJson(json))

    where:
    json               | expectedClass
    '[ "one", "two" ]' | List
    '{ "one":"two" }'  | Map

  }

  def "can find a stage"() {
    given:
    def source = ['stage': '''${#stage('my stage')}''']
    def context = [execution: execution]

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.stage.value == "two"

    where:
    execution = [
      "stages": [
        [
          "name" : "my stage",
          "value": "two"
        ]
      ]
    ]
  }

  def "can find a judgment result"() {
    given:
    def source = ['judgment': '''${#judgment('my stage')}''']
    def context = [execution: execution]

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.judgment == "input"

    where:
    execution = [
      "stages": [
        [
          "type"   : "bake",
          "name"   : "my stage",
          "context": [
            "judgmentInput": "input2"
          ]
        ],
        [
          "type"   : "manualJudgment",
          "name"   : "my stage",
          "context": [
            "judgmentInput": "input"
          ]
        ]
      ]
    ]
  }

}
