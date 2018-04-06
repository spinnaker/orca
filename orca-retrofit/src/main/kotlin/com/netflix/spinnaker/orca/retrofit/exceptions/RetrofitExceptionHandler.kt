/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.retrofit.exceptions

import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import org.springframework.core.Ordered.HIGHEST_PRECEDENCE
import org.springframework.core.annotation.Order
import retrofit.RetrofitError
import retrofit.RetrofitError.Kind.HTTP
import retrofit.client.Response
import retrofit.http.RestMethod
import retrofit.mime.TypedByteArray
import java.net.HttpURLConnection.*

@Order(HIGHEST_PRECEDENCE)
class RetrofitExceptionHandler : ExceptionHandler {
  override fun handles(e: Exception): Boolean {
    return e.javaClass == RetrofitError::class.java
  }

  override fun handle(stepName: String, ex: Exception): ExceptionHandler.Response {
    val e = ex as RetrofitError
    return e.response.let { response: Response? ->
      val responseDetails = mutableMapOf<String, Any?>()

      try {
        val body = e.getBodyAs(Map::class.java) as Map<String, Any>?

        val error = body?.get("error") as String? ?: response?.reason
        val errors: MutableList<String> = body?.get("errors") as MutableList<String>?
          ?: (body?.get("messages")
            ?: mutableListOf<String>()) as MutableList<String>
        if (errors.isEmpty()) {
          (body?.get("message") as String?)?.let { errors.add(it) }
        }

        responseDetails.putAll(ExceptionHandler.responseDetails(error, errors))

        if (body?.get("exception") != null) {
          responseDetails["rootException"] = body["exception"] as Any
        }
      } catch (ex: Exception) {
        ex.printStackTrace()
        responseDetails.putAll(ExceptionHandler.responseDetails(response?.reason
          ?: e.message))
      }

      try {
        responseDetails["responseBody"] = String((e.response.body as TypedByteArray).bytes)
      } catch (_: Exception) {
        responseDetails["responseBody"] = null
      }
      responseDetails["kind"] = e.kind
      responseDetails["status"] = response?.status
      responseDetails["url"] = response?.url
      val shouldRetry = (e.isNetworkError || e.isGatewayTimeout || e.isThrottle) && e.isIdempotentRequest

      ExceptionHandler.Response(e.javaClass.simpleName, stepName, responseDetails, shouldRetry)
    }
  }

  companion object {
    private val HTTP_TOO_MANY_REQUESTS = 429
  }

  private val RetrofitError.isGatewayTimeout: Boolean
    get() = kind == HTTP && response.status in listOf(HTTP_BAD_GATEWAY, HTTP_UNAVAILABLE, HTTP_GATEWAY_TIMEOUT)

  private val RetrofitError.isThrottle: Boolean
    get() = kind == HTTP && response.status == HTTP_TOO_MANY_REQUESTS

  private val RetrofitError.isIdempotentRequest: Boolean
    get() = findHttpMethodAnnotation(this) in listOf("GET", "HEAD", "DELETE", "PUT")

  private fun findHttpMethodAnnotation(exception: RetrofitError): String? {
    return try {
      exception.stackTrace
        .mapNotNull { frame ->
          Class.forName(frame.className)
            .interfaces.asIterable()
            .flatMap { iface -> Class.forName(iface.name).declaredMethods.filter { it.name == frame.methodName } }
            .flatMap { it.declaredAnnotations.asIterable() }
            .firstOrNull { annotation -> annotation.isAnnotationPresent<RestMethod>() }
            ?.getAnnotation<RestMethod>()?.value
        }
        .firstOrNull()
    } catch (_: ClassNotFoundException) {
      // inner class or something non-accessible
      null
    }
  }
}

private inline fun <reified T : Annotation> Annotation.isAnnotationPresent(): Boolean =
  annotationClass.java.isAnnotationPresent(T::class.java)

private inline fun <reified T : Annotation> Annotation.getAnnotation(): T? =
  annotationClass.java.getAnnotation(T::class.java)
