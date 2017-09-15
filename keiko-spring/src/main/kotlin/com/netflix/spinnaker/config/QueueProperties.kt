package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("keiko.queue")
class QueueProperties {
  var handlerCorePoolSize: Int = 20
  var handlerMaxPoolSize: Int = 20
}