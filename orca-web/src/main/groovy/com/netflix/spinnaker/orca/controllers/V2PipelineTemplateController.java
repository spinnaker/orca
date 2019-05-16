/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.controllers;

import com.netflix.spinnaker.orca.extensionpoint.pipeline.ExecutionPreprocessor;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import com.netflix.spinnaker.orca.pipelinetemplate.V2Util;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/v2/pipelineTemplates")
@ConditionalOnExpression("${pipeline-templates.enabled:true}")
@Slf4j
public class V2PipelineTemplateController {

  @Autowired private ContextParameterProcessor contextParameterProcessor;

  @Autowired(required = false)
  private List<ExecutionPreprocessor> executionPreprocessors = new ArrayList<>();

  @RequestMapping(value = "/plan", method = RequestMethod.POST)
  Map<String, Object> plan(@RequestBody Map<String, Object> pipeline) {
    return V2Util.planPipeline(contextParameterProcessor, executionPreprocessors, pipeline);
  }
}
