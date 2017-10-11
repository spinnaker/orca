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

package com.netflix.spinnaker.orca.clouddriver.expressions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.clouddriver.MortService;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.pipeline.expressions.ExpressionFunctionProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class CloudDriverExpressionFunctionProvider implements ExpressionFunctionProvider {
  private static final AtomicReference<ObjectMapper> objectMapper = new AtomicReference<>();
  private static final AtomicReference<OortService> oortService = new AtomicReference<>();
  private static final AtomicReference<MortService> mortService = new AtomicReference<>();

  @Autowired
  public CloudDriverExpressionFunctionProvider(ObjectMapper objectMapper,
                                               OortService oortService,
                                               MortService mortService) {
    CloudDriverExpressionFunctionProvider.objectMapper.set(objectMapper);
    CloudDriverExpressionFunctionProvider.oortService.set(oortService);
    CloudDriverExpressionFunctionProvider.mortService.set(mortService);
  }

  @Override
  public String getNamespace() {
    return "api_v1";
  }

  @Override
  public Collection<FunctionDefinition> getFunctions() {
    List<FunctionDefinition> functions = new ArrayList<>();
    functions.add(new FunctionDefinition(
      "echo", Collections.singletonList(new FunctionParameter(String.class, "value"))
    ));
    functions.add(new FunctionDefinition(
      "getTargetServerGroup",
      Arrays.asList(
        new FunctionParameter(String.class, "application"),
        new FunctionParameter(String.class, "account"),
        new FunctionParameter(String.class, "cluster"),
        new FunctionParameter(String.class, "cloudProvider"), // TODO-AJ this should be an optional parameter
        new FunctionParameter(String.class, "scope", "Region, zone or namespace depending on cloud provider"),
        new FunctionParameter(String.class, "target", "One of CURRENT, PREVIOUS, OLDEST, LARGEST")
      )
    ));

    return functions;
  }

  static String echo(String parameter) {
    return "Hello " + parameter;
  }

  static List<Map> getTargetServerGroup(String application,
                                        String account,
                                        String cluster,
                                        String cloudProvider,
                                        String scope,
                                        String target) {
    try {
      Response response = oortService.get().getTargetServerGroup(application, account, cluster, cloudProvider, scope, target);
      Map targetServerGroup = objectMapper.get().readValue(response.getBody().in(), new TypeReference<Map>() {
      });
      return Collections.singletonList(targetServerGroup);
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }
}
