/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.bakery.config

import java.text.SimpleDateFormat
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy.LowerCaseWithUnderscoresStrategy
import com.netflix.spinnaker.orca.bakery.api.BakeryApi
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import groovy.transform.CompileStatic
import okhttp3.HttpUrl
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES

@Configuration
@Import([OrcaConfiguration])
@ComponentScan([
  "com.netflix.spinnaker.orca.bakery.pipeline",
  "com.netflix.spinnaker.orca.bakery.tasks"
])
@CompileStatic
@ConditionalOnExpression('${bakery.enabled:true}')
class BakeryConfiguration {

  @Bean
  HttpUrl bakeryEndpoint(@Value('${bakery.baseUrl}') String bakeryBaseUrl) {
    HttpUrl.parse(bakeryBaseUrl)
  }

  @Bean
  BakeryApi bakery(HttpUrl bakeryEndpoint) {
    def objectMapper = new ObjectMapper()
      .setPropertyNamingStrategy(new LowerCaseWithUnderscoresStrategy())
      .setDateFormat(new SimpleDateFormat("YYYYMMDDHHmm"))
      .setSerializationInclusion(NON_NULL)
      .disable(FAIL_ON_UNKNOWN_PROPERTIES)

    new Retrofit.Builder()
      .baseUrl(bakeryEndpoint)
      .addConverterFactory(JacksonConverterFactory.create(objectMapper))
      .build()
      .create(BakeryApi.class);
  }
}
