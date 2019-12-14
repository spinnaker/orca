/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.keel.task

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.KeelService
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.igor.ScmService
import com.netflix.spinnaker.orca.keel.model.ResourceSpec
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.client.Response
import java.util.concurrent.TimeUnit

@Component
class PublishKeelSpecsTask
@Autowired
constructor(
  private val keelService: KeelService,
  private val keelObjectMapper: ObjectMapper,
  private val scmService: ScmService
) : RetryableTask {
  private val log = LoggerFactory.getLogger(javaClass)

  override fun execute(stage: Stage): TaskResult {
//      parameters:
//      - name: repoUrl
//        mapping: cluster.env.MD_REPO_URL
//        label: Git Repository SSH URL
//        order: 0
//      - name: directory
//        mapping: cluster.env.MD_DIRECTORY
//        label: File Directory
//        defaultValue: .netflix/
//        order: 1
//      - name: fileExtension
//        mapping: cluster.env.MD_FILE_EXTENSION
//        label: File Extension
//        defaultValue: .yml
//        order: 2
//      - name: forceRepublish
//        mapping: cluster.env.MD_FORCE_REPUBLISH
//        defaultValue: false
//        label: Force Republish
//        order: 3
//      - name: gitBranch
//        mapping: cluster.env.GIT_BRANCH
//        defaultValue: ${trigger.branch}
//        label: Branch
//        order: 4
//      - name: gitSha
//        mapping: cluster.env.GIT_SHA
//        defaultValue: ${trigger.hash}
//        label: SHA
//        order: 5

    val context = keelObjectMapper.convertValue<PublishKeelSpecsContext>(stage.context)
    val manifests = scmService.listKeelManifests(
      context.scmType, context.projectKey, context.repositorySlug, context.ref)

    var failedManifest: String? = null
    for (manifest in manifests) {
      // TODO: do we even support multiple SCM servers of each kind? judging from igor, I'm guessing no
      val uri = "${context.scmType}://<server???>/${context.projectKey}/${context.repositorySlug}/.netflix/spinnaker/$manifest"

      log.info("Retrieving keel manifest at $uri")
      // FIXME: I *think* the ref parameter below should actually be based on info from the (Rocket?) code event trigger
      val manifestContents = scmService.getKeelManifest(
        context.scmType, context.projectKey, context.repositorySlug, manifest, context.ref)

      var resourceSpec: ResourceSpec
      try {
        resourceSpec = keelObjectMapper.convertValue(manifestContents)
      } catch (e: java.lang.Exception) {
        throw IllegalArgumentException("Unable to parse resource spec at $uri")
      }

      // TODO: confirm this is the right thing to do
      val user = stage.execution.trigger.user ?: "anonymous"
      log.info("Publishing manifest $manifest to keel on behalf of $user")
      val response = keelService.publishSpec(resourceSpec, user)

      if (!response.successful()) {
        log.error("Failed to publish resource spec at $uri")
        failedManifest = manifest
        break
      }
    }

    val outputs = mutableMapOf<String, Any>()
    // TODO: add failed manifest info to output, if any
    // TODO: add other outputs

    val executionStatus = if (failedManifest == null) ExecutionStatus.SUCCEEDED else ExecutionStatus.TERMINAL
    return TaskResult.builder(executionStatus).context(outputs).build()
  }

  override fun getBackoffPeriod() = TimeUnit.SECONDS.toMillis(15)

  override fun getTimeout() = TimeUnit.MINUTES.toMillis(1)

  private fun Response.successful() =
    this.status in 200..299

  data class PublishKeelSpecsContext(
    val scmType: String,
    val projectKey: String,
    val repositorySlug: String,
    val ref: String? = "refs/heads/master"
    // TODO: forceRepublish -- what does it do?
  )
}
