package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("remote-stage")
class CallbackConfigurationProperties {
  var http: HttpConfigurationProperties = HttpConfigurationProperties()
  var pubsub: PubsubConfigurationProperties = PubsubConfigurationProperties()

  inner class HttpConfigurationProperties {
    var uri: String = "configure-me"
  }

  inner class PubsubConfigurationProperties {
    var name: String = "configure-me"
    var provider: String = "configure-me"
    var providerConfig: MutableMap<String, String> = mutableMapOf()
  }
}
