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

package com.netflix.spinnaker.orca.pipeline.model.support

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger
import com.netflix.spinnaker.orca.pipeline.model.ArtifactoryTrigger
import com.netflix.spinnaker.orca.pipeline.model.ConcourseTrigger
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.DockerTrigger
import com.netflix.spinnaker.orca.pipeline.model.GitTrigger
import com.netflix.spinnaker.orca.pipeline.model.JenkinsTrigger
import com.netflix.spinnaker.orca.pipeline.model.NexusTrigger
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger

class TriggerDeserializer :
  StdDeserializer<Trigger>(Trigger::class.java) {

  companion object {
    val customTriggerSuppliers: MutableSet<CustomTriggerDeserializerSupplier> = mutableSetOf()
  }

  override fun deserialize(parser: JsonParser, context: DeserializationContext): Trigger =
    parser.codec.readTree<JsonNode>(parser).run {
      return when {
        looksLikeDocker() -> DockerTrigger(
          get("type").textValue(),
          get("correlationId")?.textValue(),
          get("user")?.textValue() ?: "[anonymous]",
          get("parameters")?.mapValue(parser) ?: mutableMapOf(),
          get("artifacts")?.listValue(parser) ?: mutableListOf(),
          get("notifications")?.listValue(parser) ?: mutableListOf(),
          get("rebake")?.booleanValue() == true,
          get("dryRun")?.booleanValue() == true,
          get("strategy")?.booleanValue() == true,
          get("account").textValue(),
          get("repository").textValue(),
          get("tag").textValue()
        )
        looksLikeConcourse() -> ConcourseTrigger(
                get("type").textValue(),
                get("correlationId")?.textValue(),
                get("user")?.textValue() ?: "[anonymous]",
                get("parameters")?.mapValue(parser) ?: mutableMapOf(),
                get("artifacts")?.listValue(parser) ?: mutableListOf(),
                get("notifications")?.listValue(parser) ?: mutableListOf(),
                get("rebake")?.booleanValue() == true,
                get("dryRun")?.booleanValue() == true,
                get("strategy")?.booleanValue() == true
        ).apply {
          buildInfo = get("buildInfo")?.parseValue(parser)
          properties = get("properties")?.parseValue(parser) ?: mutableMapOf()
        }
        looksLikeJenkins() -> JenkinsTrigger(
          get("type").textValue(),
          get("correlationId")?.textValue(),
          get("user")?.textValue() ?: "[anonymous]",
          get("parameters")?.mapValue(parser) ?: mutableMapOf(),
          get("artifacts")?.listValue(parser) ?: mutableListOf(),
          get("notifications")?.listValue(parser) ?: mutableListOf(),
          get("rebake")?.booleanValue() == true,
          get("dryRun")?.booleanValue() == true,
          get("strategy")?.booleanValue() == true,
          get("master").textValue(),
          get("job").textValue(),
          get("buildNumber").intValue(),
          get("propertyFile")?.textValue()
        ).apply {
          buildInfo = get("buildInfo")?.parseValue(parser)
          properties = get("properties")?.mapValue(parser) ?: mutableMapOf()
        }
        looksLikePipeline() -> PipelineTrigger(
          get("type").textValue(),
          get("correlationId")?.textValue(),
          get("user")?.textValue() ?: "[anonymous]",
          get("parameters")?.mapValue(parser) ?: mutableMapOf(),
          get("artifacts")?.listValue(parser) ?: mutableListOf(),
          get("notifications")?.listValue(parser) ?: mutableListOf(),
          get("rebake")?.booleanValue() == true,
          get("dryRun")?.booleanValue() == true,
          get("strategy")?.booleanValue() == true,
          get("parentExecution").parseValue<PipelineExecution>(parser),
          get("parentPipelineStageId")?.textValue()
        )
        looksLikeArtifactory() -> ArtifactoryTrigger(
          get("type").textValue(),
          get("correlationId")?.textValue(),
          get("user")?.textValue() ?: "[anonymous]",
          get("parameters")?.mapValue(parser) ?: mutableMapOf(),
          get("artifacts")?.listValue(parser) ?: mutableListOf(),
          get("notifications")?.listValue(parser) ?: mutableListOf(),
          get("rebake")?.booleanValue() == true,
          get("dryRun")?.booleanValue() == true,
          get("strategy")?.booleanValue() == true,
          get("artifactorySearchName").textValue()
        )
        looksLikeNexus() -> NexusTrigger(
          get("type").textValue(),
          get("correlationId")?.textValue(),
          get("user")?.textValue() ?: "[anonymous]",
          get("parameters")?.mapValue(parser) ?: mutableMapOf(),
          get("artifacts")?.listValue(parser) ?: mutableListOf(),
          get("notifications")?.listValue(parser) ?: mutableListOf(),
          get("rebake")?.booleanValue() == true,
          get("dryRun")?.booleanValue() == true,
          get("strategy")?.booleanValue() == true,
          get("nexusSearchName").textValue()
        )
        looksLikeGit() -> GitTrigger(
          get("type").textValue(),
          get("correlationId")?.textValue(),
          get("user")?.textValue() ?: "[anonymous]",
          get("parameters")?.mapValue(parser) ?: mutableMapOf(),
          get("artifacts")?.listValue(parser) ?: mutableListOf(),
          get("notifications")?.listValue(parser) ?: mutableListOf(),
          get("rebake")?.booleanValue() == true,
          get("dryRun")?.booleanValue() == true,
          get("strategy")?.booleanValue() == true,
          get("hash").textValue(),
          get("source").textValue(),
          get("project").textValue(),
          get("branch").textValue(),
          get("slug").textValue(),
          get("action")?.textValue() ?: "undefined"
        )
        looksLikeCustom() -> {
          // chooses the first custom deserializer to keep behavior consistent
          // with the rest of this conditional
          customTriggerSuppliers.first { it.predicate.invoke(this) }.deserializer.invoke(this)
        }
        else -> DefaultTrigger(
          get("type")?.textValue() ?: "none",
          get("correlationId")?.textValue(),
          get("user")?.textValue() ?: "[anonymous]",
          get("parameters")?.mapValue(parser) ?: mutableMapOf(),
          get("artifacts")?.listValue(parser) ?: mutableListOf(),
          get("notifications")?.listValue(parser) ?: mutableListOf(),
          get("rebake")?.booleanValue() == true,
          get("dryRun")?.booleanValue() == true,
          get("strategy")?.booleanValue() == true
        )
      }.apply {
        mapValue<Any>(parser).forEach { (k, v) -> other[k] = v }
        resolvedExpectedArtifacts = get("resolvedExpectedArtifacts")?.listValue(parser) ?: mutableListOf()
      }
    }

  private fun JsonNode.looksLikeDocker() =
    hasNonNull("account") && hasNonNull("repository") && hasNonNull("tag")

  private fun JsonNode.looksLikeGit() =
    hasNonNull("source") && hasNonNull("project") && hasNonNull("branch") && hasNonNull("slug") && hasNonNull("hash")

  private fun JsonNode.looksLikeJenkins() =
    hasNonNull("master") && hasNonNull("job") && hasNonNull("buildNumber")

  private fun JsonNode.looksLikeConcourse() = get("type")?.textValue() == "concourse"

  private fun JsonNode.looksLikePipeline() =
    hasNonNull("parentExecution")

  private fun JsonNode.looksLikeArtifactory() =
    hasNonNull("artifactorySearchName")

  private fun JsonNode.looksLikeNexus() =
    hasNonNull("nexusSearchName")

  private fun JsonNode.looksLikeCustom() =
    customTriggerSuppliers.any { it.predicate.invoke(this) }

  private inline fun <reified E> JsonNode.listValue(parser: JsonParser): MutableList<E> =
    this.map { parser.codec.treeToValue(it, E::class.java) }.toMutableList()

  private inline fun <reified V> JsonNode.mapValue(parser: JsonParser): MutableMap<String, V> {
    val m = mutableMapOf<String, V>()
    this.fields().asSequence().forEach { entry ->
      m[entry.key] = parser.codec.treeToValue(entry.value, V::class.java)
    }
    return m
  }

  private inline fun <reified T> JsonNode.parseValue(parser: JsonParser): T =
    parser.codec.treeToValue(this, T::class.java)
}
