package com.netflix.spinnaker.orca.pipeline.expressions;

import com.netflix.spinnaker.kork.expressions.ExpressionsSupport;
import com.netflix.spinnaker.kork.expressions.SpelHelperFunctionException;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.JenkinsTrigger;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.Trigger;
import com.netflix.spinnaker.orca.pipeline.util.HttpClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public class PipelineExpressionsSupport extends ExpressionsSupport {
  private static final Logger LOGGER = LoggerFactory.getLogger(PipelineExpressionsSupport.class);
  private static List<String> DEPLOY_STAGE_NAMES = Arrays.asList("deploy", "createServerGroup", "cloneServerGroup", "rollingPush");
  private static AtomicReference<UserConfiguredUrlRestrictions> helperFunctionUrlRestrictions = new AtomicReference<>();

  PipelineExpressionsSupport(UserConfiguredUrlRestrictions urlRestrictions) {
    super(
      Execution.class,
      Stage.class,
      Trigger.class,
      JenkinsTrigger.BuildInfo.class,
      JenkinsTrigger.JenkinsArtifact.class,
      JenkinsTrigger.SourceControl.class,
      ExecutionStatus.class,
      Execution.AuthenticationDetails.class,
      Execution.PausedDetails.class
    );
    helperFunctionUrlRestrictions.set(urlRestrictions);
  }

  /**
   * Creates a configured Spel evaluation context
   *
   * @param rootObject       the root object to transform
   * @param allowUnknownKeys flag to control what helper functions are available
   * @return an evaluation context hooked with helper functions and correct ACL via whitelisting
   */
  public StandardEvaluationContext buildEvaluationContext(Object rootObject, boolean allowUnknownKeys) {
    StandardEvaluationContext evaluationContext = super.buildEvaluationContext(rootObject, allowUnknownKeys);

    try {
      if (allowUnknownKeys) {
        registerFunction(evaluationContext, "fromUrl", String.class);
        registerFunction(evaluationContext, "jsonFromUrl", String.class);
        registerFunction(evaluationContext, "propertiesFromUrl", String.class);
        registerFunction(evaluationContext, "stage", Object.class, String.class);
        registerFunction(evaluationContext, "stageExists", Object.class, String.class);
        registerFunction(evaluationContext, "judgment", Object.class, String.class);
        registerFunction(evaluationContext, "judgement", Object.class, String.class);
        registerFunction(evaluationContext, "deployedServerGroups", Object.class, String[].class);
      }
    } catch (NoSuchMethodException e) {
      // Indicates a function was not properly registered. This should not happen. Please fix the faulty function
      LOGGER.error("Failed to register helper functions for rootObject {}", rootObject, e);
    }

    return evaluationContext;
  }

  /**
   * Internally registers a Spel method to an evaluation context
   */
  private static void registerFunction(StandardEvaluationContext context, String name, Class<?>... types) throws NoSuchMethodException {
    context.registerFunction(name, PipelineExpressionsSupport.class.getDeclaredMethod(name, types));
  }

  /**
   * Reads a json text
   *
   * @param url url to get the json text
   * @return an object representing the json object
   */
  static Object jsonFromUrl(String url) {
    return readJson(fromUrl(url));
  }

  /**
   * Reads a properties file stored at a url
   *
   * @param url the location of the properties file
   * @return a hashmap representing the properties file
   */
  static Map propertiesFromUrl(String url) {
    try {
      return readProperties(fromUrl(url));
    } catch (Exception e) {
      throw new SpelHelperFunctionException(String.format("#propertiesFromUrl(%s) failed", url), e);
    }
  }

  /**
   * Returns the text response from url
   *
   * @param url used to perform the http get response
   * @return string result from get request
   */
  static String fromUrl(String url) {
    try {
      URL u = helperFunctionUrlRestrictions.get().validateURI(url).toURL();
      return HttpClientUtils.httpGetAsString(u.toString());
    } catch (Exception e) {
      throw new SpelHelperFunctionException(String.format("#from(%s) failed", url), e);
    }
  }

  /**
   * Reads properties from a text
   *
   * @param text text
   * @return a hashmap of the key-value pairs in the text
   * @throws IOException
   */
  static Map readProperties(String text) throws IOException {
    Map map = new HashMap();
    Properties properties = new Properties();
    properties.load(new ByteArrayInputStream(text.getBytes()));
    map.putAll(properties);
    return map;
  }

  /**
   * Finds a Stage by id
   *
   * @param obj #root.execution
   * @param id  the name or id of the stage to find
   * @return a stage specified by id
   */
  static Object stage(Object obj, String id) {
    if (obj instanceof Execution) {
      Execution execution = (Execution) obj;
      return execution.getStages()
        .stream()
        .filter(i -> id != null && (id.equals(i.getName()) || id.equals(i.getId())))
        .findFirst()
        .orElseThrow(
          () -> new SpelHelperFunctionException(
            String.format("Unable to locate [%s] using #stage(%s) in execution %s", id, id, execution.getId())
          )
        );
    }

    throw new SpelHelperFunctionException(String.format("Invalid first param to #stage(%s). must be an execution", id));
  }

  /**
   * Checks existence of a Stage by id
   *
   * @param obj #root.execution
   * @param id  the name or id of the stage to check existence
   * @return W
   */
  static boolean stageExists(Object obj, String id) {
    if (obj instanceof Execution) {
      Execution execution = (Execution) obj;
      return execution.getStages()
        .stream()
        .anyMatch(i -> id != null && (id.equals(i.getName()) || id.equals(i.getId())));
    }

    throw new SpelHelperFunctionException(String.format("Invalid first param to #stage(%s). must be an execution", id));
  }

  /**
   * Finds a stage by id and returns the judgment input text
   *
   * @param obj #root.execution
   * @param id  the name of the stage to find
   * @return the judgment input text
   */
  static String judgment(Object obj, String id) {
    if (obj instanceof Execution) {
      Execution execution = (Execution) obj;
      Stage stageWithJudgmentInput = execution.getStages()
        .stream()
        .filter(isManualStageWithManualInput(id))
        .findFirst()
        .orElseThrow(
          () -> new SpelHelperFunctionException(
            String.format("Unable to locate manual Judgment stage [%s] using #judgment(%s) in execution %s. " +
                "Stage doesn't exist or doesn't contain judgmentInput in its context ",
              id, id, execution.getId()
            )
          )
        );

      return (String) stageWithJudgmentInput.getContext().get("judgmentInput");
    }

    throw new SpelHelperFunctionException(
      String.format("Invalid first param to #judgment(%s). must be an execution", id)
    );
  }

  static List<Map<String, Object>> deployedServerGroups(Object obj, String... id) {
    if (obj instanceof Execution) {
      List<Map<String, Object>> deployedServerGroups = new ArrayList<>();
      ((Execution) obj).getStages()
        .stream()
        .filter(matchesDeployedStage(id))
        .forEach(stage -> {
          String region = (String) stage.getContext().get("region");
          if (region == null) {
            Map<String, Object> availabilityZones = (Map<String, Object>) stage.getContext().get("availabilityZones");
            if (availabilityZones != null) {
              region = availabilityZones.keySet().iterator().next();
            }
          }

          if (region != null) {
            Map<String, Object> deployDetails = new HashMap<>();
            deployDetails.put("account", stage.getContext().get("account"));
            deployDetails.put("capacity", stage.getContext().get("capacity"));
            deployDetails.put("parentStage", stage.getContext().get("parentStage"));
            deployDetails.put("region", region);
            List<Map> existingDetails = (List<Map>) stage.getContext().get("deploymentDetails");
            if (existingDetails != null) {
              existingDetails
                .stream()
                .filter(d -> deployDetails.get("region").equals(d.get("region")))
                .forEach(deployDetails::putAll);
            }

            List<Map> serverGroups = (List<Map>) ((Map) stage.getContext().get("deploy.server.groups")).get(region);
            if (serverGroups != null) {
              deployDetails.put("serverGroup", serverGroups.get(0));
            }

            deployedServerGroups.add(deployDetails);
          }
        });

      return deployedServerGroups;
    }

    throw new IllegalArgumentException("An execution is required for this function");
  }

  /**
   * Alias to judgment
   */
  static String judgement(Object obj, String id) {
    return judgment(obj, id);
  }

  private static Predicate<Stage> isManualStageWithManualInput(String id) {
    return i -> (id != null && id.equals(i.getName())) && (i.getContext() != null && i.getType().equals("manualJudgment") && i.getContext().get("judgmentInput") != null);
  }

  private static Predicate<Stage> matchesDeployedStage(String... id) {
    List<String> idsOrNames = Arrays.asList(id);
    if (!idsOrNames.isEmpty()) {
      return stage -> DEPLOY_STAGE_NAMES.contains(stage.getType()) &&
        stage.getContext().containsKey("deploy.server.groups") &&
        stage.getStatus() == ExecutionStatus.SUCCEEDED &&
        (idsOrNames.contains(stage.getName()) || idsOrNames.contains(stage.getId()));
    } else {
      return stage -> DEPLOY_STAGE_NAMES.contains(stage.getType()) &&
        stage.getContext().containsKey("deploy.server.groups") && stage.getStatus() == ExecutionStatus.SUCCEEDED;
    }
  }
}
