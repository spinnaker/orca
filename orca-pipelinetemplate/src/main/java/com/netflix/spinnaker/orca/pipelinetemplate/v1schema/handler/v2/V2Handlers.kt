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

package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.handler.v2

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipelinetemplate.handler.Handler
import com.netflix.spinnaker.orca.pipelinetemplate.handler.HandlerChain
import com.netflix.spinnaker.orca.pipelinetemplate.handler.HandlerGroup
import com.netflix.spinnaker.orca.pipelinetemplate.handler.PipelineTemplateContext
import com.netflix.spinnaker.orca.pipelinetemplate.handler.v2.V2PipelineTemplateContext
import com.netflix.spinnaker.orca.pipelinetemplate.loader.v2.V2TemplateLoader
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.v2.V2GraphMutator
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.validator.v2.V2TemplateConfigurationSchemaValidator
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.validator.v2.V2TemplateSchemaValidator
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.V2SchemaExecutionGenerator
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.stream.Collectors

@Component
class V2SchemaHandlerGroup
@Autowired constructor(
  private val templateLoader: V2TemplateLoader,
  private val objectMapper: ObjectMapper,
  private val contextParameterProcessor: ContextParameterProcessor
): HandlerGroup {

  override fun getHandlers(): List<Handler>
    = listOf(
    V2TemplateLoaderHandler(templateLoader, contextParameterProcessor, objectMapper),
    V2ConfigurationValidationHandler(),
    V2TemplateValidationHandler(),
    V2GraphMutatorHandler(),
    V2PipelineGenerator()
  )
}

class V2ConfigurationValidationHandler : Handler {
  override fun handle(chain: HandlerChain, context: PipelineTemplateContext) {
    val errors = Errors()

    val ctx = context.getSchemaContext<V2PipelineTemplateContext>()
    V2TemplateConfigurationSchemaValidator().validate(
      ctx.configuration,
      errors,
      V2TemplateConfigurationSchemaValidator.SchemaValidatorContext(
        ctx.template.stages.stream().map { it["id"].toString() }.collect(Collectors.toList())
      )
    )
    if (errors.hasErrors(context.getRequest().plan)) {
      context.getErrors().addAll(errors)
      chain.clear()
    }
  }
}

class V2TemplateValidationHandler : Handler {
  override fun handle(chain: HandlerChain, context: PipelineTemplateContext) {
    val errors = Errors()
    V2TemplateSchemaValidator<V2TemplateSchemaValidator.SchemaValidatorContext>().validate(
      context.getSchemaContext<V2PipelineTemplateContext>().template,
      errors,
      V2TemplateSchemaValidator.SchemaValidatorContext(
        !context.getSchemaContext<V2PipelineTemplateContext>().configuration.stages.isEmpty()
      )
    )
    if (errors.hasErrors(context.getRequest().plan)) {
      context.getErrors().addAll(errors)
      chain.clear()
    }
  }
}

class V2GraphMutatorHandler : Handler {
  override fun handle(chain: HandlerChain, context: PipelineTemplateContext) {
    val ctx = context.getSchemaContext<V2PipelineTemplateContext>()
    val mutator = V2GraphMutator(ctx.configuration)
    mutator.mutate(ctx.template)
  }
}

class V2PipelineGenerator : Handler {
  override fun handle(chain: HandlerChain, context: PipelineTemplateContext) {
    val ctx = context.getSchemaContext<V2PipelineTemplateContext>()
    val generator = V2SchemaExecutionGenerator()
    context.getProcessedOutput().putAll(generator.generate(ctx.template, ctx.configuration, context.getRequest()))
  }
}
