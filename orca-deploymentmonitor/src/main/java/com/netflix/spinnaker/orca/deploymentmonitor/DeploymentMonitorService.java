/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.deploymentmonitor;

import com.netflix.spinnaker.orca.deploymentmonitor.models.*;
import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.POST;

public interface DeploymentMonitorService {
  @POST("/deployment/starting")
  EvaluateHealthResponse notifyStarting(@Body RequestBase request);

  @POST("/deployment/completed")
  Response notifyCompleted(@Body DeploymentCompletedRequest request);

  @POST("/deployment/evaluateHealth")
  EvaluateHealthResponse evaluateHealth(@Body EvaluateHealthRequest request);
}
