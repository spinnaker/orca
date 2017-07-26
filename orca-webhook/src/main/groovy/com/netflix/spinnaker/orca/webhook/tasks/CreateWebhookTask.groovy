/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.webhook.tasks

import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.webhook.service.WebhookService
import groovy.util.logging.Slf4j
import org.apache.http.HttpHeaders
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException

@Slf4j
@Component
class CreateWebhookTask implements RetryableTask {

  long backoffPeriod = 10000
  long timeout = 300000

  @Autowired
  WebhookService webhookService

  @Override
  TaskResult execute(Stage stage) {
    String url = stage.context.url
    def method = stage.context.method ? HttpMethod.valueOf(stage.context.method.toString().toUpperCase()) : HttpMethod.POST
    def payload = stage.context.payload
    def customHeaders = stage.context.customHeaders
    boolean waitForCompletion = (stage.context.waitForCompletion as String)?.toBoolean()

    def response
    try {
       response = webhookService.exchange(method, url, payload, customHeaders)
    } catch (HttpStatusCodeException e) {
      def statusCode = e.getStatusCode()
      if (statusCode.is5xxServerError() || statusCode.value() == 429) {
        log.warn("error submitting webhook to ${url}, will retry", e)
        return new TaskResult(ExecutionStatus.RUNNING)
      }
      throw e
    }

    def statusCode = response.statusCode
    def outputs = [:]
    outputs << [statusCode: statusCode]
    if (response.body) {
      outputs << [buildInfo: response.body]
    }
    if (statusCode.is2xxSuccessful() || statusCode.is3xxRedirection()) {
      if (waitForCompletion) {
        def statusUrl = null
        def statusUrlResolution = stage.context.statusUrlResolution
        switch (statusUrlResolution) {
          case "getMethod":
            statusUrl = url
            break
          case "locationHeader":
            statusUrl = response.headers.getFirst(HttpHeaders.LOCATION)
            break
          case "webhookResponse":
            try {
              statusUrl = JsonPath.read(response.body, stage.context.statusUrlJsonPath as String)
            } catch (PathNotFoundException e) {
              return new TaskResult(ExecutionStatus.TERMINAL,
                [error: [reason: e.message, response: response.body]])
            }
        }
        if (!statusUrl || !(statusUrl instanceof String)) {
          return new TaskResult(ExecutionStatus.TERMINAL,
            outputs + [
              error: "The status URL couldn't be resolved, but 'Wait for completion' was checked",
              statusUrlValue: statusUrl
            ])
        }
        stage.context.statusEndpoint = statusUrl
        return new TaskResult(ExecutionStatus.SUCCEEDED, outputs + [statusEndpoint: statusUrl])
      }
      return new TaskResult(ExecutionStatus.SUCCEEDED, outputs)
    } else {
      return new TaskResult(ExecutionStatus.TERMINAL, outputs + [error: "The request did not return a 2xx/3xx status"])
    }
  }

}
