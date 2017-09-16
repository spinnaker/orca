package com.netflix.spinnaker.q

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id.MINIMAL_CLASS

/**
 * Implemented by all messages used with the [Queue]. Sub-types should be simple
 * immutable value types such as Kotlin data classes or _Lombok_ `@Value`
 * classes.
 */
@JsonTypeInfo(use = MINIMAL_CLASS, include = PROPERTY, property = "@class")
abstract class Message {
  // TODO: this type should be immutable
  private val _attributes: MutableList<Attribute> = mutableListOf()
  val attributes: List<Attribute>
    get() = _attributes

  /**
   * @return the attribute of type [A] or `null`.
   */
  inline fun <reified A : Attribute> getAttribute() =
    attributes.find { it is A } as A?

  /**
   * Adds an attribute of type [A] to the message.
   */
  fun <A : Attribute> setAttribute(attribute: A): A {
    _attributes.add(attribute)

    return attribute
  }
}

/**
 * The base type for message metadata attributes.
 */
@JsonTypeInfo(use = MINIMAL_CLASS, include = PROPERTY, property = "@class")
interface Attribute

/**
 * An attribute representing the maximum number of retries for a message.
 */
data class MaxAttemptsAttribute(val maxAttempts: Int = -1) : Attribute

/**
 * An attribute representing the number of times a message has been retried.
 */
data class AttemptsAttribute(var attempts: Int = 0) : Attribute
