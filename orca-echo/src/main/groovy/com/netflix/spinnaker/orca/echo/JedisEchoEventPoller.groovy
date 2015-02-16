package com.netflix.spinnaker.orca.echo

import groovy.transform.CompileStatic
import java.time.Instant
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component
import redis.clients.jedis.JedisCommands
import retrofit.client.Response
import static com.google.common.net.HttpHeaders.DATE
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME

/**
 * Fetches events from Echo and caches the time of the last check in Redis.
 */
@Component
@ConditionalOnBean(JedisCommands)
@CompileStatic
class JedisEchoEventPoller implements EchoEventPoller {

  public static final String LAST_CHECK_KEY = "echo:event:last-check"

  private final JedisCommands jedis
  private final EchoService echoService

  @Autowired
  JedisEchoEventPoller(JedisCommands jedis, EchoService echoService) {
    this.jedis = jedis
    this.echoService = echoService
  }

  @Override
  Response getEvents(String type) {
    def lastCheck = jedis.get(LAST_CHECK_KEY)?.toLong()
    def response = echoService.getEvents(type, lastCheck ?: 0L)
    jedis.set LAST_CHECK_KEY, dateHeaderFrom(response).toString()
    return response
  }

  private static long dateHeaderFrom(Response response) {
    def header = response.headers.find { it.name == DATE }
    Instant.from(RFC_1123_DATE_TIME.parse(header.value)).toEpochMilli()
  }
}
