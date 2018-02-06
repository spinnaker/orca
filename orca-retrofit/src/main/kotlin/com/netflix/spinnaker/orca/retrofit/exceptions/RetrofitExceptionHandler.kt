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
import retrofit.RetrofitError.Kind.NETWORK
import retrofit.http.RestMethod
import retrofit.mime.TypedByteArray
import java.lang.reflect.Method
import java.net.HttpURLConnection.*

@Order(HIGHEST_PRECEDENCE)
class RetrofitExceptionHandler : ExceptionHandler {
  override fun handles(e: Exception): Boolean {
    return e.javaClass == RetrofitError::class.java
  }

  override fun handle(stepName: String, ex: Exception): ExceptionHandler.Response {
    val e = ex as RetrofitError
    return e.response.run {
      val responseDetails = mutableMapOf<String, Any?>()

      try {
        val body = e.getBodyAs(Map::class.java) as Map<String, Any>

        val error = body["error"] as String? ?: reason
        val errors: MutableList<String> = body["errors"] as MutableList<String>?
          ?: (body["messages"]
            ?: mutableListOf<String>()) as MutableList<String>
        if (errors.isEmpty()) {
          errors.addAll(body["message"] as List<String>? ?: emptyList())
        }

        responseDetails.putAll(ExceptionHandler.responseDetails(error, errors))

        if (body["exception"] != null) {
          responseDetails["rootException"] = body["exception"] as Any
        }
      } catch (_: Exception) {
        responseDetails.putAll(ExceptionHandler.responseDetails(reason
          ?: e.message))
      }

      try {
        responseDetails["responseBody"] = (e.response.body as TypedByteArray).bytes.contentToString()
      } catch (_: Exception) {
        responseDetails["responseBody"] = null
      }
      responseDetails["kind"] = e.kind
      responseDetails["status"] = status
      responseDetails["url"] = url
      val shouldRetry = ((isNetworkError(e) || isGatewayTimeout(e) || isThrottle(e)) && isIdempotentRequest(e))

      ExceptionHandler.Response(e.javaClass.simpleName, stepName, responseDetails, shouldRetry)
    }
  }

  companion object {
    private val HTTP_TOO_MANY_REQUESTS = 429
  }

  private fun isGatewayTimeout(e: RetrofitError) =
    e.kind == HTTP && e.response.status in listOf(HTTP_BAD_GATEWAY, HTTP_UNAVAILABLE, HTTP_GATEWAY_TIMEOUT)

  private fun isThrottle(e: RetrofitError) =
    e.kind == HTTP && e.response.status == HTTP_TOO_MANY_REQUESTS

  private fun isNetworkError(e: RetrofitError) =
    e.kind == NETWORK

  private fun isIdempotentRequest(e: RetrofitError) =
    findHttpMethodAnnotation(e) in listOf("GET", "HEAD", "DELETE", "PUT")

  private fun findHttpMethodAnnotation(exception: RetrofitError): String? {
    return try {
      exception
        .stackTrace
        .map { Class.forName(it.className).getDeclaredMethod(it.methodName) }
        .firstOrNull { it.isAnnotationPresent<RestMethod>() }
        ?.getAnnotation<RestMethod>()?.value
    } catch (_: ClassNotFoundException) {
      // inner class or something non-accessible
      null
    }
  }
}

private inline fun <reified T : Annotation> Method.isAnnotationPresent(): Boolean =
  isAnnotationPresent(T::class.java)

private inline fun <reified T : Annotation> Method.getAnnotation(): T? =
  getAnnotation(T::class.java)
