/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions;
import com.netflix.spinnaker.orca.pipeline.expressions.ExpressionEvaluationSummary;
import com.netflix.spinnaker.orca.pipeline.expressions.ExpressionEvaluator;
import com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator;
import com.netflix.spinnaker.orca.pipeline.model.JenkinsTrigger;
import com.netflix.spinnaker.orca.pipeline.model.JenkinsTrigger.BuildInfo;
import com.netflix.spinnaker.orca.pipeline.model.JenkinsTrigger.SourceControl;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator.ExpressionEvaluationVersion.V2;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

/**
 * Common methods for dealing with passing context parameters used by both Script and Jenkins stages
 */
public class ContextParameterProcessor {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private ExpressionEvaluator expressionEvaluator;

  public ContextParameterProcessor() {
    this(new ContextFunctionConfiguration(new UserConfiguredUrlRestrictions.Builder().build(), V2));
  }

  public ContextParameterProcessor(ContextFunctionConfiguration contextFunctionConfiguration) {
    expressionEvaluator = new PipelineExpressionEvaluator(contextFunctionConfiguration);
  }

  public Map<String, Object> process(Map<String, Object> source, Map<String, Object> context, boolean allowUnknownKeys) {
    if (source.isEmpty()) {
      return new HashMap<>();
    }

    ExpressionEvaluationSummary summary = new ExpressionEvaluationSummary();
    Map<String, Object> result = expressionEvaluator.evaluate(
      source,
      precomputeValues(context),
      summary,
      allowUnknownKeys
    );

    if (summary.getTotalEvaluated() > 0 && context.containsKey("execution")) {
      log.info("Evaluated {}", summary);
    }

    if (summary.getFailureCount() > 0) {
      result.put("expressionEvaluationSummary", summary.getExpressionResult());
    }

    return result;
  }

  public Map<String, Object> buildExecutionContext(Stage stage, boolean includeStageContext) {
    Map<String, Object> augmentedContext = new HashMap<>();
    if (includeStageContext) {
      augmentedContext.putAll(stage.getContext());
    }
    if (stage.getExecution().getType() == PIPELINE) {
      augmentedContext.put("trigger", stage.getExecution().getTrigger());
      augmentedContext.put("execution", stage.getExecution());
    }

    return augmentedContext;
  }

  public static boolean containsExpression(String value) {
    return isNotEmpty(value) && value.contains("${");
  }

  private Map<String, Object> precomputeValues(Map<String, Object> context) {
    Trigger trigger = (Trigger) context.get("trigger");
    if (trigger != null && !trigger.getParameters().isEmpty()) {
      context.put("parameters", trigger.getParameters());
    }

    context.put("scmInfo", Optional.ofNullable((BuildInfo) context.get("buildInfo")).map(BuildInfo::getScm).orElse(null));
    if (context.get("scmInfo") == null && trigger instanceof JenkinsTrigger) {
      context.put("scmInfo", ((JenkinsTrigger) trigger).getBuildInfo().getScm());
    }
    if (context.get("scmInfo") != null && ((List) context.get("scmInfo")).size() >= 2) {
      List<SourceControl> scmInfos = (List<SourceControl>) context.get("scmInfo");
      SourceControl scmInfo = scmInfos
        .stream()
        .filter(it -> !"master".equals(it.getBranch()) && !"develop".equals(it.getBranch()))
        .findFirst()
        .orElseGet(() -> scmInfos.get(0));
      context.put("scmInfo", scmInfo);
    } else if (context.get("scmInfo") != null && !((List) context.get("scmInfo")).isEmpty()) {
      context.put("scmInfo", ((List) context.get("scmInfo")).get(0));
    } else {
      context.put("scmInfo", null);
    }

    return context;
  }
}
