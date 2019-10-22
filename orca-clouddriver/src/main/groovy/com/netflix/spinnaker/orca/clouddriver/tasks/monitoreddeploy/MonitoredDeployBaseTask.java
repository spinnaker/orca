/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.clouddriver.tasks.monitoreddeploy;

import com.google.common.io.CharStreams;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.config.DeploymentMonitorDefinition;
import com.netflix.spinnaker.config.DeploymentMonitorServiceProvider;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.MonitoredDeployStageData;
import com.netflix.spinnaker.orca.deploymentmonitor.models.DeploymentStep;
import com.netflix.spinnaker.orca.deploymentmonitor.models.EvaluateHealthResponse;
import com.netflix.spinnaker.orca.deploymentmonitor.models.StatusExplanation;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit.RetrofitError;
import retrofit.client.Header;
import retrofit.client.Response;

public class MonitoredDeployBaseTask implements RetryableTask {
  private static final int MAX_RETRY_COUNT = 3;
  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected Registry registry;
  private DeploymentMonitorServiceProvider deploymentMonitorServiceProvider;
  private final Map<EvaluateHealthResponse.NextStepDirective, String> summaryMapping =
      new HashMap<>();

  MonitoredDeployBaseTask(
      DeploymentMonitorServiceProvider deploymentMonitorServiceProvider, Registry registry) {
    this.deploymentMonitorServiceProvider = deploymentMonitorServiceProvider;
    this.registry = registry;

    // This could be in deck, but for now, I think it's valuable to have this show up in the JSON
    // for easier debugging
    summaryMapping.put(
        EvaluateHealthResponse.NextStepDirective.WAIT,
        "Waiting for deployment monitor to evaluate health of deployed instances");
    summaryMapping.put(
        EvaluateHealthResponse.NextStepDirective.ABORT,
        "Deployment monitor deemed the deployed instances unhealthy, aborting deployment");
    summaryMapping.put(
        EvaluateHealthResponse.NextStepDirective.CONTINUE,
        "Deployment monitor deemed the instances healthy, proceeding with deploy");
    summaryMapping.put(
        EvaluateHealthResponse.NextStepDirective.COMPLETE,
        "Deployment monitor deemed the instances healthy and requested to complete the deployment early");
  }

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.MINUTES.toMillis(1);
  }

  @Override
  public long getTimeout() {
    // NOTE: This is not used since we override getDynamicTimeout
    return 0;
  }

  @Override
  public long getDynamicTimeout(Stage stage) {
    DeploymentMonitorDefinition monitorDefinition = getDeploymentMonitorDefinition(stage);

    final Duration defaultTimeout = Duration.ofMinutes(60);
    long timeout;

    try {
      timeout = TimeUnit.MINUTES.toMillis(monitorDefinition.getMaxAnalysisMinutes());
    } catch (Exception e) {
      log.error(
          "Failed to compute timeout for {}, returning {} min",
          getClass().getSimpleName(),
          defaultTimeout.toMinutes(),
          e);

      timeout = defaultTimeout.toMillis();
    }

    return timeout;
  }

  @Override
  public @Nullable TaskResult onTimeout(@Nonnull Stage stage) {
    ExecutionStatus taskStatus;
    String message;
    DeploymentMonitorDefinition monitorDefinition = getDeploymentMonitorDefinition(stage);

    if (monitorDefinition.isFailOnError()) {
      message =
          "Deployment monitor failed to evaluate health in the allotted time, assuming failure because the monitor is configured to failOnError";
      taskStatus = ExecutionStatus.TERMINAL;
    } else {
      message =
          "Deployment monitor failed to evaluate health in the allotted time, proceeding anyway because the monitor is not configured to failOnError";
      taskStatus = ExecutionStatus.FAILED_CONTINUE;
    }

    return buildTaskResult(TaskResult.builder(taskStatus), message);
  }

  @Override
  public @Nonnull TaskResult execute(@Nonnull Stage stage) {
    MonitoredDeployStageData context = getStageContext(stage);
    DeploymentMonitorDefinition monitorDefinition = getDeploymentMonitorDefinition(stage);

    try {
      return executeInternal(stage, monitorDefinition);
    } catch (RetrofitError e) {
      log.warn(
          "HTTP Error encountered while talking to {}->{}, {}}",
          monitorDefinition,
          e.getUrl(),
          getRetrofitLogMessage(e.getResponse()),
          e);

      return handleError(context, e, true, monitorDefinition);
    } catch (DeploymentMonitorInvalidDataException e) {

      return handleError(context, e, false, monitorDefinition);
    } catch (Exception e) {
      log.error("Exception while executing {}, aborting deployment", getClass().getSimpleName(), e);

      // TODO(mvulfson): I don't love this
      throw e;
    }
  }

  public @Nonnull TaskResult executeInternal(
      Stage stage, DeploymentMonitorDefinition monitorDefinition) {
    throw new UnsupportedOperationException("Must implement executeInternal method");
  }

  private TaskResult handleError(
      MonitoredDeployStageData context,
      Exception e,
      boolean retryAllowed,
      DeploymentMonitorDefinition monitorDefinition) {
    registry
        .counter("deploymentMonitor.errors", "monitorId", monitorDefinition.getId())
        .increment();

    if (retryAllowed) {
      int currentRetryCount = context.getDeployMonitorHttpRetryCount();

      if (currentRetryCount < MAX_RETRY_COUNT) {
        log.warn(
            "Failed to get valid response for {} from deployment monitor {}, will retry",
            getClass().getSimpleName(),
            monitorDefinition,
            e);

        return TaskResult.builder(ExecutionStatus.RUNNING)
            .context("deployMonitorHttpRetryCount", ++currentRetryCount)
            .build();
      }
    }

    if (monitorDefinition.isFailOnError()) {
      registry
          .counter("deploymentMonitor.fatalErrors", "monitorId", monitorDefinition.getId())
          .increment();

      log.error(
          "Failed to get valid response for {} from deployment monitor {}, aborting because the monitor is marked with failOnError",
          getClass().getSimpleName(),
          monitorDefinition,
          e);

      String userMessage =
          String.format(
              "Failed to get a valid response from deployment monitor %s, aborting because this deployment monitor is configured to failOnError",
              monitorDefinition.getName());

      return buildTaskResult(TaskResult.builder(ExecutionStatus.TERMINAL), userMessage);
    }

    log.warn(
        "Failed to get valid response for {} from deployment monitor {}, ignoring failure",
        getClass().getSimpleName(),
        monitorDefinition,
        e);

    String userMessage =
        String.format(
            "Failed to get a valid response from deployment monitor %s, proceeding anyway because this deployment monitor is configured to not failOnError",
            monitorDefinition.getName());

    return buildTaskResult(TaskResult.builder(ExecutionStatus.SUCCEEDED), userMessage);
  }

  TaskResult buildTaskResult(
      TaskResult.TaskResultBuilder taskResultBuilder, EvaluateHealthResponse response) {

    String summary =
        summaryMapping.getOrDefault(
            response.getNextStep().getDirective(), "Health evaluation results are unknown");
    StatusExplanation explanation = new StatusExplanation(summary, response.getStatusReason());

    return taskResultBuilder.context("deploymentMonitorReasons", explanation).build();
  }

  private TaskResult buildTaskResult(
      TaskResult.TaskResultBuilder taskResultBuilder, String summary) {
    StatusExplanation explanation = new StatusExplanation(summary);

    return taskResultBuilder.context("deploymentMonitorReasons", explanation).build();
  }

  private DeploymentMonitorDefinition getDeploymentMonitorDefinition(Stage stage) {
    MonitoredDeployStageData context = getStageContext(stage);

    return deploymentMonitorServiceProvider.getDefinitionById(
        context.getDeploymentMonitor().getId());
  }

  private MonitoredDeployStageData getStageContext(Stage stage) {
    return stage.mapTo(MonitoredDeployStageData.class);
  }

  private String getRetrofitLogMessage(Response response) {
    if (response == null) {
      return "<NO RESPONSE>";
    }

    String body = "";
    String status = "";
    String headers = "";

    try {
      status = String.format("%d (%s)", response.getStatus(), response.getReason());
      body =
          CharStreams.toString(
              new InputStreamReader(response.getBody().in(), StandardCharsets.UTF_8));
      headers =
          response.getHeaders().stream().map(Header::toString).collect(Collectors.joining("\n"));
    } catch (Exception e) {
      log.error(
          "Failed to fully parse retrofit error while reading response from deployment monitor", e);
    }

    return String.format("status: %s\nheaders: %s\nresponse body: %s", status, headers, body);
  }

  void sanitizeAndLogResponse(
      EvaluateHealthResponse response,
      DeploymentMonitorDefinition monitorDefinition,
      String executionId) {
    if (response.getNextStep() == null) {
      log.error("Deployment monitor {}: returned null nextStep", monitorDefinition);

      DeploymentStep step = new DeploymentStep();
      step.setDirective(EvaluateHealthResponse.NextStepDirective.UNSPECIFIED);

      response.setNextStep(step);
    }

    if (response.getNextStep().getDirective() == null) {
      log.error("Deployment monitor {}: returned null nextStep.directive", monitorDefinition);

      response.getNextStep().setDirective(EvaluateHealthResponse.NextStepDirective.UNSPECIFIED);
    }

    EvaluateHealthResponse.NextStepDirective nextStepDirective =
        response.getNextStep().getDirective();

    switch (nextStepDirective) {
      case ABORT:
      case COMPLETE:
      case WAIT:
        log.warn(
            "Deployment monitor {}: {} deployment in response to {} for {}",
            monitorDefinition,
            nextStepDirective,
            this.getClass().getSimpleName(),
            executionId);
        break;

      case CONTINUE:
        log.info(
            "Deployment monitor {}: {} deployment in response to {} for {}",
            monitorDefinition,
            nextStepDirective,
            this.getClass().getSimpleName(),
            executionId);
        break;

      default:
        log.error(
            "Invalid next step directive: {} received from Deployment Monitor: {}",
            nextStepDirective,
            monitorDefinition);
        break;
    }
  }
}
