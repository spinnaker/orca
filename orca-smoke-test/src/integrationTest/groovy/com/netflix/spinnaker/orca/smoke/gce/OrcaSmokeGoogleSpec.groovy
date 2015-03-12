/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.orca.smoke.gce
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.front50.config.Front50Configuration
import com.netflix.spinnaker.orca.kato.config.KatoConfiguration
import com.netflix.spinnaker.orca.oort.config.OortConfiguration
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.smoke.OrcaSmokeUtils
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.orca.test.net.Network.notReachable
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS
// Only runs if the gcs-kms server is listening on port 7909 on the same machine.
@IgnoreIf({ notReachable("http://localhost:7909") })
@ContextConfiguration(classes = [OrcaConfiguration, KatoConfiguration, BatchTestConfiguration, OortConfiguration,
                                 Front50Configuration])
@DirtiesContext(classMode = AFTER_CLASS)
class OrcaSmokeGoogleSpec extends Specification {

  @Shared String applicationName

  def setupSpec() {
    System.setProperty("kato.baseUrl", "http://localhost:8501")
    System.setProperty("oort.baseUrl", "http://localhost:8081")
    System.setProperty("front50.baseUrl", "http://localhost:8080")

    applicationName = "googletest${System.currentTimeMillis()}"
  }

  @Autowired PipelineStarter jobStarter
  @Autowired ObjectMapper mapper
  @Autowired JobExplorer jobExplorer

  def "can create application"() {
    def configJson = mapper.writeValueAsString(config)

    when:
    def pipeline = jobStarter.start(configJson)
    def jobName = OrcaSmokeUtils.buildJobName(config.application, config.name, pipeline.id)

    then:
    jobExplorer.getJobInstanceCount(jobName) == 1

    def jobInstance = jobExplorer.getJobInstances(jobName, 0, 1)[0]
    def jobExecutions = jobExplorer.getJobExecutions(jobInstance)

    jobExecutions.size == 1

    with (jobExecutions[0]) {
      status == BatchStatus.COMPLETED
      exitStatus == ExitStatus.COMPLETED
    }

    where:
    config = [
      application: applicationName,
      name       : "my-pipeline",
      stages     : [
        [
          type         : "createApplication",
          account      : "my-account-name",
          application  :
            [
              name        : applicationName,
              description : "A test Google application.",
              email       : "some-email-addr@gmail.com",
              pdApiKey    : "Some pager key."
            ],
          description  : "Create new googletest application as smoke test."
        ]
      ]
    ]
  }

  def "can deploy server group"() {
    def configJson = mapper.writeValueAsString(config)

    when:
    def pipeline = jobStarter.start(configJson)
    def jobName = OrcaSmokeUtils.buildJobName(config.application, config.name, pipeline.id)

    then:
    jobExplorer.getJobInstanceCount(jobName) == 1

    def jobInstance = jobExplorer.getJobInstances(jobName, 0, 1)[0]
    def jobExecutions = jobExplorer.getJobExecutions(jobInstance)

    jobExecutions.size == 1

    with (jobExecutions[0]) {
      status == BatchStatus.COMPLETED
      exitStatus == ExitStatus.COMPLETED
    }

    where:
    config = [
      application: applicationName,
      name       : "my-pipeline",
      stages     : [
        [
          type         : "linearDeploy",
          providerType : "gce",
          zone         : "us-central1-b",
          image        : "debian-7-wheezy-v20141021",
          instanceType : "f1-micro",
          capacity     : [ desired : 2 ],
          application  : applicationName,
          stack        : "test",
          credentials  : "my-account-name",
          user         : "smoke-test-user",
          description  : "Create new server group in cluster googletest as smoke test."
        ]
      ]
    ]
  }
}
