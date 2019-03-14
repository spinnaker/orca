package com.netflix.spinnaker.orca.locks

import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.test.model.ExecutionBuilder
import spock.lang.Specification

class LockContextSpec extends Specification {

  def "builder uses explicitly provided id in when present"() {
    given:
    def builder = new LockContext.LockContextBuilder.LockValueBuilder(application, type, explicitId, stage)

    expect:
    builder.build() == expected

    where:
    application = 'app'
    type = 'pipeline'
    explicitId = 'bacon'

    stage = ExecutionBuilder.stage {
      context = [:]
    }

    expectedId = explicitId
    expected = new LockManager.LockValue(application, type, expectedId)
  }

  def "builder uses execution id in simple execution"() {
    given:
    def builder = new LockContext.LockContextBuilder.LockValueBuilder(application, type, explicitId, stage)

    expect:
    builder.build() == expected

    where:
    application = 'app'
    type = 'pipeline'
    explicitId = null

    stage = ExecutionBuilder.stage {
      context = [:]
    }

    expectedId = stage.execution.id
    expected = new LockManager.LockValue(application, type, expectedId)
  }

  def "builder traverses up the hierarchy when execution is triggered by a PipelineTrigger"() {
    given:
    def application = 'app'
    def type = 'pipeline'
    def explicitId = null

    def parentStage = ExecutionBuilder.stage {
      type = 'pipeline'
    }

    def exec = ExecutionBuilder.pipeline {
      trigger = makeTrigger(parentStage)
      ExecutionBuilder.stage {
        type = 'acquireLock'
      }
    }

    def stage = exec.stages[0]

    def expectedId = parentStage.execution.id
    def expected = new LockManager.LockValue(application, type, expectedId)

    expect:
    new LockContext.LockContextBuilder.LockValueBuilder(application, type, explicitId, stage).build() == expected
  }

  def "builder traverses up the hierarchy multiple levels when execution is triggered by a PipelineTrigger"() {
    given:
    def builder = new LockContext.LockContextBuilder.LockValueBuilder(application, type, explicitId, stage)

    expect:
    builder.build() == expected

    where:
    application = 'app'
    type = 'pipeline'
    explicitId = null

    grandParentStage = ExecutionBuilder.stage {
      type = 'pipeline'
    }

    parentExec = childExec(grandParentStage)

    exec = childExec(parentExec.stages[0], 'acquireLock')
    stage = exec.stages[0]

    expectedId = grandParentStage.execution.id
    expected = new LockManager.LockValue(application, type, expectedId)
  }

  private PipelineTrigger makeTrigger(Stage parentStage) {
    return new PipelineTrigger('pipeline', null, '[anonymous', [:], [], [], false, false, false, parentStage.execution, parentStage.id)
  }

  private Execution childExec(Stage parentStage, String stageType = 'pipeline') {
    return ExecutionBuilder.pipeline {
      trigger = makeTrigger(parentStage)
      ExecutionBuilder.stage {
        type = stageType
      }
    }


  }
}
