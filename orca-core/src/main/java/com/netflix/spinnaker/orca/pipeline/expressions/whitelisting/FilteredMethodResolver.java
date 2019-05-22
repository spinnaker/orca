/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.expressions.whitelisting;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.springframework.expression.spel.support.ReflectiveMethodResolver;

public class FilteredMethodResolver extends ReflectiveMethodResolver {

  private static final List<Method> rejectedMethods = buildRejectedMethods();

  private static List<Method> buildRejectedMethods() {
    try {
      List<Method> allowedObjectMethods =
          asList(
              Object.class.getMethod("equals", Object.class),
              Object.class.getMethod("hashCode"),
              Object.class.getMethod("toString"));
      return ImmutableList.<Method>builder()
          .addAll(
              stream(Object.class.getMethods())
                  .filter(it -> !allowedObjectMethods.contains(it))
                  .collect(toList()))
          .addAll(asList(Class.class.getMethods()))
          .addAll(
              stream(Boolean.class.getMethods())
                  .filter(it -> it.getName().equals("getBoolean"))
                  .collect(toList()))
          .addAll(
              stream(Integer.class.getMethods())
                  .filter(it -> it.getName().equals("getInteger"))
                  .collect(toList()))
          .addAll(
              stream(Long.class.getMethods())
                  .filter(it -> it.getName().equals("getLong"))
                  .collect(toList()))
          .build();
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  protected Method[] getMethods(Class<?> type) {
    Method[] methods = super.getMethods(type);

    List<Method> m = new ArrayList<>(asList(methods));
    m.removeAll(rejectedMethods);
    m =
        m.stream()
            .filter(it -> ReturnTypeRestrictor.supports(it.getReturnType()))
            .collect(toList());

    return m.toArray(new Method[m.size()]);
  }
}
