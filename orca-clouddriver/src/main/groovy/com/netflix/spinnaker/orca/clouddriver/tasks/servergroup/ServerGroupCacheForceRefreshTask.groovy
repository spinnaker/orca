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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import java.time.Clock
import java.util.concurrent.TimeUnit
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheStatusService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED

@Component
@Slf4j
class ServerGroupCacheForceRefreshTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  static final String REFRESH_TYPE = "ServerGroup"

  long backoffPeriod = TimeUnit.SECONDS.toMillis(10)
  long timeout = TimeUnit.MINUTES.toMillis(15)

  long autoSucceedAfterMs = TimeUnit.MINUTES.toMillis(12)

  @Autowired
  CloudDriverCacheStatusService cacheStatusService

  @Autowired
  CloudDriverCacheService cacheService

  @Autowired
  ObjectMapper objectMapper

  Clock clock = Clock.systemUTC()

  @Override
  TaskResult execute(Stage stage) {

    if ((clock.millis() - stage.startTime) > autoSucceedAfterMs) {
      /*
       * If an issue arises performing a refresh, wait at least 10 minutes (the default ttl of a cache record) before
       * short-circuiting and succeeding.
       *
       * Under normal operations, this refresh task should complete sub-minute.
       */
      log.warn("After {}ms no progress has been made with the force cache refresh. Shorting the circuit.",
        clock.millis() - stage.startTime
      )
      return new TaskResult(SUCCEEDED, ["shortCircuit": true])
    }

    def account = getCredentials(stage)
    def cloudProvider = getCloudProvider(stage)
    def stageData = stage.mapTo(StageData)

    def optionalTaskResult = performForceCacheRefresh(account, cloudProvider, stageData)
    if (optionalTaskResult.present) {
      return optionalTaskResult.get()
    }

    boolean allAreComplete = processPendingForceCacheUpdates(stage.execution.id, account, cloudProvider, stageData, stage.startTime)

    if (allAreComplete) {
      log.debug("Force cache refresh is complete. Stage reset.")
      // ensure clean stage data such that a subsequent ServerGroupCacheForceRefresh (in this stage) starts fresh
      stageData.reset()
    }

    return new TaskResult(allAreComplete ? SUCCEEDED : RUNNING, convertAndStripNullValues(stageData))
  }

  /**
   * Every deployed server group should be force cache refreshed.
   *
   * An HTTP 200 response indicates that the force cache operation has completed and there is no need for additional
   * polling. Long term, the expectation is that all caching agents will be asynchronous.
   *
   * An HTTP 202 response indicates that the force cache operation has been queued and will complete at some point in
   * the near future.
   */
  private Optional<TaskResult> performForceCacheRefresh(String account, String cloudProvider, StageData stageData) {
    def zone = stageData.zone

    def refreshableServerGroups = stageData.deployServerGroups.collect { region, serverGroups ->
      serverGroups.findResults { String serverGroup ->
        def model = [asgName: serverGroup, serverGroupName: serverGroup, region: region, account: account]
        if (zone) {
          model.zone = zone
        }

        return !stageData.refreshedServerGroups.contains(model) ? model : null
      }
    }.flatten()

    if (!refreshableServerGroups) {
      return Optional.empty()
    }

    boolean allUpdatesApplied = false
    refreshableServerGroups.each { Map<String, String> model ->
      try {
        log.info("Force cache refresh POST a cache refresh request to clouddriver with model: ${model}")
        def response = cacheService.forceCacheUpdate(cloudProvider, REFRESH_TYPE, model)
        if (response.status == HttpURLConnection.HTTP_OK) {
          // cache update was applied immediately, no need to poll for completion
          allUpdatesApplied = true
        }

        stageData.refreshedServerGroups << model
      } catch (e) {
        stageData.errors << e.message
      }
    }

    def status = RUNNING
    // If all server groups had their cache updates applied immediately, we don't need to do any
    // polling for completion and can return SUCCEEDED right away.
    // In that case, we also reset stageData so that a subsequent ServerGroupCacheForceRefresh in
    // this stage starts fresh
    if (allUpdatesApplied) {
      status = SUCCEEDED
      stageData.reset()
    }

    return Optional.of(new TaskResult(status, convertAndStripNullValues(stageData)))
  }

  /**
   * Ensure that:
   * - We see a pending force cache update for every deployed server group
   * - The pending force cache update is recent (newer than the start of this particular stage)
   * - The pending force cache update has been processed (ie. it's survived one full pass of the caching agent)
   *
   * It's possible waiting until processing is overkill but we do this to avoid the possibility of a race condition
   * between a forceCache refresh and an ongoing caching agent cycle.
   */
  private boolean processPendingForceCacheUpdates(String executionId,
                                                  String account,
                                                  String cloudProvider,
                                                  StageData stageData,
                                                  Long startTime) {

    def pendingForceCacheUpdates = cacheStatusService.pendingForceCacheUpdates(cloudProvider, REFRESH_TYPE)
    log.debug("Force cache refresh clouddriver response was: ${pendingForceCacheUpdates}.")
    boolean isRecent = (startTime != null) ? pendingForceCacheUpdates.find { it.cacheTime >= startTime } : false

    log.debug("Force cache refresh isRecent = ${isRecent}")

    boolean finishedProcessing = true
    stageData.deployServerGroups.each { String region, Set<String> serverGroups ->
      def makeModel = { serverGroup -> [serverGroup: serverGroup, region: region, account: account] }

      def processedServerGroups = serverGroups.findAll { String serverGroup ->
        def model = makeModel(serverGroup)

        def forceCacheUpdate = pendingForceCacheUpdates.find {
          (it.details as Map<String, String>).intersect(model) == model
        }

        if (!stageData.processedServerGroups.contains(model) && forceCacheUpdate == null) {
          log.debug("Force cache refresh response narrowed to ${model} was null, and this group hasn't been processed yet!")
        }

        if (stageData.processedServerGroups.contains(model)) {
          // this server group has already been processed
          log.debug("Force cache refresh the model ${model} has been already processed, returning true.")
          return true
        }

        if (!forceCacheUpdate) {
          // there is no pending cache update, force it again in the event that it was missed
          stageData.removeRefreshedServerGroup(model.serverGroup, model.region, model.account)
          log.warn("Unable to find pending cache refresh request (model: ${model}), forcing a new cache refresh.")

          try {
            log.info("Force immediate cache refresh POST to clouddriver with model: ${model}")
            def response = cacheService.forceCacheUpdate(cloudProvider, REFRESH_TYPE, model)
            if (response.status == HttpURLConnection.HTTP_OK) {
              // cache update was applied immediately, no need to poll for completion
              log.info("Processed force cache refresh request immediately (model: ${model})")
              return true
            }
            stageData.refreshedServerGroups << model
          } catch (e) {
            stageData.errors << e.message
          }
          return false
        }

        if (!isRecent) {
          // replication lag -- there are no pending force cache refreshes newer than this particular stage ... retry in 10s
          log.warn(
            "No recent pending force cache refresh updates found, retrying in 10s (lag: {}ms, model: {}, executionId: {})",
            System.currentTimeMillis() - startTime,
            model,
            executionId
          )
          return false
        }

        if (forceCacheUpdate) {
          if (!forceCacheUpdate.processedTime) {
            // there is a pending cache update that is still awaiting processing
            log.warn("Awaiting processing on pending cache refresh request (model: ${model})")
            return false
          }

          if (forceCacheUpdate.processedTime < startTime || forceCacheUpdate.cacheTime < startTime) {
            // there is a stale pending cache update, force it again
            stageData.removeRefreshedServerGroup(serverGroup, region, account)
            log.warn("Found stale pending cache refresh request (model: ${model}, request: ${forceCacheUpdate})")
            return false
          }
        }

        log.info("Processed force cache refresh request in ${forceCacheUpdate.cacheTime - startTime}ms (model: ${model})")
        return true
      }

      stageData.processedServerGroups.addAll(processedServerGroups.collect {
        makeModel(it)
      })

      finishedProcessing = finishedProcessing && (processedServerGroups == serverGroups)
    }
    return finishedProcessing
  }

  private Map convertAndStripNullValues(StageData stageData) {
    def result = objectMapper.convertValue(stageData, Map)

    result.values().removeAll { it == null }

    return result
  }

  static class StageData {
    @JsonProperty("deploy.server.groups")
    Map<String, Set<String>> deployServerGroups = [:]

    @JsonProperty("refreshed.server.groups")
    Set<Map> refreshedServerGroups = []

    @JsonProperty("processed.server.groups")
    Set<Map> processedServerGroups = []

    @JsonProperty("force.cache.refresh.errors")
    Collection<String> errors = []

    Collection<String> zones = []
    String zone

    String getZone() {
      return this.zone ?: (zones ? zones[0] : null)
    }

    void removeRefreshedServerGroup(String serverGroupName, String region, String account) {
      refreshedServerGroups.remove(
        refreshedServerGroups.find {
          it.serverGroupName == serverGroupName && it.region == region && it.account == account
        }
      )
    }

    void reset() {
      refreshedServerGroups = []
      processedServerGroups = []
      errors = []
    }
  }
}
