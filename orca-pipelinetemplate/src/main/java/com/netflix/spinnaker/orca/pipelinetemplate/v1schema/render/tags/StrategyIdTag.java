package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.tags;

import com.hubspot.jinjava.interpret.Context;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.interpret.TemplateSyntaxException;
import com.hubspot.jinjava.lib.tag.Tag;
import com.hubspot.jinjava.tree.TagNode;
import com.hubspot.jinjava.util.HelperStringTokenizer;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors;

import java.util.*;

public class StrategyIdTag implements Tag {
  private static final String APPLICATION = "application";
  private static final String NAME = "name";

  private final Front50Service front50Service;

  public StrategyIdTag(Front50Service front50Service) {
    this.front50Service = front50Service;
  }

  @Override
  public String getEndTagName() {
    return null;
  }

  @Override
  public String getName() {
    return "strategyid";
  }

  @Override
  public String interpret(TagNode tagNode, JinjavaInterpreter interpreter) {
    List<String> helper = new HelperStringTokenizer(tagNode.getHelpers()).splitComma(true).allTokens();
    if (helper.isEmpty()) {
        throw new TemplateSyntaxException(tagNode.getMaster().getImage(), "Tag 'strategyId' expects at least a strategy name: " + helper, tagNode.getLineNumber());
    }

    Map<String, String> paramPairs = new HashMap<>();
    helper.forEach(p -> {
        String[] parts = p.split("=");
        if (parts.length != 2) {
            throw new TemplateSyntaxException(tagNode.getMaster().getImage(), "Tag 'strategyId' expects parameters to be in a 'key=value' format: " + helper, tagNode.getLineNumber());
        }

        paramPairs.put(parts[0], parts[1]);
    });

    Context context = interpreter.getContext();

    String application = paramPairs.getOrDefault(APPLICATION, (String) context.get(APPLICATION)).replaceAll("^[\"\']|[\"\']$", "");
    application = checkContext(application, context);

    String name = paramPairs.get(NAME).replaceAll("^[\"\']|[\"\']$", "");
    name = checkContext(name, context);

    List<Map<String, Object>> strategies = Optional.ofNullable(front50Service.getStrategies(application)).orElse(Collections.emptyList());
    Map<String, Object> result = findStrategy(strategies, application, name);
    return (String) result.get("id");
  }

  private String checkContext(String param, Context context) {
    Object var = context.get(param);

    if (var != null) {
        return (String) var;
    }

    return param;
  }

  private Map<String, Object> findStrategy(List<Map<String, Object>> strategies, String application, String strategyName) {
    return strategies
            .stream()
            .filter(p -> p.get(NAME).equals(strategyName))
            .findFirst()
            .orElseThrow(
                    () -> TemplateRenderException.fromError(
                            new Errors.Error()
                                    .withMessage(String.format("Failed to find strategyId ID with name '%s' in application '%s'", strategyName, application)
                                    )));
  }
}
