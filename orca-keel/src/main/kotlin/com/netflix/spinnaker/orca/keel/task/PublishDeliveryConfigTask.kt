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
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.client.Response
import java.util.concurrent.TimeUnit

@Component
class PublishDeliveryConfigTask
constructor(
  private val keelService: KeelService,
  private val keelObjectMapper: ObjectMapper,
  private val scmService: ScmService
) : RetryableTask {
  private val log = LoggerFactory.getLogger(javaClass)

  override fun execute(stage: Stage): TaskResult {
    val context = keelObjectMapper.convertValue<PublishDeliveryConfigContext>(stage.context)
    val manifestLocation = "${context.scmType}://${context.project}/${context.repository}/.netflix/${context.directory
      ?: ""}/${context.manifest}"

    return try {
      log.info("Retrieving keel manifest at $manifestLocation")
      // FIXME: I *think* the ref parameter below should actually be based on info from the git trigger
      val deliveryConfig = scmService.getDeliveryConfigManifest(
        context.scmType, context.project, context.repository, context.directory, context.manifest, context.ref)

      // TODO: confirm this is the right thing to do
      val user = stage.execution.trigger.user ?: "anonymous"

      log.info("Publishing manifest ${context.manifest} to keel on behalf of $user")
      val response = keelService.publishDeliveryConfig(deliveryConfig, user)

      val outputs = mutableMapOf<String, Any>()
      // TODO: add failed manifest info to output, if any
      // TODO: add other outputs

      val executionStatus = if (response.successful()) ExecutionStatus.SUCCEEDED else ExecutionStatus.TERMINAL
      return TaskResult.builder(executionStatus).context(outputs).build()
    } catch (e: RetrofitError) {
      // TODO: handle external service call errors with retries
      throw e
    } catch (e: Exception) {
      log.error("Exception while executing {}, aborting.", javaClass.simpleName, e)
      throw e
    }
  }

  override fun getBackoffPeriod() = TimeUnit.SECONDS.toMillis(30)

  override fun getTimeout() = TimeUnit.MINUTES.toMillis(1)

  private fun Response.successful() =
    this.status in 200..299

  data class PublishDeliveryConfigContext(
    val scmType: String,
    val project: String,
    val repository: String,
    val directory: String? = ".", // as in, the directory *under* whatever manifest base path is configured in igor, e.g. ".netflix"
    val manifest: String? = "spinnaker.yml",
    val ref: String? = "refs/heads/master",
    val forceRepublish: Boolean? = false
  )
}
