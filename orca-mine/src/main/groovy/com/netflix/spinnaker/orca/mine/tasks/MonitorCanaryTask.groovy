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

package com.netflix.spinnaker.orca.mine.tasks

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

import java.util.concurrent.TimeUnit

@Component
@Slf4j
class MonitorCanaryTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  long backoffPeriod = 10000
  long timeout = TimeUnit.DAYS.toMillis(2)

  @Autowired
  MineService mineService
  @Autowired
  KatoService katoService

  @Override
  TaskResult execute(Stage stage) {
    Map context = stage.context
    Map outputs = [
      canary : context.canary
    ]

    try {
      outputs << [
        canary : mineService.getCanary(context.canary.id)
      ]
    } catch (RetrofitError e) {
      log.error("Exception occurred while getting canary with id ${context.canary.id} from mine service", e)
      return new DefaultTaskResult(ExecutionStatus.RUNNING, outputs)
    }

    if (outputs.canary.status?.complete) {
      log.info("Canary $stage.id complete")
      return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, outputs, outputs)
    }

    if (outputs.canary.health?.health == 'UNHEALTHY' && !context.disableRequested) {
      log.info("Canary $stage.id unhealthy, disabling")
      def operations = DeployedClustersUtil.toKatoAsgOperations('disableAsgDescription', stage.context)
      log.info "Disabling canary $stage.id with ${operations}"
      katoService.requestOperations(operations).toBlocking().first()
      outputs.disableRequested = true
    } else {
      outputs.disableRequested = false
    }

    Map scaleUp = context.scaleUp
    if (scaleUp && scaleUp.enabled && !scaleUp.complete) {
      int capacity = scaleUp.capacity as Integer
      if (System.currentTimeMillis() - outputs.canary.launchedDate > TimeUnit.MINUTES.toMillis(scaleUp.delay as Long)) {
        def resizeCapacity = [min: capacity, max: capacity, desired: capacity]
        def resizeOps = DeployedClustersUtil.toKatoAsgOperations('resizeAsgDescription', stage.context).collect { it.resizeAsgDescription.capacity = resizeCapacity; it  }
        outputs.scaleUp = scaleUp
        outputs.scaleUp.katoId = katoService.requestOperations(resizeOps).toBlocking().first().id
        outputs.scaleUp.complete = true
        log.info("Canary $stage.id scale up requested")
      }
    }

    log.info("Canary in progress: ${outputs.canary}")
    return new DefaultTaskResult(ExecutionStatus.RUNNING, outputs)
  }
}
