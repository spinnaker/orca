/*
 * Copyright 2022 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.controllers

import com.netflix.spinnaker.orca.echo.notification.ManualJudgementCallbackHandler
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

import static com.netflix.spinnaker.orca.echo.EchoService.Notification.InteractiveActionCallback

@RequestMapping("/notifications")
@RestController
@Slf4j
class NotificationCallbackController {

  @Autowired
  ManualJudgementCallbackHandler manualJudgementCallbackHandler;

  @RequestMapping(
      value = "/callback",
      method = RequestMethod.POST,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<String> processCallback(RequestEntity<InteractiveActionCallback> request) {
    return manualJudgementCallbackHandler.processCallback(request)
  }
}
