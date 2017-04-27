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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestTemplate

@Service
class WebhookService {

  @Autowired
  RestTemplate restTemplate

  @Autowired
  UserConfiguredUrlRestrictions userConfiguredUrlRestrictions

  ResponseEntity<Object> exchange(HttpMethod httpMethod, String url, Object payload, List<Map<String, String>> headers) {
    URI validatedUri = userConfiguredUrlRestrictions.validateURI(url)
    HttpHeaders headersMap = buildHttpHeadersForRequest(headers)
    HttpEntity<Object> payloadEntity = new HttpEntity<>(payload, headersMap)
    def exchange = restTemplate.exchange(validatedUri, httpMethod, payloadEntity, Object)
    return exchange
  }

  ResponseEntity<Object> getStatus(String url, List<Map<String, String>> headers) {
    URI validatedUri = userConfiguredUrlRestrictions.validateURI(url)
    HttpHeaders headersMap = buildHttpHeadersForRequest(headers)
    HttpEntity<Object> headersEntity = new HttpEntity<>(headersMap)
    def exchange = restTemplate.exchange(validatedUri, HttpMethod.GET, headersEntity, Object)
    return exchange
  }

  private static HttpHeaders buildHttpHeadersForRequest(List<Map<String, String>> headers) {
    MultiValueMap<String, String> headersMap = new HttpHeaders()
    for (Map<String, String> entryMap in headers) {
      headersMap.add(entryMap.get("name"), entryMap.get("value"))
    }
    return headersMap
  }
}
