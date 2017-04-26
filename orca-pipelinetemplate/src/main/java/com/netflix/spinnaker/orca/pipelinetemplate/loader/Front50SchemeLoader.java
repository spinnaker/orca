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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateLoaderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;

@Component
public class Front50SchemeLoader implements TemplateSchemeLoader {
  private final Front50Service front50Service;

  private final ObjectMapper objectMapper;

  @Autowired
  public Front50SchemeLoader(Front50Service front50Service, ObjectMapper pipelineTemplateObjectMapper) {
    this.front50Service = front50Service;
    this.objectMapper = pipelineTemplateObjectMapper;
  }

  @Override
  public boolean supports(URI uri) {
    String scheme = uri.getScheme();
    return scheme.equalsIgnoreCase("spinnaker");
  }

  @Override
  public PipelineTemplate load(URI uri) {
    String id = uri.getHost();
    try {
      Map<String, Object> pipelineTemplate = front50Service.getPipelineTemplate(id);
      return objectMapper.convertValue(pipelineTemplate, PipelineTemplate.class);
    } catch (Exception e) {
      throw new TemplateLoaderException(e);
    }
  }
}
