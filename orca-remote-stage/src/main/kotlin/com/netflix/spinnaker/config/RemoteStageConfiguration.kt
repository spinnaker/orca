package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.annotations.Alpha
import com.netflix.spinnaker.kork.pubsub.aws.api.AmazonPubsubMessageHandlerFactory
import com.netflix.spinnaker.kork.pubsub.aws.config.AmazonPubsubConfig
import com.netflix.spinnaker.kork.pubsub.config.PubsubConfig
import com.netflix.spinnaker.orca.remote.stage.aws.RemoteStageAmazonMessageHandler
import com.netflix.spinnaker.orca.remote.stage.messages.RemoteStageMessage
import com.netflix.spinnaker.orca.remote.stage.messages.RemoteStageMessageHandler
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(PubsubConfig::class, AmazonPubsubConfig::class)
@ConditionalOnProperty("remote-stage.enabled")
@EnableConfigurationProperties(CallbackConfigurationProperties::class)
@ComponentScan(basePackages = [
  "com.netflix.spinnaker.orca.remote.stage"
])
@Alpha
class RemoteStageConfiguration {

  @Bean
  @ConditionalOnProperty("pubsub.enabled", "pubsub.amazon.enabled")
  fun amazonPubsubMessageHandlerFactory(
    objectMapper: ObjectMapper,
    messageHandlers: Collection<RemoteStageMessageHandler<*>>
  ): AmazonPubsubMessageHandlerFactory {
    return AmazonPubsubMessageHandlerFactory { subscription ->
      if (subscription.name == "remote-stage") {
        RemoteStageAmazonMessageHandler(objectMapper, messageHandlers as Collection<RemoteStageMessageHandler<RemoteStageMessage>>)
      }
      return@AmazonPubsubMessageHandlerFactory null
    }
  }
}
