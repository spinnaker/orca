/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.keel

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.KeelService
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.igor.ScmService
import com.netflix.spinnaker.orca.keel.task.PublishDeliveryConfigTask
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Trigger
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import retrofit.RetrofitError
import retrofit.client.Response
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class PublishDeliveryConfigTaskTests : JUnit5Minutests {
  data class ManifestLocation(
    val scmType: String,
    val project: String,
    val repository: String,
    val directory: String,
    val manifest: String,
    val ref: String
  )

  data class Fixture(
    val trigger: Trigger,
    val context: Map<String, Any?>
  ) {

    companion object {
      val objectMapper = ObjectMapper()
      val manifestLocation = ManifestLocation(
        scmType = "stash",
        project = "SPKR",
        repository = "keeldemo",
        directory = ".",
        manifest = "spinnaker.yml",
        ref = "refs/heads/master"
      )
    }

    val manifestLocation = Companion.manifestLocation

    val manifest = mapOf(
      "name" to "keeldemo-manifest",
      "application" to "keeldemo",
      "artifacts" to emptySet<Map<String, Any?>>(),
      "environments" to emptySet<Map<String, Any?>>()
    )

    val scmService: ScmService = mockk(relaxUnitFun = true) {
      every {
        getDeliveryConfigManifest(
          manifestLocation.scmType,
          manifestLocation.project,
          manifestLocation.repository,
          manifestLocation.directory,
          manifestLocation.manifest,
          manifestLocation.ref
        )
      } returns manifest
    }

    val keelService: KeelService = mockk(relaxUnitFun = true) {
      every {
        publishDeliveryConfig(any(), any())
      } returns Response("http://keel", 200, "", emptyList(), null)
    }

    val subject = PublishDeliveryConfigTask(keelService, scmService, objectMapper)

    fun execute(additionalContext: Map<String, Any?> = emptyMap()) =
      subject.execute(
        Stage(
          Execution(Execution.ExecutionType.PIPELINE, "keeldemo").also { it.trigger = trigger },
          Execution.ExecutionType.PIPELINE.toString(),
          context + additionalContext
        )
      )
  }

  fun ManifestLocation.toMap() = Fixture.objectMapper.convertValue<Map<String, Any?>>(this)

  fun tests() = rootContext<Fixture> {
    context("with manual trigger and proper stage context") {
      fixture {
        Fixture(
          DefaultTrigger("manual"),
          Fixture.manifestLocation.toMap()
        )
      }

      test("retrieves specified manifest from SCM and publishes it to keel") {
        val result = execute()

        expectThat(result.status).isEqualTo(ExecutionStatus.SUCCEEDED)

        verify(exactly = 1) {
          scmService.getDeliveryConfigManifest(
            manifestLocation.scmType,
            manifestLocation.project,
            manifestLocation.repository,
            manifestLocation.directory,
            manifestLocation.manifest,
            manifestLocation.ref
          )
        }

        verify(exactly = 1) {
          keelService.publishDeliveryConfig(manifest, trigger.user!!)
        }
      }

      context("manifest not found") {
        modifyFixture {
          with(scmService) {
            every {
              getDeliveryConfigManifest(
                manifestLocation.scmType,
                manifestLocation.project,
                manifestLocation.repository,
                manifestLocation.directory,
                manifestLocation.manifest,
                manifestLocation.ref
              )
            } throws RetrofitError.httpError("http://igor",
              Response("http://igor", 404, "", emptyList(), null),
              null, null)
          }
        }

        test("task fails if manifest not found") {
          val result = execute()
          expectThat(result.status).isEqualTo(ExecutionStatus.TERMINAL)
        }
      }

      context("failure to call downstream services") {
        modifyFixture {
          with(scmService) {
            every {
              getDeliveryConfigManifest(
                manifestLocation.scmType,
                manifestLocation.project,
                manifestLocation.repository,
                manifestLocation.directory,
                manifestLocation.manifest,
                manifestLocation.ref
              )
            } throws RetrofitError.httpError("http://igor",
              Response("http://igor", 503, "", emptyList(), null),
              null, null)
          }
        }

        test("task retries if max retries not reached") {
          var result: TaskResult
          for (attempt in 1..PublishDeliveryConfigTask.MAX_RETRIES) {
            result = execute(mapOf("attempt" to attempt))
            expectThat(result.status).isEqualTo(ExecutionStatus.RUNNING)
            expectThat(result.context["attempt"]).isEqualTo(attempt + 1)
          }
        }

        test("task fails if max retries reached") {
          val result = execute(mapOf("attempt" to PublishDeliveryConfigTask.MAX_RETRIES + 1))
          expectThat(result.status).isEqualTo(ExecutionStatus.TERMINAL)
        }
      }
    }
  }
}
