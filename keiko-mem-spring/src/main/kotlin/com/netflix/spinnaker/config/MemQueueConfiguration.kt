package com.netflix.spinnaker.config

import com.netflix.spinnaker.q.DeadMessageCallback
import com.netflix.spinnaker.q.memory.InMemoryQueue
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
open class MemQueueConfiguration {

  @Bean
  open fun queue(
    clock: Clock,
    deadMessageHandler: DeadMessageCallback,
    publisher: ApplicationEventPublisher
  ) =
    InMemoryQueue(
      clock = clock,
      deadMessageHandler = deadMessageHandler::invoke,
      publisher = publisher
    )
}