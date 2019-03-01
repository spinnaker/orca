/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servicebroker;

import com.netflix.spinnaker.orca.clouddriver.pipeline.AbstractCloudProviderAwareStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servicebroker.DeployServiceStagePreprocessor;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import groovy.transform.CompileStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

@Component
class DeployServiceStage extends AbstractCloudProviderAwareStage {
  public static final String PIPELINE_CONFIG_TYPE = "deployService";

  @Autowired(required = false)
  List<DeployServiceStagePreprocessor> deployServiceStagePreprocessors = new ArrayList<>();

  public DeployServiceStage() {
    super(PIPELINE_CONFIG_TYPE);
  }

  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    deployServiceStagePreprocessors
      .stream()
      .filter(it -> it.supports(stage))
      .forEach(it -> it.addSteps(builder, stage));
  }
}
