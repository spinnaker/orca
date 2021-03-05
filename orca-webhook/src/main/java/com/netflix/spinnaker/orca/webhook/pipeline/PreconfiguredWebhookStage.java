/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.webhook.pipeline;

import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties;
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties.PreconfiguredWebhook;
import com.netflix.spinnaker.orca.webhook.exception.PreconfiguredWebhookNotFoundException;
import com.netflix.spinnaker.orca.webhook.exception.PreconfiguredWebhookUnauthorizedException;
import com.netflix.spinnaker.orca.webhook.service.WebhookService;
import com.netflix.spinnaker.orca.webhook.tasks.MonitorWebhookTask;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PreconfiguredWebhookStage extends WebhookStage {

  private final boolean fiatEnabled;
  private final FiatService fiatService;
  private final WebhookService webhookService;

  @Autowired
  PreconfiguredWebhookStage(
      WebhookService webhookService,
      @Value("${services.fiat.enabled:false}") boolean fiatEnabled,
      FiatService fiatService,
      MonitorWebhookTask monitorWebhookTask) {
    super(monitorWebhookTask);

    this.webhookService = webhookService;
    this.fiatEnabled = fiatEnabled;
    this.fiatService = fiatService;
  }

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    var preconfiguredWebhook =
        webhookService.getPreconfiguredWebhooks().stream()
            .filter(webhook -> Objects.equals(stage.getType(), webhook.getType()))
            .findFirst()
            .orElseThrow(() -> new PreconfiguredWebhookNotFoundException(stage.getType()));

    var permissions = preconfiguredWebhook.getPermissions();
    if (permissions != null && !permissions.isEmpty()) {
      String user = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous");
      var userPermission = fiatService.getUserPermission(user);

      boolean isAllowed = preconfiguredWebhook.isAllowed("WRITE", userPermission.getRoles());
      if (!isAllowed) {
        throw new PreconfiguredWebhookUnauthorizedException(user, stage.getType());
      }
    }

    overrideIfNotSetInContextAndOverrideDefault(stage.getContext(), preconfiguredWebhook);
    super.taskGraph(stage, builder);
  }

  /** Mutates the context map. */
  private static void overrideIfNotSetInContextAndOverrideDefault(
      Map<String, Object> context, PreconfiguredWebhook preconfiguredWebhook) {
    WebhookProperties.ALL_FIELDS.forEach(
        it -> {
          try {
            if (context.get(it.getName()) == null || it.get(preconfiguredWebhook) != null) {
              context.put(it.getName(), it.get(preconfiguredWebhook));
            }
          } catch (IllegalAccessException e) {
            log.warn("unexpected reflection issue for field '{}'", it.getName());
          }
        });
  }
}
