package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("remote-stage")
class CallbackConfigurationProperties {
  var http: HttpConfigurationProperties = HttpConfigurationProperties()
  var pubsub: PubsubConfigurationProperties = PubsubConfigurationProperties()

  inner class HttpConfigurationProperties {
    var url: String = "configure-me"
  }

  inner class PubsubConfigurationProperties {
    var id: String = "configure-me"
    var provider: String = "configure-me"
    var providerConfig: Map<String, String> = mapOf()
  }
}
