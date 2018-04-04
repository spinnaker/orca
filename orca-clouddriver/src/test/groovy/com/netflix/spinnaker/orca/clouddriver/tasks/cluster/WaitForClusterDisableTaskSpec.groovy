package com.netflix.spinnaker.orca.clouddriver.tasks.cluster

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForRequiredInstancesDownTask
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED

import java.util.concurrent.TimeUnit

class WaitForClusterDisableTaskSpec extends Specification {
    def oortHelper = Mock(OortHelper)
    def katoService = Mock(KatoService)
    def objectMapper = new ObjectMapper()

    @Shared def region = "theregion"
    @Shared def clusterName = "thecluster"


    @Shared
    ServerGroupCreator aCreator = Stub(ServerGroupCreator) {
        getCloudProvider() >> "aCloud"
        isKatoResultExpected() >> false
        getOperations(_) >> [["aOp": "foo"]]
    }

    @Subject def task = new WaitForClusterDisableTask([aCreator])

    def instance(name, platformHealthState = 'Unknown') {
        return [name: name,
                launchTime: null,
                health: [[healthClass: 'platform', type: 'platformHealthType', state: platformHealthState]],
                healthState: null,
                zone: 'thezone']
    }

    def serverGroup(name, region, Map other) {
        return [
                name  : name,
                region: region,
                health: null
        ] + other
    }

    @Unroll
    def "status=#status when dsgregion=#dsgregion, oldServerGroupDisabled=#oldServerGroupDisabled, desiredPercentage=#desiredPercentage, interestingHealthProviderNames=#interestingHealthProviderNames"() {
        setup:
        def stage = new Stage(Execution.newPipeline("orca"), "test", [
                cluster                                     : clusterName,
                credentials                                 : "theaccount",
                "deploy.server.groups"                      : [
                        (dsgregion): ["$clusterName-$oldServerGroup".toString()]
                ],
                (desiredPct ? "desiredPercentage" : "blerp"): desiredPct,
                interestingHealthProviderNames              : interestingHealthProviderNames
        ])
        stage.setStartTime(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1))

        oortHelper.getCluster(*_) >> [
                name: clusterName,
                serverGroups: [
                        serverGroup("$clusterName-v050".toString(), "us-west-1", [:]),
                        serverGroup("$clusterName-v051".toString(), "us-west-1", [:]),
                        serverGroup("$clusterName-$newServerGroup".toString(), region, [:]),
                        serverGroup("$clusterName-$oldServerGroup".toString(), region, [
                                disabled: oldSGDisabled,
                                capacity: [desired: desired],
                                instances: [
                                        instance('i-1', platformHealthState),
                                        instance('i-2', platformHealthState),
                                        instance('i-3', platformHealthState),
                                ]
                        ])
                ]
        ]

        task.oortHelper = oortHelper
        task.waitForRequiredInstancesDownTask = new WaitForRequiredInstancesDownTask()

        when:
        TaskResult result = task.execute(stage)

        then:
        result.getStatus() == status

        where:
        dsgregion | oldSGDisabled | desired | desiredPct | interestingHealthProviderNames | platformHealthState || status
        "other"   | false         | 3       | null       | ['platformHealthType']         | 'Unknown'           || SUCCEEDED  // exercises if (!remainingDeployServerGroups)

        // tests for isDisabled==true
        region    | true          | 3       | null       | ['platformHealthType']         | 'Unknown'           || SUCCEEDED  // exercises if (isDisabled) short-circuit
        region    | true          | 3       | null       | ['platformHealthType']         | 'NotUnknown'        || RUNNING    // wait for instances down even if cluster is disabled
        region    | true          | 3       | 100        | ['platformHealthType']         | 'NotUnknown'        || RUNNING    // also wait for instances down with a desiredPct
        region    | true          | 4       | 50         | ['platformHealthType']         | 'Unknown'           || SUCCEEDED

        // tests for isDisabled==false, no desiredPct
        region    | false         | 3       | null       | []                             | 'Unknown'           || SUCCEEDED  // no health providers to check so short-circuits early
        region    | false         | 3       | null       | null                           | 'Unknown'           || RUNNING    // FIXME also no health providers to check, should probably short-circuit early
        region    | false         | 3       | null       | ['platformHealthType']         | 'Unknown'           || SUCCEEDED  // considered complete because only considers the platform health
        region    | false         | 3       | null       | ['strangeHealthType']          | 'Unknown'           || RUNNING    // can't complete if we need to monitor an unknown health provider

        // tests for waitForRequiredInstancesDownTask.hasSucceeded
        region    | false         | 3       | 100        | null                           | 'Unknown'           || SUCCEEDED  // no other health providers than platform, and it looks down
        region    | false         | 3       | 100        | null                           | 'NotUnknown'        || RUNNING    // no other health providers than platform, and it looks NOT down
        region    | false         | 4       | 100        | ['platformHealthType']         | 'Unknown'           || RUNNING    // can't reach count(someAreDownAndNoneAreUp) >= targetDesiredSize
        region    | false         | 4       | 50         | ['platformHealthType']         | 'Unknown'           || SUCCEEDED  // all look down, and we want at least 2 down so we're done

        oldServerGroup = "v167"
        newServerGroup = "v168"
    }

    @Unroll
    def "fails with '#message' when clusterData=#clusterData"() {
        setup:
        def stage = new Stage(Execution.newPipeline("orca"), "test", [
                "deploy.server.groups": [
                        (region): ["$clusterName-v42".toString()]
                ]
        ])

        oortHelper.getCluster(*_) >> clusterData
        task.oortHelper = oortHelper

        when:
        TaskResult result = task.execute(stage)

        then:
        IllegalStateException e = thrown()
        e.message.startsWith(message)

        where:
        clusterData << [
                Optional.empty(),
                [name: clusterName, serverGroups: []]
        ]

        message << [
                'no cluster details found',
                'no server groups found'
        ]

    }
}

