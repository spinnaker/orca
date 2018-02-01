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

package com.netflix.spinnaker.orca.exceptions;

import java.util.Collections;
import java.util.Map;
import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import static java.lang.String.format;
import static org.springframework.core.Ordered.LOWEST_PRECEDENCE;

@Order(LOWEST_PRECEDENCE)
public class DefaultExceptionHandler implements ExceptionHandler {
  private final Logger log = LoggerFactory.getLogger(getClass());

  @Override public boolean handles(Exception e) {
    return true;
  }

  @Override
  public ExceptionHandler.Response handle(String taskName, Exception e) {
    Map<String, Object> exceptionDetails = ExceptionHandler.responseDetails("Unexpected Task Failure", Collections.singletonList(e.getMessage()));
    exceptionDetails.put("stackTrace", Throwables.getStackTraceAsString(e));
    log.warn(format("Error occurred during task %s", taskName), e);
    return new ExceptionHandler.Response(e.getClass().getSimpleName(), taskName, exceptionDetails, false);
  }
}
