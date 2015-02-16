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

package com.netflix.spinnaker.orca.notifications.jenkins

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.echo.EchoEventPoller
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.NotificationHandler
import net.greghaines.jesque.client.Client
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class BuildJobPollingNotificationAgent extends AbstractPollingNotificationAgent {

  public static final String NOTIFICATION_TYPE = "build"

  final long pollingInterval = 30
  final String notificationType = NOTIFICATION_TYPE

  @Autowired
  BuildJobPollingNotificationAgent(ObjectMapper objectMapper,
                                   EchoEventPoller echoEventPoller,
                                   Client jesqueClient) {
    super(objectMapper, echoEventPoller, jesqueClient)
  }

  @Override
  Class<? extends NotificationHandler> handlerType() {
    BuildJobNotificationHandler
  }

  @Override
  @CompileDynamic
  protected List<Map> filterEvents(List<Map> response) {
    def events = []
    for (event in response) {
      if (event.content.containsKey("project") && event.content.containsKey("master")) {
        def input = event.content.project as Map
        input.master = event.content.master
        events << input
      }
    }
    return events
  }
}
