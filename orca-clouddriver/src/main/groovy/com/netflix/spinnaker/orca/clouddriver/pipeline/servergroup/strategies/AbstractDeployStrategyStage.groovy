/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies

import groovy.transform.Immutable
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.orca.clouddriver.pipeline.AbstractCloudProviderAwareStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask
import com.netflix.spinnaker.orca.kato.pipeline.strategy.DetermineSourceServerGroupTask
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.kato.tasks.DiffTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import org.springframework.beans.factory.annotation.Autowired
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.newStage

@Slf4j
abstract class AbstractDeployStrategyStage extends AbstractCloudProviderAwareStage {

  @Autowired
  List<Strategy> strategies

  @Autowired
  NoStrategy noStrategy

  @Autowired(required = false)
  List<DiffTask> diffTasks

  @Autowired(required = false)
  List<DeployStagePreProcessor> deployStagePreProcessors = []

  AbstractDeployStrategyStage(String name) {
    super(name)
  }

  /**
   * @return the steps for the stage excluding whatever cleanup steps will be
   * handled by the deployment strategy.
   */
  protected abstract List<StageDefinitionBuilder.TaskDefinition> basicTasks(Stage stage)

  @Override
  def <T extends Execution> List<StageDefinitionBuilder.TaskDefinition> taskGraph(Stage<T> parentStage) {
    // TODO(ttomsu): This is currently an AWS-only stage. I need to add and support the "useSourceCapacity" option.
    def tasks = [
      new StageDefinitionBuilder.TaskDefinition("determineSourceServerGroup", DetermineSourceServerGroupTask),
      new StageDefinitionBuilder.TaskDefinition("determineHealthProviders", DetermineHealthProvidersTask)
    ]

    deployStagePreProcessors.findAll { it.supports(parentStage) }.each {
      it.additionalSteps().each {
        tasks << new StageDefinitionBuilder.TaskDefinition(it.name, it.taskClass)
      }
    }

    correctContext(parentStage)
    Strategy strategy = (Strategy) strategies.findResult(noStrategy, {
      it.name.equalsIgnoreCase(parentStage.context.strategy) ? it : null
    })
    if (!strategy.replacesBasicSteps()) {
      tasks.addAll((basicTasks(parentStage) ?: []))

      if (diffTasks) {
        diffTasks.each { DiffTask diffTask ->
          try {
            tasks << new StageDefinitionBuilder.TaskDefinition(getDiffTaskName(diffTask.class.simpleName), diffTask.class)
          } catch (Exception e) {
            log.error("Unable to build diff task (name: ${diffTask.class.simpleName}: executionId: ${stage.execution.id})", e)
          }
        }
      }
    }

    return tasks
  }

  @Override
  def <T extends Execution> List<Stage<T>> aroundStages(Stage<T> parentStage) {
    correctContext(parentStage)
    Strategy strategy = (Strategy) strategies.findResult(noStrategy, {
      it.name.equalsIgnoreCase(parentStage.context.strategy) ? it : null
    })
    def stages = strategy.composeFlow(parentStage)

    def stageData = parentStage.mapTo(StageData)
    deployStagePreProcessors.findAll { it.supports(parentStage) }.each {
      def defaultContext = [
        credentials  : stageData.account,
        cloudProvider: stageData.cloudProvider
      ]
      it.beforeStageDefinitions().each {
        stages << newStage(
          parentStage.execution,
          it.stageDefinitionBuilder.type,
          it.name,
          defaultContext + it.context,
          parentStage,
          SyntheticStageOwner.STAGE_BEFORE
        )
      }
      it.afterStageDefinitions().each {
        stages << newStage(
          parentStage.execution,
          it.stageDefinitionBuilder.type,
          it.name,
          defaultContext + it.context,
          parentStage,
          SyntheticStageOwner.STAGE_AFTER
        )
      }
    }

    return stages
  }

  /**
   * This nasty method is here because of an unfortunate misstep in pipeline configuration that introduced a nested
   * "cluster" key, when in reality we want all of the parameters to be derived from the top level. To preserve
   * functionality (and not break any contracts), this method is employed to move the nested data back to the context's
   * top-level
   */
  private static void correctContext(Stage stage) {
    if (stage.context.containsKey("cluster")) {
      stage.context.putAll(stage.context.cluster as Map)
    }
    stage.context.remove("cluster")
  }

  static String getDiffTaskName(String className) {
    try {
      className = className[0].toLowerCase() + className.substring(1)
      className = className.replaceAll("Task", "")
    } catch (e) {}
    return className
  }

  @Immutable
  static class CleanupConfig {
    String account
    String cluster
    String cloudProvider
    Location location

    static CleanupConfig fromStage(Stage stage) {
      def stageData = stage.mapTo(StageData)
      def loc = TargetServerGroup.Support.locationFromStageData(stageData)
      new CleanupConfig(
          account: stageData.account,
          cluster: stageData.cluster,
          cloudProvider: stageData.cloudProvider,
          location: loc
      )
    }
  }
}
