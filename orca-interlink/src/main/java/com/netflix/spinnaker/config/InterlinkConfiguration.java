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

package com.netflix.spinnaker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.pubsub.PubsubPublishers;
import com.netflix.spinnaker.kork.pubsub.aws.SNSPublisherProvider;
import com.netflix.spinnaker.kork.pubsub.aws.api.AmazonPubsubMessageHandler;
import com.netflix.spinnaker.kork.pubsub.aws.api.AmazonPubsubMessageHandlerFactory;
import com.netflix.spinnaker.kork.pubsub.aws.config.AmazonPubsubConfig;
import com.netflix.spinnaker.kork.pubsub.aws.config.AmazonPubsubProperties;
import com.netflix.spinnaker.kork.pubsub.config.PubsubConfig;
import com.netflix.spinnaker.orca.config.PreprocessorConfiguration;
import com.netflix.spinnaker.orca.interlink.Interlink;
import com.netflix.spinnaker.orca.interlink.MessageFlagger;
import com.netflix.spinnaker.orca.interlink.aws.InterlinkAmazonMessageHandler;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
  PreprocessorConfiguration.class,
  PluginsAutoConfiguration.class,
  PubsubConfig.class,
  AmazonPubsubConfig.class
})
@ConditionalOnProperty("interlink.enabled")
@EnableConfigurationProperties(InterlinkConfigurationProperties.class)
public class InterlinkConfiguration {
  @Bean
  @ConditionalOnProperty({"pubsub.enabled", "pubsub.amazon.enabled"})
  public AmazonPubsubMessageHandlerFactory amazonPubsubMessageHandlerFactory(
      ObjectMapper objectMapper, ExecutionRepository repository) {
    return new AmazonPubsubMessageHandlerFactory() {
      @Override
      public AmazonPubsubMessageHandler create(
          AmazonPubsubProperties.AmazonPubsubSubscription subscription) {
        if (!"interlink".equals(subscription.getName())) {
          return null;
        }

        return new InterlinkAmazonMessageHandler(objectMapper, repository);
      }
    };
  }

  @Bean
  @ConditionalOnProperty({"pubsub.enabled", "pubsub.amazon.enabled"})
  public Interlink amazonInterlink(
      PubsubPublishers publishers,
      ObjectMapper objectMapper,
      InterlinkConfigurationProperties properties,
      Registry registry,

      // injected here to make sure the provider ran before Interlink,
      // otherwise the publisher may not have been initialized
      SNSPublisherProvider snsProvider) {
    return new Interlink(
        publishers,
        objectMapper,
        new MessageFlagger(properties.flagger.maxSize, properties.flagger.threshold),
        registry);
  }
}
