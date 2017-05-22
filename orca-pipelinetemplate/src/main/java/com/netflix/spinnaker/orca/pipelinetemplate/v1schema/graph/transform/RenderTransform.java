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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.transform;

import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.IllegalTemplateConfigurationException;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.PipelineTemplateVisitor;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PartialDefinition;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Variable;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.DefaultRenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderUtil;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors.Error;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RenderTransform implements PipelineTemplateVisitor {

  TemplateConfiguration templateConfiguration;

  Renderer renderer;

  Registry registry;

  Map<String, Object> trigger;

  private final Timer renderTemplateTimer;

  public RenderTransform(TemplateConfiguration templateConfiguration, Renderer renderer, Registry registry, Map<String, Object> trigger) {
    this.templateConfiguration = templateConfiguration;
    this.renderer = renderer;
    this.registry = registry;
    this.trigger = trigger;
    this.renderTemplateTimer = registry.timer("server.renderPipelineTemplate");
  }

  @Override
  public void visitPipelineTemplate(PipelineTemplate pipelineTemplate) {
    long start = registry.clock().monotonicTime();
    render(pipelineTemplate);
    long end = registry.clock().monotonicTime();
    renderTemplateTimer.record(end - start, TimeUnit.NANOSECONDS);
  }

  private void render(PipelineTemplate template) {
    RenderContext context = new DefaultRenderContext(templateConfiguration.getPipeline().getApplication(), template, trigger);

    if (template.getVariables() != null) {
      template.getVariables().stream()
        .filter(Variable::hasDefaultValue)
        .forEach(v -> context.getVariables().put(v.getName(), v.getDefaultValue()));
    }

    context.getVariables().putAll(templateConfiguration.getPipeline().getVariables());

    // We only render the stages here, whereas modules will be rendered only if used within stages.
    renderStages(filterStages(template.getStages(), false), context, "template");
    renderStages(filterStages(templateConfiguration.getStages(), false), context, "configuration");

    // We don't care about configuration partials, they were already merged into the template at this point
    renderPartials(template.getPartials(), filterStages(template.getStages(), true), context);
  }

  private void renderStages(List<StageDefinition> stages, RenderContext context, String locationNamespace) {
    if (stages == null) {
      return;
    }

    for (StageDefinition stage : stages) {
      if (stage.isPartialType()) {
        // Partials are handled separately
        continue;
      }

      context.setLocation(String.format("%s:stages.%s", locationNamespace, stage.getId()));
      renderStage(stage, context, locationNamespace);
    }
  }

  @SuppressWarnings("unchecked")
  private void renderStage(StageDefinition stage, RenderContext context, String locationNamespace) {
    Object rendered;
    try {
      rendered = RenderUtil.deepRender(renderer, stage.getConfig(), context);
    } catch (TemplateRenderException e) {
      throw TemplateRenderException.fromError(
        new Error()
          .withMessage("Failed rendering stage")
          .withLocation(context.getLocation()),
        e
      );
    }

    if (!(rendered instanceof Map)) {
      throw new IllegalTemplateConfigurationException(new Errors.Error()
        .withMessage("A stage's rendered config must be a map")
        .withCause("Received type " + rendered.getClass().toString())
        .withLocation(context.getLocation())
      );
    }
    stage.setConfig((Map<String, Object>) rendered);

    stage.setName(renderStageProperty(stage.getName(), context, getStagePropertyLocation(locationNamespace, stage.getId(), "name")));
    stage.setComments(renderStageProperty(stage.getComments(), context, getStagePropertyLocation(locationNamespace, stage.getId(), "comments")));
  }

  private String renderStageProperty(String input, RenderContext context, String location) {
    try {
      return (String) RenderUtil.deepRender(renderer, input, context);
    } catch (TemplateRenderException e) {
      throw TemplateRenderException.fromError(
        new Error()
          .withMessage("Failed rendering stage property")
          .withLocation(location),
        e
      );
    }
  }

  private static String getStagePropertyLocation(String namespace, String stageId, String propertyName) {
    return String.format("%s:stages.%s.%s", namespace, stageId, propertyName);
  }

  private static List<StageDefinition> filterStages(List<StageDefinition> stages, boolean partialsOnly) {
    return stages.stream().filter(s -> partialsOnly == s.isPartialType()).collect(Collectors.toList());
  }

  private void renderPartials(List<PartialDefinition> partials, List<StageDefinition> stages, RenderContext context) {
    for (StageDefinition stage : stages) {
      String partialId = stage.getPartialId();
      if (partialId == null) {
        throw TemplateRenderException.fromError(
          new Error()
            .withMessage("Stage with partial type has malformed ID format")
            .withCause(String.format("Expected 'partial:PARTIAL_ID' got '%s'", stage.getType()))
            .withLocation(String.format("template:stages.%s", stage.getId()))
        );
      }
      PartialDefinition partial = partials.stream().filter(p -> p.getId().equals(partialId)).findFirst().orElseThrow(() -> new TemplateRenderException("guh"));

      RenderContext partialContext = context.copy();
      partialContext.getVariables().putAll(stage.getConfig());
      partialContext.setLocation(String.format("partial:%s.%s", stage.getId(), partial.getId()));

      List<StageDefinition> renderedStages = new ArrayList<>();
      for (StageDefinition partialStage : partial.getStages()) {
        // TODO rz - add recursive partials support
        if (partialStage.isPartialType()) {
          throw TemplateRenderException.fromError(
            new Error()
              .withMessage("Recursive partials support is not currently implemented")
              .withLocation(String.format("partial:%s", partial.getId()))
          );
        }

        StageDefinition renderedStage;
        try {
          renderedStage = (StageDefinition) partialStage.clone();
        } catch (CloneNotSupportedException e) {
          // This definitely should never happen. Yay checked exceptions.
          throw new TemplateRenderException("StageDefinition clone unsupported", e);
        }

        renderedStage.setId(String.format("%s.%s", stage.getId(), renderedStage.getId()));
        renderedStage.setDependsOn(
          renderedStage.getDependsOn().stream()
            .map(d -> String.format("%s.%s", stage.getId(), d))
            .collect(Collectors.toSet())
        );

        renderStage(renderedStage, partialContext, String.format("partial:%s.%s", stage.getId(), partial.getId()));

        renderedStages.add(renderedStage);
      }
      partial.getRenderedPartials().put(stage.getId(), renderedStages);
    }
  }
}
