/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapterImpl
import com.netflix.spinnaker.orca.clouddriver.InstanceService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisExecutionRepository
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.repository.JobRepository
import org.springframework.context.ApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class QuickPatchStageSpec extends Specification {

  @Shared @AutoCleanup("destroy") EmbeddedRedis embeddedRedis

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
  }

  def cleanup() {
    embeddedRedis.jedis.withCloseable { it.flushDB() }
  }

  Pool<Jedis> jedisPool = embeddedRedis.pool

  @Subject quickPatchStage = Spy(QuickPatchStage)
  def oort = Mock(OortService)

  def oortHelper = Mock(OortHelper)
  def bulkQuickPatchStage = Spy(BulkQuickPatchStage)

  def objectMapper = new OrcaObjectMapper()
  def executionRepository = new JedisExecutionRepository(new NoopRegistry(), jedisPool, 1, 50)
  InstanceService instanceService = Mock(InstanceService)

  void setup() {
    GroovyMock(OortHelper, global: true)

    quickPatchStage.applicationContext = Stub(ApplicationContext) {
      getBean(_) >> { Class type -> type.newInstance() }
    }
    quickPatchStage.objectMapper = objectMapper
    quickPatchStage.steps = new StepBuilderFactory(Stub(JobRepository), Stub(PlatformTransactionManager))
    quickPatchStage.taskTaskletAdapters = [new TaskTaskletAdapterImpl(executionRepository, [])]
    quickPatchStage.oortService = oort
    quickPatchStage.bulkQuickPatchStage = bulkQuickPatchStage
    quickPatchStage.INSTANCE_VERSION_SLEEP = 1
    quickPatchStage.oortHelper = oortHelper
  }

  def "quick patch can't run due to too many asgs"() {
    given:
    def config = [
      application: "deck",
      clusterName: "deck-cluster",
      account    : "account",
      region     : "us-east-1",
      baseOs     : "ubuntu"
    ]

    and:
    def stage = new PipelineStage(new Pipeline(), "quickPatch", config)
    stage.beforeStages = new NeverClearedArrayList()
    stage.afterStages = new NeverClearedArrayList()

    when:
    quickPatchStage.buildSteps(stage)

    then:
    1 * oortHelper.getInstancesForCluster(config, null, true, false) >> { throw new RuntimeException("too many asgs!") }

    and:
    thrown(RuntimeException)

    where:
    asgNames = ["deck-prestaging-v300", "deck-prestaging-v303", "deck-prestaging-v304"]
  }

  def "configures bulk quickpatch"() {
    given:
    def config = [
      application: "deck",
      clusterName: "deck-cluster",
      account    : "account",
      region     : "us-east-1",
      baseOs     : "ubuntu"
    ]

    and:
    1 * oortHelper.getInstancesForCluster(config, null, true, false) >> expectedInstances

    def stage = new PipelineStage(new Pipeline(), "quickPatch", config)
    stage.beforeStages = new NeverClearedArrayList()
    stage.afterStages = new NeverClearedArrayList()

    when:
    quickPatchStage.buildSteps(stage)

    then:
    1 == stage.afterStages.size()

    and:
    stage.afterStages*.stageBuilder.unique() == [bulkQuickPatchStage]

    and:
    with(stage.afterStages[0].context) {
      application == "deck"
      account == "account"
      region == "us-east-1"
      clusterName == "deck-cluster"
      instanceIds == ["i-1234", "i-2345"]
      instances.size() == expectedInstances.size()
      instances.every {
        it.value.hostName == expectedInstances.get(it.key).hostName
        it.value.healthCheck == expectedInstances.get(it.key).healthCheck
      }
    }

    where:
    asgNames = ["deck-prestaging-v300"]
    instance1 = [instanceId: "i-1234", publicDnsName: "foo.com", health: [[foo: "bar"], [healthCheckUrl: "http://foo.com:7001/healthCheck"]]]
    instance2 = [instanceId: "i-2345", publicDnsName: "foo2.com", health: [[foo2: "bar"], [healthCheckUrl: "http://foo2.com:7001/healthCheck"]]]
    expectedInstances = ["i-1234": [hostName: "foo.com", healthCheckUrl: "http://foo.com:7001/healthCheck"], "i-2345": [hostName: "foo2.com", healthCheckUrl: "http://foo.com:7001/healthCheck"]]
  }

  def "configures rolling quickpatch"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "quickPatch", config)
    stage.beforeStages = new NeverClearedArrayList()
    stage.afterStages = new NeverClearedArrayList()

    when:
    quickPatchStage.buildSteps(stage)

    then:
    1 * oortHelper.getInstancesForCluster(config, null, true, false) >> expectedInstances

    and:
    2 == stage.afterStages.size()

    and:
    stage.afterStages*.stageBuilder.unique() == [bulkQuickPatchStage]

    and:
    with(stage.afterStages[0].context) {
      application == "deck"
      account == "account"
      region == "us-east-1"
      clusterName == config.clusterName
      instanceIds == ["i-1234"]
      instances.size() == 1
      instances.every {
        it.value.hostName == expectedInstances.get(it.key).hostName
        it.value.healthCheck == expectedInstances.get(it.key).healthCheck
      }
    }

    and:
    with(stage.afterStages[1].context) {
      application == "deck"
      account == "account"
      region == "us-east-1"
      clusterName == config.clusterName
      instanceIds == ["i-2345"]
      instances.size() == 1
      instances.every {
        it.value.hostName == expectedInstances.get(it.key).hostName
        it.value.healthCheck == expectedInstances.get(it.key).healthCheck
      }
    }

    where:
    asgNames = ["deck-prestaging-v300"]
    instance1 = [instanceId: "i-1234", publicDnsName: "foo.com", health: [[foo: "bar"], [healthCheckUrl: "http://foo.com:7001/healthCheck"]]]
    instance2 = [instanceId: "i-2345", publicDnsName: "foo2.com", health: [[foo2: "bar"], [healthCheckUrl: "http://foo2.com:7001/healthCheck"]]]
    expectedInstances = ["i-1234": [hostName: "foo.com", healthCheckUrl: "http://foo.com:7001/healthCheck"], "i-2345": [hostName: "foo2.com", healthCheckUrl: "http://foo.com:7001/healthCheck"]]

    config | _
    [application: "deck", clusterName: "deck-cluster", account: "account", region: "us-east-1", rollingPatch: true, baseOs: "ubuntu"] | _
    [application: "deck", clusterName: "deck", account: "account", region: "us-east-1", rollingPatch: true, baseOs: "ubuntu"] | _
  }

  def "some instances are skipped due to skipUpToDate"() {
    given:
    def config = [
      application : application,
      clusterName : "deck-cluster",
      account     : account,
      region      : region,
      baseOs      : "ubuntu",
      skipUpToDate: true,
      patchVersion: "1.2",
      package     : "deck"
    ]
    def stage = new PipelineStage(null, "quickPatch", config)

    and:
    stage.beforeStages = new NeverClearedArrayList()
    stage.afterStages = new NeverClearedArrayList()
    expectedInstances.size() * quickPatchStage.createInstanceService(_) >> instanceService
    1 * instanceService.getCurrentVersion(_) >>> new Response(
      "foo", 200, "ok", [],
      new TypedByteArray(
        "application/json",
        objectMapper.writeValueAsBytes(["version": "1.21"])
      )
    )
    1 * instanceService.getCurrentVersion(_) >>> new Response(
      "foo", 200, "ok", [],
      new TypedByteArray(
        "application/json",
        objectMapper.writeValueAsBytes(["version": "1.2"])
      )
    )
    when:
    quickPatchStage.buildSteps(stage)

    then:
    1 * oortHelper.getInstancesForCluster(config, null, true, false) >> expectedInstances

    and:
    stage.context.skippedInstances.'i-2345'
    stage.afterStages.size() == 1
    with(stage.afterStages[0].context) {
      application == application
      account == account
      region == region
      clusterName == config.clusterName
      instanceIds == ["i-1234"]
      instances.size() == 1
      instances.every {
        it.value.hostName == expectedInstances.get(it.key).hostName
        it.value.healthCheck == expectedInstances.get(it.key).healthCheck
      }
    }
    where:
    application = "deck"
    region = "us-east-1"
    account = "account"
    asgNames = ["deck-prestaging-v300"]
    instance1 = [instanceId: "i-1234", publicDnsName: "foo.com", health: [[foo: "bar"], [healthCheckUrl: "http://foo.com:7001/healthCheck"]]]
    instance2 = [instanceId: "i-2345", publicDnsName: "foo2.com", health: [[foo2: "bar"], [healthCheckUrl: "http://foo2.com:7001/healthCheck"]]]
    expectedInstances = ["i-1234": [hostName: "foo.com", healthCheckUrl: "http://foo.com:7001/healthCheck"], "i-2345": [hostName: "foo2.com", healthCheckUrl: "http://foo.com:7001/healthCheck"]]
  }

  def "skipUpToDate with getVersion retries"() {
    given:
    def config = [
      application : application,
      clusterName : "deck-cluster",
      account     : account,
      region      : region,
      baseOs      : "ubuntu",
      skipUpToDate: true,
      patchVersion: "1.2",
      package     : "deck"
    ]
    def stage = new PipelineStage(null, "quickPatch", config)
    1 * quickPatchStage.createInstanceService(_) >> instanceService
    4 * instanceService.getCurrentVersion(_) >> { throw new RetrofitError(null, null, null, null, null, null, null) }
    1 * instanceService.getCurrentVersion(_) >>> new Response(
      "foo", 200, "ok", [],
      new TypedByteArray(
        "application/json",
        objectMapper.writeValueAsBytes(["version": "1.21"])
      )
    )

    when:
    quickPatchStage.buildSteps(stage)

    then:
    1 * oortHelper.getInstancesForCluster(config, null, true, false) >> expectedInstances

    where:
    application = "deck"
    region = "us-east-1"
    account = "account"
    asgNames = ["deck-prestaging-v300"]
    expectedInstances = ["i-1234": [hostName: "foo.com", healthCheckUrl: "http://foo.com:7001/healthCheck"]]
  }
}
