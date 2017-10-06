/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.expressions;

import java.util.Collection;
import java.util.List;

public interface ExpressionFunctionProvider {
  String getNamespace();
  Collection<FunctionDefinition> getFunctions();

  class FunctionDefinition {
    private final String name;
    private final List<FunctionParameter> parameters;

    public FunctionDefinition(String name, List<FunctionParameter> parameters) {
      this.name = name;
      this.parameters = parameters;
    }

    public String getName() {
      return name;
    }

    public List<FunctionParameter> getParameters() {
      return parameters;
    }
  }

  class FunctionParameter {
    private final Class type;
    private final String name;
    private final String description;

    public FunctionParameter(Class type, String name) {
      this(type, name, null);
    }

    public FunctionParameter(Class type, String name, String description) {
      this.type = type;
      this.name = name;
      this.description = description;
    }

    public Class getType() {
      return type;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }
  }
}
