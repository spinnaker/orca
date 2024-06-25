/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package com.netflix.spinnaker.orca.sql

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger
import com.netflix.spinnaker.orca.pipeline.model.support.CustomTriggerDeserializerSupplier
import com.netflix.spinnaker.orca.pipeline.model.support.mapValue
import com.netflix.spinnaker.orca.pipeline.model.support.listValue
import com.netflix.spinnaker.orca.sql.pipeline.persistence.PipelineRefTrigger

class PipelineRefTriggerDeserializerSupplier : CustomTriggerDeserializerSupplier {

  override val rule: CustomTriggerDeserializerSupplier.OTHER_FIELD_RULE
    get() = CustomTriggerDeserializerSupplier.OTHER_FIELD_RULE.EMPTY

  override val predicateByNode: (node: JsonNode) -> Boolean
    get() = { node ->
      node.looksLikePipeline() || node.isPipelineRefTrigger()
    }

  override val predicateByTrigger: (trigger: Trigger) -> Boolean
    get() = {
      it.type == "pipelineRef"
    }

  override val deserializer: (node: JsonNode, parser: JsonParser) -> Trigger
    get() = { node, parser ->

      when {
        node.looksLikePipeline() -> {
          with(node) {
            PipelineRefTrigger(
              correlationId = get("correlationId").textValue(),
              user = get("user").textValue(),
              parameters = get("parameters")?.mapValue(parser) ?: mutableMapOf(),
              artifacts = get("artifacts")?.listValue(parser) ?: mutableListOf(),
              notifications = get("notifications")?.listValue(parser) ?: mutableListOf(),
              isRebake = get("rebake")?.booleanValue() == false,
              isDryRun = get("dryRun")?.booleanValue() == false,
              isStrategy = get("strategy")?.booleanValue() == false,
              parentExecutionId = get("parentExecution").get("id").textValue(),
              parentPipelineStageId = get("parentPipelineStageId").textValue()
            )
          }
        }
        else -> {
          with(node) {
            PipelineRefTrigger(
              correlationId = get("correlationId").textValue(),
              user = get("user").textValue(),
              parameters = get("parameters")?.mapValue(parser) ?: mutableMapOf(),
              artifacts = get("artifacts")?.listValue(parser) ?: mutableListOf(),
              notifications = get("notifications")?.listValue(parser) ?: mutableListOf(),
              isRebake = get("rebake")?.booleanValue() == false,
              isDryRun = get("dryRun")?.booleanValue() == false,
              isStrategy = get("strategy")?.booleanValue() == false,
              parentPipelineStageId = get("parentPipelineStageId").textValue(),
              parentExecutionId = get("parentExecutionId").textValue()

            )
          }
        }
      }
    }

  private fun JsonNode.isPipelineRefTrigger() =
    get("type")?.textValue() == "pipelineRef"

  private fun JsonNode.looksLikePipeline() =
    hasNonNull("parentExecution")
}
