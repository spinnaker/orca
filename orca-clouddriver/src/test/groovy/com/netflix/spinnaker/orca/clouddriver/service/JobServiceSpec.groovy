/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.service

import com.netflix.spinnaker.orca.api.PreconfiguredJobConfigurationProvider
import com.netflix.spinnaker.orca.api.PreconfiguredJobStageProperties
import com.netflix.spinnaker.orca.api.TitusPreconfiguredJobProperties
import com.netflix.spinnaker.orca.clouddriver.config.JobConfigurationProperties
import spock.lang.Specification

class JobServiceSpec extends Specification {

  def 'should initialize the preconfigured job stages with config objects'() {
    given:
    JobConfigurationProperties jobConfigurationProperties = new JobConfigurationProperties(titus: [new TitusPreconfiguredJobProperties("test", "type")])

    when:
    List<PreconfiguredJobStageProperties> jobStageProperties = new JobService(jobConfigurationProperties, null).preconfiguredStages

    then:
    jobStageProperties.size() == 1
  }

  def 'should initialize the preconfigured job stages via config objects & provider objects'() {
    given:
    JobConfigurationProperties jobConfigurationProperties = new JobConfigurationProperties(titus: [new TitusPreconfiguredJobProperties("test", "type")])
    PreconfiguredJobConfigurationProvider provider = new TestPreconfiguredJobConfigurationProvider()

    when:
    List<PreconfiguredJobStageProperties> jobStageProperties = new JobService(jobConfigurationProperties, [provider]).preconfiguredStages

    then:
    jobStageProperties.size() == 2
  }

  class TestPreconfiguredJobConfigurationProvider implements PreconfiguredJobConfigurationProvider {

    @Override
    List<TitusPreconfiguredJobProperties> getJobConfigurations() {
      TitusPreconfiguredJobProperties jobProps = new TitusPreconfiguredJobProperties("jobconfigplugin", "pluginimpl")
      jobProps.cluster.imageId = 'someId'
      jobProps.cluster.region = 'us-east-1'
      jobProps.cluster.application = 'test'
      return [jobProps]
    }
  }

}
