/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

interface TriggerPayload

data class JenkinsTriggerPayload
@JsonCreator constructor(
  @JsonProperty("master") val master: String,
  @JsonProperty("job") val job: String,
  @JsonProperty("buildNumber") val buildNumber: Int,
  @JsonProperty("propertyFile") val propertyFile: String?
) : TriggerPayload {

  var buildInfo: BuildInfo? = null
  var properties: Map<String, Any> = mutableMapOf()

  data class BuildInfo
  @JsonCreator constructor(
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("number") val number: Int,
    @param:JsonProperty("url") val url: String,
    @JsonProperty("artifacts") val artifacts: List<JenkinsArtifact>? = emptyList(),
    @JsonProperty("scm") val scm: List<SourceControl>? = emptyList(),
    @param:JsonProperty("building") val isBuilding: Boolean,
    @param:JsonProperty("result") val result: String?
  ) {
    @get:JsonIgnore val fullDisplayName: String
      get() = name + " #" + number
  }

  data class SourceControl
  @JsonCreator constructor(
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("branch") val branch: String,
    @param:JsonProperty("sha1") val sha1: String
  )

  data class JenkinsArtifact
  @JsonCreator constructor(
    @param:JsonProperty("fileName") val fileName: String,
    @param:JsonProperty("relativePath") val relativePath: String
  )
}

data class DockerTriggerPayload
@JsonCreator constructor(
  @JsonProperty("account") val account: String,
  @JsonProperty("repository") val repository: String,
  @JsonProperty("tag") val tag: String?
) : TriggerPayload

data class PipelineTriggerPayload
@JsonCreator constructor(
  @JsonProperty("parentExecution") val parentExecution: Execution,
  @JsonProperty("parentPipelineStageId") val parentPipelineStageId: String? = null
) : TriggerPayload {
  constructor(parentExecution: Execution) : this(parentExecution, null)

  @JsonIgnore
  val parentStage: Stage? =
    if (parentPipelineStageId != null) {
      parentExecution.stageById(parentPipelineStageId)
    } else {
      null
    }
}

data class GitTriggerPayload
@JsonCreator constructor(
  @JsonProperty("source") val source: String,
  @JsonProperty("project") val project: String,
  @JsonProperty("branch") val branch: String,
  @JsonProperty("slug") val slug: String
) : TriggerPayload
