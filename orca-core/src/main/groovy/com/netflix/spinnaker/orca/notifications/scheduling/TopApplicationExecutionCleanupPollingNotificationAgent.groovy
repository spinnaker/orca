/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.notifications.scheduling

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.kork.eureka.EurekaStatusChangedEvent
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import rx.Observable
import rx.Scheduler
import rx.Subscription
import rx.functions.Func1
import rx.schedulers.Schedulers

import javax.annotation.PreDestroy
import java.util.concurrent.TimeUnit

import static com.netflix.appinfo.InstanceInfo.InstanceStatus.UP

@Slf4j
@Component
@ConditionalOnExpression(value = '${pollers.topApplicationExecutionCleanup.enabled:false}')
class TopApplicationExecutionCleanupPollingNotificationAgent implements ApplicationListener<EurekaStatusChangedEvent> {

  private Scheduler scheduler = Schedulers.io()
  private Subscription subscription

  private Func1<Execution, Boolean> filter = { Execution execution ->
    execution.status.complete || execution.buildTime < (new Date() - 31).time
  }
  private Func1<Execution, Map> mapper = { Execution execution ->
    def pipelineConfigId = execution instanceof Pipeline ? ((Pipeline) execution).pipelineConfigId : null
    [id: execution.id, startTime: execution.startTime, pipelineConfigId: pipelineConfigId, status: execution.status]
  }

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  ExecutionRepository executionRepository

  @Autowired
  Pool<Jedis> jedisPool

  @Value('${pollers.topApplicationExecutionCleanup.intervalMs:3600000}')
  long pollingIntervalMs

  @Value('${pollers.topApplicationExecutionCleanup.threshold:2500}')
  int threshold

  @PreDestroy
  void stopPolling() {
    subscription?.unsubscribe()
  }

  @Override
  void onApplicationEvent(EurekaStatusChangedEvent event) {
    event.statusChangeEvent.with {
      if (it.status == UP) {
        log.info("Instance is $it.status... starting top application execution cleanup")
        startPolling()
      } else if (it.previousStatus == UP) {
        log.warn("Instance is $it.status... stopping top application execution cleanup")
        stopPolling()
      }
    }
  }

  private void startPolling() {
    subscription = Observable
      .timer(pollingIntervalMs, TimeUnit.MILLISECONDS, scheduler)
      .repeat()
      .subscribe({ Long interval -> tick() })
  }

  @PackageScope
  @VisibleForTesting
  void tick() {
    def jedis = jedisPool.resource
    try {
      jedis.keys("orchestration:app:*").each { String id ->
        if (jedis.scard(id) > threshold) {
          def (type, ignored, application) = id.split(":")
          switch (type) {
            case "orchestration":
              log.info("Cleaning up orchestration executions (application: ${application}, threshold: ${threshold})")

              def executionCriteria = new ExecutionRepository.ExecutionCriteria(limit: Integer.MAX_VALUE)
              cleanup(executionRepository.retrieveOrchestrationsForApplication(application, executionCriteria), application, "orchestration")
              break
            default:
              log.error("Unable to cleanup executions, unsupported type: ${type}")
          }
        }
      }
    } catch (Exception e) {
      log.error("Cleanup failed", e)
    } finally {
      jedisPool.returnResource(jedis)
    }
  }

  private void cleanup(Observable<Execution> observable, String application, String type) {
    def executions = observable.filter(filter).map(mapper).toList().toBlocking().single().sort { it.startTime }
    if (executions.size() > threshold) {
      executions[0..(executions.size() - threshold - 1)].each {
        def startTime = it.startTime ?: (it.buildTime ?: 0)
        log.info("Deleting ${type} execution ${it.id} (startTime: ${new Date(startTime)}, application: ${application}, pipelineConfigId: ${it.pipelineConfigId}, status: ${it.status})")
        switch (type) {
          case "orchestration":
            executionRepository.deleteOrchestration(it.id as String)
            break
          default:
            throw new IllegalArgumentException("Unsupported type '${type}'")
        }
      }
    }
  }
}
