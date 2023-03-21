/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.manifest;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Collections.emptyMap;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.kork.expressions.ExpressionEvaluationSummary;
import com.netflix.spinnaker.orca.api.pipeline.graph.StageGraphBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.ManifestCoordinates;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.artifacts.CleanupArtifactsTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.*;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.DeployManifestContext.TrafficManagement;
import com.netflix.spinnaker.orca.pipeline.ExpressionAwareStageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeployManifestStage extends ExpressionAwareStageDefinitionBuilder {

  public static final String PIPELINE_CONFIG_TYPE = "deployManifest";

  private final ManifestOperationsHelper manifestOperationsHelper;
  private final GetDeployedManifests deployedManifests;

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    builder
        .withTask(ResolveDeploySourceManifestTask.TASK_NAME, ResolveDeploySourceManifestTask.class)
        .withTask(DeployManifestTask.TASK_NAME, DeployManifestTask.class)
        .withTask("monitorDeploy", MonitorKatoTask.class)
        .withTask(PromoteManifestKatoOutputsTask.TASK_NAME, PromoteManifestKatoOutputsTask.class)
        .withTask(WaitForManifestStableTask.TASK_NAME, WaitForManifestStableTask.class)
        .withTask(CleanupArtifactsTask.TASK_NAME, CleanupArtifactsTask.class)
        .withTask("monitorCleanup", MonitorKatoTask.class)
        .withTask(PromoteManifestKatoOutputsTask.TASK_NAME, PromoteManifestKatoOutputsTask.class)
        .withTask(BindProducedArtifactsTask.TASK_NAME, BindProducedArtifactsTask.class);
  }

  @Override
  public boolean processExpressions(
      @Nonnull StageExecution stage,
      @Nonnull ContextParameterProcessor contextParameterProcessor,
      @Nonnull ExpressionEvaluationSummary summary) {
    DeployManifestContext context = stage.mapTo(DeployManifestContext.class);
    if (context.isSkipExpressionEvaluation()) {
      processDefaultEntries(
          stage, contextParameterProcessor, summary, Collections.singletonList("manifests"));
      return false;
    }
    return true;
  }

  @Override
  public void afterStages(@Nonnull StageExecution stage, @Nonnull StageGraphBuilder graph) {
    TrafficManagement trafficManagement =
        stage.mapTo(DeployManifestContext.class).getTrafficManagement();
    if (trafficManagement.isEnabled()) {
      switch (trafficManagement.getOptions().getStrategy()) {
        case RED_BLACK:
        case BLUE_GREEN:
          disableOldManifests(stage.getContext(), graph);
          break;
        case HIGHLANDER:
          disableOldManifests(stage.getContext(), graph);
          deleteOldManifests(stage.getContext(), graph);
          break;
        case NONE:
          // do nothing
      }
    }
    if (stage.getContext().getOrDefault("noOutput", "false").toString().equals("true")) {
      stage.setOutputs(emptyMap());
    }
  }

  private void disableOldManifests(Map<String, Object> parentContext, StageGraphBuilder graph) {
    addStagesForOldManifests(parentContext, graph, DisableManifestStage.PIPELINE_CONFIG_TYPE);
  }

  private void deleteOldManifests(Map<String, Object> parentContext, StageGraphBuilder graph) {
    addStagesForOldManifests(parentContext, graph, DeleteManifestStage.PIPELINE_CONFIG_TYPE);
  }

  private void addStagesForOldManifests(
      Map<String, Object> parentContext, StageGraphBuilder graph, String defaultStageType) {

    this.deployedManifests
        .get(parentContext)
        .forEach(
            deployedManifest -> {
              manifestOperationsHelper
                  .getOldManifestNames(
                      deployedManifest.getApplication(),
                      deployedManifest.getAccount(),
                      deployedManifest.getClusterName(),
                      deployedManifest.getNamespace(),
                      deployedManifest.getManifestName())
                  .forEach(appendStageToOlderManifests(graph, defaultStageType, deployedManifest));
            });
  }

  @NotNull
  private Consumer<String> appendStageToOlderManifests(
      StageGraphBuilder graph, String defaultStageType, DeployedManifest dm) {
    return name -> {
      var oldManifestNotDeployed =
          this.manifestOperationsHelper.previousDeploymentNeitherStableNorFailed(
              dm.getAccount(), name);
      graph.append(
          (stage) -> {
            stage.setType(
                oldManifestNotDeployed
                    ? DeleteManifestStage.PIPELINE_CONFIG_TYPE
                    : defaultStageType);
            Map<String, Object> context = stage.getContext();
            context.put("account", dm.getAccount());
            context.put("app", dm.getApplication());
            context.put("cloudProvider", dm.getCloudProvider());
            context.put("manifestName", name);
            context.put("location", dm.getNamespace());
          });
    };
  }

  @RequiredArgsConstructor
  static class ManifestOperationsHelper {

    private static final String REPLICA_SET = "replicaSet";
    private static final String KIND = "kind";
    private static final String OUTPUTS_MANIFEST = "outputs.manifests";

    private final OortService oortService;

    ImmutableList<String> getOldManifestNames(
        String application,
        String account,
        String clusterName,
        String namespace,
        String newManifestName) {
      return oortService
          .getClusterManifests(account, namespace, REPLICA_SET, application, clusterName)
          .stream()
          .filter(m -> !m.getFullResourceName().equals(newManifestName))
          .map(ManifestCoordinates::getFullResourceName)
          .collect(toImmutableList());
    }

    List<Map<String, ?>> getNewManifests(Map<String, Object> parentContext) {
      var manifestsFromParentContext = (List<Map<String, ?>>) parentContext.get(OUTPUTS_MANIFEST);
      return manifestsFromParentContext.stream()
          .filter(manifest -> REPLICA_SET.equalsIgnoreCase((String) manifest.get(KIND)))
          .collect(Collectors.toList());
    }

    /*
    During a B/G deployment, if the blue deployment fails to create pods (due to issues such as an incorrect image name),
    the deployment will not fail, but will wait indefinitely to achieve stability.
    This is indicated by status.failed=false and status.stable=false. This method checks for such a situation.
     */
    boolean previousDeploymentNeitherStableNorFailed(String account, String name) {
      var oldManifest = this.oortService.getManifest(account, name, false);

      var status = oldManifest.getStatus();
      var notStable = !status.getStable().isState();
      var notFailed = !status.getFailed().isState();

      return notFailed && notStable;
    }
  }

  @Component
  @RequiredArgsConstructor
  static class GetDeployedManifests {

    private final ManifestOperationsHelper manifestOperationsHelper;

    List<DeployedManifest> get(Map<String, Object> parentContext) {

      var deployedManifests = new ArrayList<DeployedManifest>();
      var account = (String) parentContext.get("account");
      var manifestMoniker = (Map) parentContext.get("moniker");
      var application = (String) manifestMoniker.get("app");

      this.manifestOperationsHelper
          .getNewManifests(parentContext)
          .forEach(
              manifest -> {
                var manifestMetadata = (Map) manifest.get("metadata");
                var annotations = (Map) manifestMetadata.get("annotations");

                deployedManifests.add(
                    new DeployedManifest(
                        account,
                        manifestMoniker,
                        application,
                        (Map) manifest.get("metadata"),
                        String.format("replicaSet %s", manifestMetadata.get("name")),
                        (String) manifestMetadata.get("namespace"),
                        (Map) manifestMetadata.get("annotations"),
                        (String) annotations.get("moniker.spinnaker.io/cluster"),
                        "kubernetes"));
              });
      return deployedManifests;
    }
  }

  @Getter
  @RequiredArgsConstructor
  static class DeployedManifest {
    private final String account;
    private final Map manifestMoniker;
    private final String application;
    private final Map manifestMetadata;
    private final String manifestName;
    private final String namespace;
    private final Map annotations;
    private final String clusterName;
    private final String cloudProvider;
  }
}
