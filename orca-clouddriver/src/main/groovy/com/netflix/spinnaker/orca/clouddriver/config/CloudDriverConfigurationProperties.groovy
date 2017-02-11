/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.config

import groovy.transform.Canonical
import org.springframework.boot.context.properties.ConfigurationProperties

@Canonical
@ConfigurationProperties
class CloudDriverConfigurationProperties {
  @Canonical
  static class BaseUrl {
    String baseUrl
  }

  @Canonical
  static class CloudDriver {
    String baseUrl;
    BaseUrl readonly;
  }

  BaseUrl mort
  BaseUrl oort
  BaseUrl kato
  CloudDriver clouddriver

  String getCloudDriverBaseUrl() {
    clouddriver?.baseUrl ?: kato?.baseUrl ?: oort?.baseUrl ?: mort?.baseUrl
  }

  String getCloudDriverReadOnlyBaseUrl() {
    clouddriver?.readonly?.baseUrl ?: getCloudDriverBaseUrl()
  }
}
