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
import com.netflix.spinnaker.orca.pipeline.model.*

internal class TriggerDeserializer
  : StdDeserializer<Trigger>(Trigger::class.java) {
  override fun deserialize(parser: JsonParser, context: DeserializationContext): Trigger =
    parser.codec.readTree<JsonNode>(parser).run {
      Trigger(
        get("type").textValue(),
        parseTriggerPayload(parser),
        get("correlationId")?.textValue(),
        get("user")?.textValue() ?: "[anonymous]",
        get("parameters")?.mapValue(parser) ?: mutableMapOf(),
        get("artifacts")?.listValue(parser) ?: mutableListOf(),
        get("notifications")?.listValue(parser) ?: mutableListOf(),
        get("rebake")?.booleanValue() == true,
        get("dryRun")?.booleanValue() == true,
        get("strategy")?.booleanValue() == true
      ).apply {
        resolvedExpectedArtifacts = get("resolvedExpectedArtifacts")?.listValue(parser) ?: mutableListOf()
      }
    }

  private fun JsonNode.parseTriggerPayload(parser: JsonParser): TriggerPayload {
    return when {
      looksLikeDocker() -> parseValue<DockerTriggerPayload>(parser)
      looksLikeJenkins() -> parseValue<JenkinsTriggerPayload>(parser)
      looksLikePipeline() -> parseValue<PipelineTriggerPayload>(parser)
      looksLikeGit() -> parseValue<GitTriggerPayload>(parser)
      else -> parseValue<UnknownTriggerPayload>(parser)
    }
  }

  private fun JsonNode.looksLikeDocker() =
    hasNonNull("account") && hasNonNull("repository") && hasNonNull("tag")

  private fun JsonNode.looksLikeGit() =
    hasNonNull("source") && hasNonNull("project") && hasNonNull("branch") && hasNonNull("slug")

  private fun JsonNode.looksLikeJenkins() =
    hasNonNull("master") && hasNonNull("job") && hasNonNull("buildNumber")

  private fun JsonNode.looksLikePipeline() =
    hasNonNull("parentExecution")

  private fun <E> JsonNode.listValue(parser: JsonParser) =
    parser.codec.treeToValue(this, List::class.java) as List<E>

  private fun <K, V> JsonNode.mapValue(parser: JsonParser) =
    parser.codec.treeToValue(this, Map::class.java) as Map<K, V>

  private inline fun <reified T> JsonNode.parseValue(parser: JsonParser): T =
    parser.codec.treeToValue(this, T::class.java)
}
