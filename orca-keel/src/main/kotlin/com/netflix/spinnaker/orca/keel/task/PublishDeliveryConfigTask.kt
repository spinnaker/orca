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
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.client.Response
import java.util.concurrent.TimeUnit

/**
 * Task that retrieves a Managed Delivery config manifest from source control via igor, then publishes it to keel,
 * to support GitOps flows.
 */
@Component
class PublishDeliveryConfigTask
constructor(
  private val keelService: KeelService,
  private val objectMapper: ObjectMapper,
  private val scmService: ScmService
) : RetryableTask {
  private val log = LoggerFactory.getLogger(javaClass)

  override fun execute(stage: Stage): TaskResult {
    val context = objectMapper.convertValue<PublishDeliveryConfigContext>(stage.context)

    if (context.attempt > context.maxRetries!!) {
      val error = "Maximum number of retries exceeded (${context.maxRetries})"
      log.error("$error. Aborting.")
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(mapOf("error" to error)).build()
    }

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
      keelService.publishDeliveryConfig(deliveryConfig, user)

      TaskResult.builder(ExecutionStatus.SUCCEEDED).context(emptyMap<String, Any?>()).build()
    } catch (e: RetrofitError) {
      when {
        e.kind == RetrofitError.Kind.NETWORK -> {
          // retry if unable to connect
          log.error("network error talking to downstream service, attempt ${context.attempt} of ${context.maxRetries}: ${e.friendlyMessage}")
          TaskResult.builder(ExecutionStatus.RUNNING).context(context.incrementAttempt().toMap()).build()
        }
        e.response?.status == HttpStatus.NOT_FOUND.value() -> {
          // just give up on 404
          log.error("404 response from downstream service, giving up: ${e.friendlyMessage}")
          TaskResult.builder(ExecutionStatus.TERMINAL).context(emptyMap<String, Any?>()).build()
        }
        else -> {
          // retry on other status codes
          log.error("HTTP error talking to downstream service, attempt ${context.attempt} of ${context.maxRetries}: ${e.friendlyMessage}")
          TaskResult.builder(ExecutionStatus.RUNNING).context(context.incrementAttempt().toMap()).build()
        }
      }
    } catch (e: Exception) {
      log.error("Unexpected exception while executing {}, aborting.", javaClass.simpleName, e)
      TaskResult.builder(ExecutionStatus.TERMINAL).context(mapOf("error" to e.message)).build()
    }
  }

  override fun getBackoffPeriod() = TimeUnit.SECONDS.toMillis(30)

  override fun getTimeout() = TimeUnit.SECONDS.toMillis(180)

  private fun Response.successful() =
    this.status in 200..299

  val RetrofitError.friendlyMessage: String
    get() = "HTTP ${response.status} ${response.url}: ${cause?.message ?: message}"

  data class PublishDeliveryConfigContext(
    val scmType: String,
    val project: String,
    val repository: String,
    val directory: String?, // as in, the directory *under* whatever manifest base path is configured in igor (e.g. ".netflix")
    val manifest: String? = "spinnaker.yml",
    val ref: String? = "refs/heads/master",
    val forceRepublish: Boolean? = false,
    val maxRetries: Int? = MAX_RETRIES,
    val attempt: Int = 1
  )

  fun PublishDeliveryConfigContext.incrementAttempt() = this.copy(attempt = attempt + 1)
  fun PublishDeliveryConfigContext.toMap() = objectMapper.convertValue<Map<String, Any?>>(this)

  companion object {
    const val MAX_RETRIES = 5
  }
}
