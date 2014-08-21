/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.tasks

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.Task
import com.netflix.spinnaker.orca.kato.api.TaskId
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
class MonitorKatoTask implements RetryableTask {

  long backoffPeriod = 1000
  long timeout = 300000

  @Autowired
  KatoService kato

  @Override
  TaskResult execute(TaskContext context) {
    TaskId taskId = context.inputs."kato.task.id" as TaskId
    def katoTask = kato.lookupTask(taskId.id).toBlocking().first()
    def status = katoStatusToTaskStatus(katoTask.status)
    if (status == TaskResult.Status.FAILED) {
      new DefaultTaskResult(status)
    } else if (status == TaskResult.Status.SUCCEEDED) {
      new DefaultTaskResult(status, ["deploy.server.groups": getServerGroupNames(katoTask)])
    } else {
      new DefaultTaskResult(TaskResult.Status.RUNNING)
    }
  }

  private static TaskResult.Status katoStatusToTaskStatus(Task.Status katoStatus) {
    if (katoStatus.failed) {
      return TaskResult.Status.FAILED
    } else if (katoStatus.completed) {
      return TaskResult.Status.SUCCEEDED
    } else {
      return TaskResult.Status.RUNNING
    }
  }

  // This is crappy logic
  // TODO clean this up in Kato
  @CompileStatic(TypeCheckingMode.SKIP)
  private static Map<String, List<String>> getServerGroupNames(Task task) {
    def result = [:]
    def resultObjects = task.resultObjects ?: []
    resultObjects.removeAll([null])
    resultObjects.each {
      if (it.serverGroupNames) {
        it.serverGroupNames.each {
          def parts = it.split(':')
          def region = parts[0]
          def serverGroup = parts[1]
          if (!result.containsKey(region)) {
            result[region] = []
          }
          result[region] << serverGroup
        }
      }
    }
    result
  }
}
