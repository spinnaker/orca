/*
 * Copyright 2024 Armory, Inc.
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

package com.netflix.spinnaker.orca.front50.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import com.netflix.spinnaker.orca.front50.Front50Service
import okhttp3.OkHttpClient
import retrofit.Endpoint
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import spock.lang.Specification
import spock.lang.Subject

/**
 * Simple test to verify that the Front50ConfigurationProperties defaults match OkHttpClientConfigurationProperties
 */
class Front50ConfigurationSpec extends Specification {

  def "default timeout values should match OkHttpClientConfigurationProperties"() {
    given:
    OkHttpClientConfigurationProperties globalProps = new OkHttpClientConfigurationProperties()
    Front50ConfigurationProperties.OkHttpConfigurationProperties front50Props = 
      new Front50ConfigurationProperties.OkHttpConfigurationProperties()
    
    expect:
    front50Props.connectTimeoutMs == globalProps.connectTimeoutMs
    front50Props.readTimeoutMs == globalProps.readTimeoutMs
  }
  
  def "front50Service method should accept OkHttpClientConfigurationProperties parameter"() {
    given:
    def front50Configuration = new Front50Configuration()
    def method = Front50Configuration.class.getDeclaredMethod(
        "front50Service", 
        Endpoint.class, 
        ObjectMapper.class, 
        Front50ConfigurationProperties.class,
        OkHttpClientConfigurationProperties.class)
    
    expect:
    method != null
  }
}
