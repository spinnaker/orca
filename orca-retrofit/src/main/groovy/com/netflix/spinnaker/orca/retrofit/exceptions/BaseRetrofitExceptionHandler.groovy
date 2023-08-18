/*
 * Copyright 2023 Salesforce, Inc.
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

import java.lang.annotation.Annotation
import java.lang.reflect.Method
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import retrofit.RetrofitError
import retrofit.http.RestMethod
import static java.net.HttpURLConnection.*
import static retrofit.RetrofitError.Kind.HTTP
import static retrofit.RetrofitError.Kind.NETWORK
import static retrofit.RetrofitError.Kind.UNEXPECTED

abstract class BaseRetrofitExceptionHandler implements ExceptionHandler {
  boolean shouldRetry(RetrofitError e) {
    if (isMalformedRequest(e)) {
      return false
    }

    // retry on 503 even for non-idempotent requests
    if (e.kind == HTTP && e.response?.status == HTTP_UNAVAILABLE) {
      return true
    }

    return isIdempotentRequest(e) && (isNetworkError(e) || isGatewayErrorCode(e) || isThrottle(e))
  }

  private boolean isGatewayErrorCode(RetrofitError e) {
    e.kind == HTTP && e.response?.status in [HTTP_BAD_GATEWAY, HTTP_UNAVAILABLE, HTTP_GATEWAY_TIMEOUT]
  }

  private static final int HTTP_TOO_MANY_REQUESTS = 429

  boolean isThrottle(RetrofitError e) {
    e.kind == HTTP && e.response?.status == HTTP_TOO_MANY_REQUESTS
  }

  private boolean isNetworkError(RetrofitError e) {
    e.kind == NETWORK
  }

  private boolean isMalformedRequest(RetrofitError e) {
    // We never want to retry errors like "Path parameter "blah" value must not be null.
    return e.kind == UNEXPECTED && e.message?.contains("Path parameter")
  }

  private static boolean isIdempotentRequest(RetrofitError e) {
    findHttpMethodAnnotation(e) in ["GET", "HEAD", "DELETE", "PUT"]
  }

  private static String findHttpMethodAnnotation(RetrofitError exception) {
    exception.stackTrace.findResult { StackTraceElement frame ->
      try {
        Class.forName(frame.className)
          .interfaces
          .findResult { Class<?> iface ->
          iface.declaredMethods.findAll { Method m ->
            m.name == frame.methodName
          }.findResult { Method m ->
            m.declaredAnnotations.findResult { Annotation annotation ->
              annotation
                .annotationType()
                .getAnnotation(RestMethod)?.value()
            }
          }
        }
      } catch (ClassNotFoundException e) {
        // inner class or something non-accessible
        return null
      } catch (MissingMethodException e) {
        // While testing with RunTaskHandler, there's some case where this code fails with
        //
        // groovy.lang.MissingMethodException: No signature of method:
        // com.netflix.spinnaker.orca.retrofit.exceptions.RetrofitExceptionHandler$_findHttpMethodAnnotation_closure2$_closure3.doCall()
        // is applicable for argument types: (Optional) values: [Optional.empty]
        //
        // so treat this as having no annotation
        return null
      }
    }
  }
}
