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

package com.netflix.spinnaker.orca.q.echo

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.q.event.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnExpression("\${echo.enabled:true}")
open class EchoExecutionStartedListener
@Autowired constructor(
  private val repository: ExecutionRepository,
  private val echoService: EchoService,
  private val front50Service: Front50Service,
  private val objectMapper: ObjectMapper
) : ApplicationListener<ExecutionEvent> {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun onApplicationEvent(event: ExecutionEvent) =
    when (event) {
      is ExecutionStarted -> onExecutionStarted(event)
      is ExecutionComplete -> onExecutionComplete(event)
      is StageStarted -> onStageStarted(event)
      is StageComplete -> onStageComplete(event)
      is TaskStarted -> onTaskStarted(event)
      is TaskComplete -> onTaskComplete(event)
    }

  private fun onExecutionStarted(event: ExecutionStarted) {
    if (event.executionType is Pipeline) {
      val pipeline = repository.retrievePipeline(event.executionId)
      try {
        addApplicationNotifications(pipeline)
        echoService.recordEvent(mapOf(
          "details" to mapOf(
            "source" to "orca",
            "type" to "orca:pipeline:starting",
            "application" to pipeline.application
          ),
          "content" to mapOf(
            "execution" to pipeline,
            "executionId" to pipeline.id
          )
        ))
      } catch (e: Exception) {
        log.error("Failed to send pipeline start event: ${event.executionId}")
      }
    }
  }

  private fun onExecutionComplete(event: ExecutionComplete) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  private fun onStageStarted(event: StageStarted) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  private fun onStageComplete(event: StageComplete) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  private fun onTaskStarted(event: TaskStarted) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  private fun onTaskComplete(event: TaskComplete) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  /**
   * Adds any application-level notifications to the pipeline's notifications
   * If a notification exists on both with the same address and type, the pipeline's notification will be treated as an
   * override, and any "when" values in the application-level notification that are also in the pipeline's notification
   * will be removed from the application-level notification
   *
   * @param pipeline
   */
  private fun addApplicationNotifications(pipeline: Pipeline) {
    val notifications = front50Service.getApplicationNotifications(pipeline.application)
    if (notifications != null) {
      notifications
        .pipelineNotifications
        .map { appNotification ->
          val executionMap: Map<String, Any> = objectMapper.convertValue(pipeline)
          ContextParameterProcessor.process(appNotification, executionMap, false) as Map<String, Any>
        }
        .forEach { appNotification ->
          val targetMatch = pipeline.notifications.find { pipelineNotification ->
            pipelineNotification["address"] == appNotification["address"] && pipelineNotification["type"] == appNotification["type"]
          }

          if (targetMatch == null) {
            pipeline.notifications.add(appNotification)
          } else {
            val appWhen: MutableCollection<String> = appNotification["when"] as MutableCollection<String>
            val pipelineWhen: Collection<String> = targetMatch["when"] as Collection<String>
            appWhen.removeAll(pipelineWhen)
            if (appWhen.isNotEmpty()) {
              pipeline.notifications.add(appNotification)
            }
          }
        }
    }
  }

  inline fun <reified R> ObjectMapper.convertValue(fromValue: Any): R =
    convertValue(fromValue, R::class.java)
}
