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

package com.netflix.spinnaker.orca.web.config

import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowire
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.web.DefaultErrorAttributes
import org.springframework.boot.autoconfigure.web.ErrorAttributes
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter

import javax.servlet.*
import javax.servlet.http.HttpServletResponse

@Configuration
@ComponentScan(basePackages = 'com.netflix.spinnaker.orca.controllers')
@CompileStatic
class WebConfiguration extends WebMvcConfigurerAdapter {
  @Autowired
  ExtendedRegistry extendedRegistry

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
      new MetricsInterceptor(
        extendedRegistry, "controller.invocations", ["application"], ["BasicErrorController"]
      )
    )
  }

  @Bean(name = "objectMapper", autowire = Autowire.BY_TYPE) OrcaObjectMapper orcaObjectMapper() {
    new OrcaObjectMapper()
  }

  @Bean
  Filter authenticatedRequestFilter() {
    new AuthenticatedRequestFilter(true)
  }

  @Bean
  Filter simpleCORSFilter() {
    new Filter() {
      public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers", "x-requested-with, content-type");
        chain.doFilter(req, res);
      }

      public void init(FilterConfig filterConfig) {}

      public void destroy() {}
    }
  }

  @Bean
  ErrorAttributes errorAttributes() {
    return new ErrorAttributes() {

      DefaultErrorAttributes defaultErrorAttributes = new DefaultErrorAttributes()

      @Override
      Map<String, Object> getErrorAttributes(RequestAttributes attrs, boolean includeStackTrace) {
        Map<String, Object> errorAttributes = defaultErrorAttributes.getErrorAttributes(attrs, includeStackTrace)
        // By default, Spring echoes back the user's requested path. This opens up a potential XSS vulnerability where a
        // user, for example, requests "GET /<script>alert('Hi')</script> HTTP/1.1".
        errorAttributes.remove("path")
        return errorAttributes
      }

      @Override
      Throwable getError(RequestAttributes requestAttributes) {
        return defaultErrorAttributes.getError(requestAttributes)
      }
    }
  }
}
