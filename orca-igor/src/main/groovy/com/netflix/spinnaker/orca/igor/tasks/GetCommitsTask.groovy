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

package com.netflix.spinnaker.orca.igor.tasks
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.kato.tasks.DiffTask
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

import java.util.concurrent.TimeUnit

@Slf4j
@Component
class GetCommitsTask implements DiffTask {
  private static final int MAX_RETRIES = 3

  long backoffPeriod = 3000
  long timeout = TimeUnit.MINUTES.toMillis(5) // always set this higher than retries * backoffPeriod would take

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  @Autowired(required = false)
  BuildService buildService

  @Autowired(required = false)
  Front50Service front50Service

  @Override
  TaskResult execute(Stage stage) {
    def retriesRemaining = stage.context.getCommitsRetriesRemaining != null ? stage.context.getCommitsRetriesRemaining : MAX_RETRIES
    // is igor not configured or have we exceeded configured retries
    if (!buildService || retriesRemaining == 0) {
      log.info("igor is not configured or retries exceeded : buildService : ${buildService}, retries : ${retriesRemaining}")
      return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [commits: [], getCommitsRetriesRemaining: retriesRemaining])
    }

    if (!front50Service) {
      log.warn("Front50 is not configured. Fix this by setting front50.enabled: true")
      return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [commits: [], getCommitsRetriesRemaining: retriesRemaining])
    }

    Map repoInfo = [:]
    String sourceCommit
    String targetCommit
    List commitsList

    try {
      // get app config to see if it has repo info
      repoInfo = getRepoInfo(stage.context.account, stage.context.application)

      if(!repoInfo?.repoType || !repoInfo?.projectKey || !repoInfo?.repositorySlug) {
        log.info("not enough info to query igor for commits : [repoType: ${repoInfo?.repoType} projectKey:${repoInfo?.projectKey} repositorySlug:${repoInfo?.repositorySlug}]")
        return DefaultTaskResult.SUCCEEDED
      }

      String region = stage.context?.source?.region ?: stage.context?.availabilityZones?.findResult { key, value -> key }
      String account = stage.context?.source?.account ?: stage.context?.account

      //figure out the old asg/ami/commit
      String ancestorAmi = getAncestorAmi(stage.context, region, account) // FIXME: multi-region deployments/canaries

      if(!ancestorAmi) {
        log.info "could not determine ancestor ami, this may be a new cluster with no ancestor asg"
        return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [commits: []])
      }

      //figure out the new asg/ami/commit
      String targetAmi = getTargetAmi(stage.context, region)

      //get commits from igor
      sourceCommit = resolveCommitFromAmi(ancestorAmi, account, region)
      targetCommit = resolveCommitFromAmi(targetAmi, account, region)

      commitsList = []

      //return results
      if (repoInfo?.repoType && repoInfo?.projectKey && repoInfo?.repositorySlug && sourceCommit && targetCommit) {
        commitsList = getCommitsList(repoInfo.repoType, repoInfo.projectKey, repoInfo.repositorySlug, sourceCommit, targetCommit)
      } else {
        log.warn("not enough info to query igor for commits : [repoType: ${repoInfo?.repoType} projectKey:${repoInfo?.projectKey} repositorySlug:${repoInfo?.repositorySlug} sourceCommit:${sourceCommit} targetCommit: ${targetCommit}]")
      }
      return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [commits: commitsList])
    } catch (RetrofitError e) {
        if(e.response?.status == 404) { // just give up on 404
          log.error("got a 404 from igor for : [repoType: ${repoInfo?.repoType} projectKey:${repoInfo?.projectKey} repositorySlug:${repoInfo?.repositorySlug} sourceCommit:${sourceCommit} targetCommit: ${targetCommit}]")
          return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [commits: []])
        } else { // retry on other status codes
          log.error("retrofit error for : [repoType: ${repoInfo?.repoType} projectKey:${repoInfo?.projectKey} repositorySlug:${repoInfo?.repositorySlug} sourceCommit:${sourceCommit} targetCommit: ${targetCommit}], retrying", e)
          return new DefaultTaskResult(ExecutionStatus.RUNNING, [getCommitsRetriesRemaining: retriesRemaining - 1])
        }
    } catch(Exception f) { // retry on everything else
      log.error("unexpected exception for : [repoType: ${repoInfo?.repoType} projectKey:${repoInfo?.projectKey} repositorySlug:${repoInfo?.repositorySlug} sourceCommit:${sourceCommit} targetCommit: ${targetCommit}], retrying", f)
      return new DefaultTaskResult(ExecutionStatus.RUNNING, [getCommitsRetriesRemaining: retriesRemaining - 1])
    } catch(Throwable g) {
      log.error("unexpected throwable for : [repoType: ${repoInfo?.repoType} projectKey:${repoInfo?.projectKey} repositorySlug:${repoInfo?.repositorySlug} sourceCommit:${sourceCommit} targetCommit: ${targetCommit}], retrying", g)
      return new DefaultTaskResult(ExecutionStatus.RUNNING, [getCommitsRetriesRemaining: retriesRemaining - 1])
    }
  }

  List getCommitsList(String repoType, String projectKey, String repositorySlug, String sourceCommit, String targetCommit) {
    List commitsList = []
    List commits = buildService.compareCommits(repoType, projectKey, repositorySlug, [to: sourceCommit, from: targetCommit, limit: 100])
    commits.each {
      // add commits to the task output
      commitsList << [displayId: it.displayId, id: it.id, authorDisplayName: it.authorDisplayName,
                      timestamp: it.timestamp, commitUrl: it.commitUrl, message: it.message]
    }
    return commitsList
  }

  String resolveCommitFromAmi(String ami, String account, String region) {
    List<Map> amiDetails = oortService.getByAmiId("aws", account, region, ami)
    def appVersion = amiDetails[0]?.tags?.appversion

    if (appVersion) {
      return appVersion.substring(0, appVersion.indexOf('/')).substring(appVersion.lastIndexOf('.') + 1)
    }
  }

  String getTargetAmi(Map context, region) {
    if(context.clusterPairs) { // canary cluster stage
      return context.clusterPairs?.find{ clusterPair ->
        clusterPair?.canary?.availabilityZones?.findResult { key, value -> key == region }
      }?.canary?.amiName
    } else if (context.deploymentDetails) { // deploy asg stage
      return context.deploymentDetails.find { it.region == region }?.amiId
    } else if (context.amiName) { // copyLastAsg stage
      return context.amiName
    } else {
      return null
    }
  }

  String getAncestorAmi(Map context, String region, String account) {
    if(context.clusterPairs) { // separate cluster diff
      return context.clusterPairs?.find{ clusterPair ->
        clusterPair?.baseline?.availabilityZones?.findResult { key, value -> key == region }
      }?.baseline?.amiName
    } else if(context.get("kato.tasks")) { // same cluster asg diff
      String ancestorAsg = context.get("kato.tasks")?.find { item ->
        item.find { key, value ->
          key == 'resultObjects'
        }
      }?.resultObjects?.ancestorServerGroupNameByRegion?.find {
        it.find { key, value ->
          key == region
        }
      }?.get(region)

      String sourceCluster
      if(!ancestorAsg) {
        return null
      } else if (ancestorAsg.lastIndexOf("-") > 0) {
        sourceCluster = ancestorAsg.substring(0, ancestorAsg.lastIndexOf("-"))
      } else {
        sourceCluster = ancestorAsg
      }

      TypeReference<Map> jsonMapType = new TypeReference<Map>() {}
      Map sourceServerGroup = objectMapper.readValue(oortService.getServerGroup(context.application,
        account, sourceCluster,
        ancestorAsg, region, "aws").body.in(), jsonMapType)
      return sourceServerGroup.launchConfig.imageId
    }
  }

  Map getRepoInfo(String account, String application) {
    def globalAccount = front50Service.credentials.find { it.global }
    def applicationAccount = globalAccount?.name ?: account
    Application app = front50Service.get(application)
    return [repoType : app?.details().repoType, projectKey : app?.details().repoProjectKey, repositorySlug : app?.details().repoSlug]
  }
}
