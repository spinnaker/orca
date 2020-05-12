/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.titus;

import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.AmazonServerGroupCreator;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator;
import com.netflix.spinnaker.orca.kato.tasks.DeploymentDetailsAware;
import groovy.lang.Closure;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TitusServerGroupCreator implements ServerGroupCreator, DeploymentDetailsAware {

  private final AmazonServerGroupCreator delegate;

  public TitusServerGroupCreator(AmazonServerGroupCreator delegate) {
    this.delegate = delegate;
  }

  @Override
  public List<Map> getOperations(StageExecution stage) {
    return delegate.getOperations(stage);
  }

  @Override
  public boolean isKatoResultExpected() {
    return delegate.isKatoResultExpected();
  }

  @Override
  public String getCloudProvider() {
    return "titus";
  }

  @Override
  public Optional<String> getHealthProviderName() {
    return Optional.of("Titus");
  }

  @Override
  public void withImageFromPrecedingStage(
      StageExecution stage, String targetRegion, String targetCloudProvider, Closure callback) {
    delegate.withImageFromPrecedingStage(stage, targetRegion, targetCloudProvider, callback);
  }

  @Override
  public StageExecution getPreviousStageWithImage(
      StageExecution stage, String targetRegion, String targetCloudProvider) {
    return delegate.getPreviousStageWithImage(stage, targetRegion, targetCloudProvider);
  }

  @Override
  public List<PipelineExecution> getPipelineExecutions(PipelineExecution execution) {
    return delegate.getPipelineExecutions(execution);
  }

  @Override
  public boolean isCloudProviderEqual(StageExecution stage, StageExecution previousStage) {
    return delegate.isCloudProviderEqual(stage, previousStage);
  }

  @Override
  public void withImageFromDeploymentDetails(
      StageExecution stage, String targetRegion, String targetCloudProvider, Closure callback) {
    delegate.withImageFromDeploymentDetails(stage, targetRegion, targetCloudProvider, callback);
  }
}
