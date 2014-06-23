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

package com.netflix.spinnaker.orca.test.httpserver

import groovy.transform.CompileStatic
import com.sun.net.httpserver.HttpExchange
import static java.net.HttpURLConnection.HTTP_BAD_METHOD

@CompileStatic
class MethodFilteringHttpHandler extends HttpHandlerChain {

  private final String httpMethod

  MethodFilteringHttpHandler(String httpMethod) {
    this.httpMethod = httpMethod
  }

  @Override
  void handle(HttpExchange exchange) {
    if (exchange.requestMethod == httpMethod) {
      next exchange
    } else {
      exchange.sendResponseHeaders HTTP_BAD_METHOD, 0
    }
  }
}
