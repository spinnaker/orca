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

package com.netflix.spinnaker.orca.pipelinetemplate.loader;

import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateLoaderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.lang.String.format;

@Component
public class TemplateLoader {
  private Collection<TemplateSchemeLoader> schemeLoaders;
  private final Renderer renderer;

  @Autowired
  public TemplateLoader(Collection<TemplateSchemeLoader> schemeLoaders, Renderer renderer) {
    this.schemeLoaders = schemeLoaders;
    this.renderer = renderer;
  }

  /**
   * @return a LIFO list of pipeline templates
   */
  public List<PipelineTemplate> load(TemplateConfiguration.TemplateSource template) {
    return load(template, null);
  }

  /**
   * @return a LIFO list of pipeline templates
   */
  public List<PipelineTemplate> load(TemplateConfiguration.TemplateSource template, @Nullable RenderContext renderContext) {
    List<PipelineTemplate> pipelineTemplates = new ArrayList<>();

    // To support jinja expressions in the source field, we have to do some extra rendering here before loading the templates
    PipelineTemplate pipelineTemplate = load(parseSource(template.getSource(), renderContext));
    addNewVariablesToRenderContext(renderContext, pipelineTemplate);
    pipelineTemplates.add(0, pipelineTemplate);

    Set<String> seenTemplateSources = new HashSet<>();
    while (pipelineTemplate.getSource() != null) {
      String source = parseSource(pipelineTemplate.getSource(), renderContext);
      if (seenTemplateSources.contains(source)) {
        throw new TemplateLoaderException(
          format("Illegal cycle detected loading pipeline template '%s'", source)
        );
      }
      pipelineTemplate = load(source);
      addNewVariablesToRenderContext(renderContext, pipelineTemplate);
      seenTemplateSources.add(source);
      pipelineTemplates.add(0, pipelineTemplate);
    }

    return pipelineTemplates;
  }

  private void addNewVariablesToRenderContext(@Nullable RenderContext renderContext, PipelineTemplate pipelineTemplate) {
    if (pipelineTemplate.getVariables() != null && renderContext != null) {
      pipelineTemplate.getVariables().stream()
        .filter(Objects::nonNull)
        .forEach(v -> renderContext.getVariables().putIfAbsent(v.getName(), v.getDefaultValue()));
    }
  }

  private PipelineTemplate load(String source) {
    URI uri;
    try {
      uri = new URI(source);
    } catch (URISyntaxException e) {
      throw new TemplateLoaderException(format("Invalid URI '%s'", source), e);
    }

    TemplateSchemeLoader schemeLoader = schemeLoaders.stream()
      .filter(l -> l.supports(uri))
      .findFirst()
      .orElseThrow(() -> new TemplateLoaderException(format("No TemplateSchemeLoader found for '%s'", uri.getScheme())));

    return schemeLoader.load(uri);
  }

  private boolean sourceContainsExpression(String source) {
    return source != null && (source.contains("{{") || source.contains("{%"));
  }

  private String parseSource(String source, @Nullable RenderContext renderContext) {
    if (renderContext != null && sourceContainsExpression(source)) {
      return renderer.render(source, renderContext);
    }
    return source;
  }
}
