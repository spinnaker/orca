package com.netflix.spinnaker.q

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id.MINIMAL_CLASS

@JsonTypeInfo(use = MINIMAL_CLASS, include = PROPERTY, property = "@class")
interface Attribute

data class MaxAttemptsAttribute(val maxAttempts: Int = -1) : Attribute

data class AttemptsAttribute(var attempts: Int = 0) : Attribute

@JsonTypeInfo(use = MINIMAL_CLASS, include = PROPERTY, property = "@class")
abstract class Message {
  // TODO: this type should be immutable
  private val _attributes: MutableList<Attribute> = mutableListOf()
  val attributes: List<Attribute>
    get() = _attributes

  inline fun <reified A : Attribute> getAttribute() =
    attributes.find { it is A } as A?

  fun <A : Attribute> setAttribute(attribute: A): A {
    _attributes.add(attribute)

    return attribute
  }
}