/*
 * Copyright 2023 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.front50.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Front50 service.
 *
 * <p>These properties can be configured in your YAML configuration:
 *
 * <pre>
 * front50:
 *   baseUrl: http://front50.example.com
 *   enabled: true
 *   useTriggeredByEndpoint: true
 *   okhttp:
 *     connectTimeoutMs: 10000
 *     readTimeoutMs: 60000
 *     writeTimeoutMs: 60000
 * </pre>
 *
 * <p>If not explicitly configured, a fallback chain will be used for timeouts:
 *
 * <ol>
 *   <li>Use explicit okhttp configuration if present
 *   <li>Fall back to global okhttp client configuration
 *   <li>Use default fallback values (10s connect, 60s read/write)
 * </ol>
 */
@Data
@ConfigurationProperties("front50")
public class Front50ConfigurationProperties {
  boolean enabled;

  String baseUrl;

  /**
   * Controls the front50 endpoint that DependentPipelineExecutionListener calls to retrieve
   * pipelines.
   *
   * <p>When true: GET /pipelines/triggeredBy/{pipelineId}/{status} When false: GET /pipelines
   */
  boolean useTriggeredByEndpoint = true;

  /** HTTP client configuration for connecting to Front50 service */
  OkHttpConfigurationProperties okhttp = new OkHttpConfigurationProperties();

  /**
   * Configuration properties for the OkHttp client connecting to Front50. These will only be used
   * if explicitly set in the configuration. Otherwise, global client timeouts will be used as
   * fallback.
   */
  @Data
  public static class OkHttpConfigurationProperties {
    /** Read timeout in milliseconds. Default is 60 seconds (60000ms) */
    private Integer readTimeoutMs = 60000;

    /** Write timeout in milliseconds. Default is 60 seconds (60000ms) */
    private Integer writeTimeoutMs = 60000;

    /** Connection timeout in milliseconds. Default is 10 seconds (10000ms) */
    private Integer connectTimeoutMs = 10000;

    /**
     * Checks if read timeout is explicitly configured with a non-default value.
     *
     * @return true if read timeout is configured with a non-default value
     */
    public boolean hasReadTimeoutConfig() {
      return readTimeoutMs != null && readTimeoutMs != 60000;
    }

    /**
     * Checks if write timeout is explicitly configured with a non-default value.
     *
     * @return true if write timeout is configured with a non-default value
     */
    public boolean hasWriteTimeoutConfig() {
      return writeTimeoutMs != null && writeTimeoutMs != 60000;
    }

    /**
     * Checks if connect timeout is explicitly configured with a non-default value.
     *
     * @return true if connect timeout is configured with a non-default value
     */
    public boolean hasConnectTimeoutConfig() {
      return connectTimeoutMs != null && connectTimeoutMs != 10000;
    }
  }
}
