package com.netflix.spinnaker.orca.pipeline.util

import com.netflix.spinnaker.orca.ExecutionStatus
import spock.lang.Specification
import spock.lang.Unroll

class ContextParameterProcessorSpec extends Specification {

  @Unroll
  def "should #processAttributes"() {
    given:
    def source = ['test': sourceValue]
    def context = ['testArray': ['good', ['arrayVal': 'bad'], [['one': 'two']]], replaceMe: 'newValue', 'h1': [h1: 'h1Val'], hierarchy: [h2: 'hierarchyValue', h3: [h4: 'h4Val']],
                   replaceTest: 'stack-with-hyphens']

    when:
    def result = ContextParameterProcessor.process(source, context)

    then:
    result.test == expectedValue

    where:
    processAttributes                               | sourceValue                           | expectedValue
    'leave strings alone'                           | 'just a string'                       | 'just a string'
    'transform simple properties'                   | '${replaceMe}'                        | 'newValue'
    'transform properties with dots'                | '${h1.h1}'                            | 'h1Val'
    'transform embedded properties'                 | '${replaceMe} and ${replaceMe}'       | 'newValue and newValue'
    'transform hierarchical values'                 | '${hierarchy.h2}'                     | 'hierarchyValue'
    'transform nested hierarchical values'          | '${hierarchy.h3.h4}'                  | 'h4Val'
    'leave unresolvable values'                     | '${notResolvable}'                    | '${notResolvable}'
    'can get a value in an array'                   | '${testArray[0]}'                     | 'good'
    'can get a value in an map within an array'     | '${testArray[1].arrayVal}'            | 'bad'
    'can get a value in an array within an array'   | '${testArray[2][0].one}'              | 'two'
    'can support SPEL expression'                   | '${ h1.h1 == "h1Val" }'               | true
    'can support SPEL defaults'                     | '${ h1.h2  ?: 60 }'                   | 60
    'can support SPEL string methods'               | '${ replaceTest.replaceAll("-","") }' | 'stackwithhyphens'
    'can make any string alphanumerical for deploy' | '${ #alphanumerical(replaceTest) }'   | 'stackwithhyphens'
  }

  def "should replace the keys in a map"() {
    given:
    def source = ['${replaceMe}': 'somevalue', '${replaceMe}again': ['cats': 'dogs']]

    when:
    def result = ContextParameterProcessor.process(source, [replaceMe: 'newVal'])

    then:
    result.newVal == 'somevalue'
    result.newValagain?.cats == 'dogs'
  }

  def "should be able to swap out a SPEL expression of a string with other types"() {
    def source = ['test': ['k1': '${var1}', 'k2': '${map1}']]
    def context = [var1: 17, map1: [map1key: 'map1val']]

    when:
    def result = ContextParameterProcessor.process(source, context)

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
    def result = ContextParameterProcessor.process(source, context)

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
    def result = ContextParameterProcessor.process(source, context)

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
    def result = ContextParameterProcessor.process(source, context)

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
    def result = ContextParameterProcessor.process(source, context)

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
    def result = ContextParameterProcessor.process(source, context)

    then:
    result.json == '[{"v1":"k1"},{"v2":"k2"}]'

  }

  @Unroll
  def 'helper method to convert Strings into Integers'() {
    given:
    def source = [intParam: '${#toInt( str )}']
    def context = [str: str]

    when:
    def result = ContextParameterProcessor.process(source, context)

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
    def result = ContextParameterProcessor.process(source, context)

    then:
    result.floatParam instanceof Float
    result.floatParam == floatParam

    where:
    str   | floatParam
    '7'   | 7f
    '7.5' | 7.5f
  }

}
