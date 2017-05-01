/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.webhook

import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseActions
import org.springframework.web.client.RestTemplate
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class WebhookServiceSpec extends Specification {

  @Shared
  def restTemplate = new RestTemplate()

  @Shared
  def userConfiguredUrlRestrictions = new UserConfiguredUrlRestrictions.Builder().withRejectLocalhost(false).build()

  def server = MockRestServiceServer.createServer(restTemplate)

  @Subject
  def webhookService = new WebhookService(restTemplate: restTemplate, userConfiguredUrlRestrictions: userConfiguredUrlRestrictions)

  @Unroll
  def "Webhook is being called with correct parameters"() {
    expect:
    ResponseActions responseActions = server.expect(requestTo("https://localhost/v1/test"))
      .andExpect(method(HttpMethod.POST))

    if(payload) {
      payload.each { k, v -> responseActions.andExpect(jsonPath('$.' + k).value(v)) }
    }
    if(customHeaders) {
      customHeaders.each { k, v -> responseActions.andExpect(header(k, v)) }
    }

    responseActions.andRespond(withSuccess('{"status": "SUCCESS"}', MediaType.APPLICATION_JSON))

    when:
    HttpHeaders headers = new HttpHeaders()
    headers.add("customHeader", "value")
    def responseEntity = webhookService.exchange(
      HttpMethod.POST,
      "https://localhost/v1/test",
      payload,
      customHeaders
    )

    then:
    server.verify()
    responseEntity.statusCode == HttpStatus.OK
    responseEntity.body == ["status": "SUCCESS"]

    where:
    payload                                     | customHeaders
    ["payload1": "Hello", "payload2": "World!"] | ["X-HEADER": "value"]
    ["payload1": "Hello", "payload2": "World!"] | [:]
    [:]                                         | [:]
    null                                        | null

  }

  @Unroll
  def "Status endpoint is being called with headers #customHeaders"() {

    expect:
    def responseActions = server.expect(requestTo("https://localhost/v1/status/123"))
      .andExpect(method(HttpMethod.GET))

      if(customHeaders){
        customHeaders.each {k, v -> responseActions.andExpect(header(k, v))}
      }

      responseActions.andRespond(withSuccess('["element1", 123, false]', MediaType.APPLICATION_JSON))

    when:
    def responseEntity = webhookService.getStatus("https://localhost/v1/status/123", [Authorization: "Basic password"])

    then:
    server.verify()
    responseEntity.statusCode == HttpStatus.OK
    responseEntity.body == ["element1", 123, false]

    where:
    customHeaders << [[Authorization: "Basic password"], [:], null]
  }
}
