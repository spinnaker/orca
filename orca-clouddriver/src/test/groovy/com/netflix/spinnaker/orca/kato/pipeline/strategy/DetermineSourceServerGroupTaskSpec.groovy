/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline.strategy

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.converter.Converter
import retrofit.mime.TypedString
import spock.lang.Specification

class DetermineSourceServerGroupTaskSpec extends Specification {

  void 'should include source in context'() {
    given:
    Stage stage = new Stage<>(new Pipeline(), 'deploy', 'deploy', [
      account          : account,
      application      : 'foo',
      availabilityZones: [(region): []]])

    def resolver = Mock(SourceResolver)

    when:
    def result = new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    1 * resolver.getSource(_) >> new StageData.Source(account: account, region: region, asgName: asgName, serverGroupName: asgName)

    and:
    result.stageOutputs.source.account == account
    result.stageOutputs.source.region == region
    result.stageOutputs.source.asgName == asgName
    result.stageOutputs.source.serverGroupName == asgName
    result.stageOutputs.source.useSourceCapacity == null

    where:
    account = 'test'
    region = 'us-east-1'
    asgName = 'foo-test-v000'
  }

  void 'should useSourceCapacity from context if not provided in Source'() {
    given:
    Stage stage = new Stage<>(new Pipeline(), 'deploy', 'deploy', [useSourceCapacity: contextUseSourceCapacity, account: account, application: application, availabilityZones: [(region): []]])

    def resolver = Mock(SourceResolver)

    when:
    def result = new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    1 * resolver.getSource(stage) >> new StageData.Source(account: account, region: region, asgName: "$application-v001", useSourceCapacity: sourceUseSourceCapacity)

    and:
    result.stageOutputs.source.useSourceCapacity == expectedUseSourceCapacity

    where:
    account = 'test'
    region = 'us-east-1'
    application = 'foo'


    contextUseSourceCapacity | sourceUseSourceCapacity | expectedUseSourceCapacity
    null | null | null
    null | true | true
    false | true | true
    false | null | false
    true | null | true
  }

  void 'should NOT fail if there is region and no availabilityZones in context'() {
    given:
    Stage stage = new Stage<>(new Pipeline(), 'deploy', 'deploy', [
      account    : 'test',
      region    : 'us-east-1',
      application: 'foo'])

    def resolver = Mock(SourceResolver)

    when:
    new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    notThrown(IllegalStateException)
  }

  void 'should NOT fail if there is source and no region and no availabilityZones in context'() {
    given:
    Stage stage = new Stage<>(new Pipeline(), 'deploy', 'deploy', [
      account    : 'test',
      source: [ region    : 'us-east-1', account: 'test', asgName: 'foo-test-v000' ],
      application: 'foo'])

    def resolver = Mock(SourceResolver)

    when:
    new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    notThrown(IllegalStateException)
  }

  void 'should fail if there is no availabilityZones and no region in context'() {
    given:
    Stage stage = new Stage<>(new Pipeline(), 'deploy', 'deploy', [
      account    : 'test',
      application: 'foo'])

    def resolver = Mock(SourceResolver)

    when:
    new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    def ex = thrown(IllegalStateException)
    ex.message == "No 'source' or 'region' or 'availabilityZones' in stage context"
  }

  void 'should retry on exception from source resolver'() {
    given:
    Exception expected = new Exception('kablamo')
    Stage stage = new Stage<>(new Pipeline(), 'deploy', 'deploy', [
      account          : 'test',
      application      : 'foo',
      availabilityZones: ['us-east-1': []]])

    def resolver = Mock(SourceResolver)

    when:
    def result = new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    1 * resolver.getSource(_) >> { throw expected }

    result.status == ExecutionStatus.RUNNING
    result.stageOutputs.lastException.contains(expected.message)
    result.stageOutputs.attempt == 2
    result.stageOutputs.consecutiveNotFound == 0
  }

  void 'should reset consecutiveNotFound on non 404 exception'() {
    given:
    Exception expected = new Exception('kablamo')
    Stage stage = new Stage<>(new Pipeline(), 'deploy', 'deploy', [
      account            : 'test',
      application        : 'foo',
      consecutiveNotFound: 3,
      availabilityZones  : ['us-east-1': []]])

    def resolver = Mock(SourceResolver)

    when:
    def result = new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    1 * resolver.getSource(_) >> { throw expected }

    result.status == ExecutionStatus.RUNNING
    result.stageOutputs.lastException.contains(expected.message)
    result.stageOutputs.attempt == 2
    result.stageOutputs.consecutiveNotFound == 0
  }

  void 'should fail after MAX_ATTEMPTS'() {
    Exception expected = new Exception('kablamo')
    Stage stage = new Stage<>(new Pipeline(), 'deploy', 'deploy', [
      account          : 'test',
      application      : 'foo',
      attempt          : DetermineSourceServerGroupTask.MAX_ATTEMPTS,
      availabilityZones: ['us-east-1': []]])

    def resolver = Mock(SourceResolver)

    when:
    new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    1 * resolver.getSource(_) >> { throw expected }

    def ex = thrown(IllegalStateException)
    ex.cause.is expected
  }

  void "should #status after #attempt consecutive 404s with useSourceCapacity #useSourceCapacity"() {
    Response response = new Response("http://oort.com", 404, "NOT_FOUND", [], new TypedString(""))
    Exception expected = RetrofitError.httpError("http://oort.com", response, Stub(Converter), Response)
    Stage stage = new Stage<>(new Pipeline(), 'deploy', 'deploy', [
      account            : 'test',
      application        : 'foo',
      useSourceCapacity  : useSourceCapacity,
      attempt            : attempt,
      consecutiveNotFound: attempt,
      availabilityZones  : ['us-east-1': []]])

    def resolver = Mock(SourceResolver)

    when:
    def taskResult = new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    1 * resolver.getSource(_) >> { throw expected }

    taskResult.status == status

    where:
    status                    | useSourceCapacity | attempt
    ExecutionStatus.SUCCEEDED | false             | DetermineSourceServerGroupTask.MIN_CONSECUTIVE_404
    ExecutionStatus.RUNNING   | true              | DetermineSourceServerGroupTask.MIN_CONSECUTIVE_404
    ExecutionStatus.RUNNING   | false             | 1
    ExecutionStatus.RUNNING   | true              | 1
  }

  def 'should fail if no source resolved and useSourceCapacity requested'() {
    Stage stage = new Stage<>(new Pipeline(), 'deploy', 'deploy', [
      account            : 'test',
      application        : 'foo',
      useSourceCapacity  : true,
      availabilityZones  : ['us-east-1': []]])

    def resolver = Mock(SourceResolver)

    when:
    def result = new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    1 * resolver.getSource(_) >> null

    result.status == ExecutionStatus.RUNNING
    result.stageOutputs.lastException.contains("Cluster is configured to copy capacity from the current server group")
  }
}
