package com.netflix.spinnaker.orca.echo.spring

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.listeners.Persister
import com.netflix.spinnaker.orca.listeners.StageListener
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task
import org.springframework.beans.factory.annotation.Autowired

/**
 * Converts execution events to Echo events.
 */
@CompileStatic
@Slf4j
class EchoNotifyingStageListener implements StageListener {

  private final EchoService echoService

  @Autowired
  EchoNotifyingStageListener(EchoService echoService) {
    this.echoService = echoService
  }

  @Override
  void beforeTask(Persister persister, Stage stage, Task task) {
    if (task.status == ExecutionStatus.NOT_STARTED) {
      recordEvent('task', 'starting', stage, task)
      if (task.stageStart) {
        recordEvent("stage", "starting", stage, task)
      }
    }
  }

  @Override
  void afterTask(Persister persister, Stage stage, Task task, ExecutionStatus executionStatus, boolean wasSuccessful) {
    if (executionStatus == ExecutionStatus.RUNNING) {
      return
    }

    recordEvent('task', (wasSuccessful ? "complete" : "failed"), stage, task)
    if (stage.execution instanceof Pipeline) {
      if (wasSuccessful) {
        // TODO: latter part of condition is deprecated
        if (task.stageEnd || task.name.contains('stageEnd')) {
          recordEvent('stage', 'complete', stage, task)
          // TODO: deprecated
        } else if (task.name.contains('stageStart')) {
          recordEvent('stage', 'starting', stage, task)
        }
      } else {
        recordEvent('stage', 'failed', stage, task)
      }
    }
  }

  private void recordEvent(String type, String phase, Stage stage, Task task) {
    try {
      echoService.recordEvent(
        details: [
          source     : "orca",
          type       : "orca:${type}:${phase}".toString(),
          application: stage.execution.application
        ],
        content: [
          standalone : stage.execution instanceof Orchestration,
          canceled   : stage.execution.canceled,
          context    : stage.context,
          taskName   : task.name,
          startTime  : stage.startTime,
          endTime    : stage.endTime,
          execution  : stage.execution,
          executionId: stage.execution.id
        ]
      )
    } catch (Exception e) {
      log.error("Failed to send ${type} event ${phase} ${stage.execution.id} ${task.name}", e)
    }
  }

  @Override
  int getOrder() {
    return 1
  }
}
