/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package com.netflix.spinnaker.orca.pipeline.model.support

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode

/**
 * Parses the [JsonNode] as a [MutableList].
 */
inline fun <reified E> JsonNode.listValue(parser: JsonParser): MutableList<E> =
  this.map { parser.codec.treeToValue(it, E::class.java) }.toMutableList()

/**
 * Parses the [JsonNode] as a [MutableMap].
 */
inline fun <reified V> JsonNode.mapValue(parser: JsonParser): MutableMap<String, V> {
  val m = mutableMapOf<String, V>()
  this.fields().asSequence().forEach { entry ->
    m[entry.key] = parser.codec.treeToValue(entry.value, V::class.java)
  }
  return m
}

/**
 * Parses the [JsonNode] as a [T].
 */
inline fun <reified T> JsonNode.parseValue(parser: JsonParser): T =
  parser.codec.treeToValue(this, T::class.java)

