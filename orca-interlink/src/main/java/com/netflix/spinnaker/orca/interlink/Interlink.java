/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.orca.interlink;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import com.netflix.spinnaker.kork.pubsub.PubsubPublishers;
import com.netflix.spinnaker.kork.pubsub.model.PubsubPublisher;
import com.netflix.spinnaker.orca.interlink.events.InterlinkEvent;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Interlink {
  private final PubsubPublisher publisher;
  private final ObjectMapper objectMapper;
  private final MessageFlagger flagger;
  private Counter flaggedCounter;

  public Interlink(
      PubsubPublishers publishers,
      ObjectMapper objectMapper,
      MessageFlagger flagger,
      Registry registry) {
    this.objectMapper = objectMapper;
    this.flagger = flagger;

    publisher =
        publishers.getAll().stream()
            .filter(pubsubPublisher -> "interlink".equals(pubsubPublisher.getTopicName()))
            .findFirst()
            .orElse(null);

    if (publisher == null) {
      throw new SystemException(
          "could not find interlink publisher in ["
              + publishers.getAll().stream()
                  .map(PubsubPublisher::getTopicName)
                  .collect(Collectors.joining(", "))
              + "]");
    }

    this.flaggedCounter =
        registry.counter(
            "pubsub." + publisher.getPubsubSystem() + ".flagged",
            "subscription",
            publisher.getName());
  }

  public void publish(InterlinkEvent event) {
    if (flagger.isFlagged(event)) {
      log.warn("Event {} has been flagged, not publishing to interlink", event);
      flaggedCounter.increment();
      return;
    }

    try {
      publisher.publish(objectMapper.writeValueAsString(event));
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize event {}", event, e);
    }
  }
}
