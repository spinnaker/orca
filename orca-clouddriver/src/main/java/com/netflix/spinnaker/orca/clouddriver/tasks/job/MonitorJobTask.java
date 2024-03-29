/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 *
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.job;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.Task;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MonitorJobTask extends MonitorKatoTask {
  public static final String TASK_NAME = "monitorDeploy";
  private final JobUtils jobUtils;
  private static final Logger log = LoggerFactory.getLogger(MonitorJobTask.class);

  @Autowired
  public MonitorJobTask(
      KatoService katoService,
      Registry registry,
      JobUtils jobUtils,
      DynamicConfigService dynamicConfigService,
      RetrySupport retrySupport) {
    super(katoService, registry, dynamicConfigService, retrySupport);
    this.jobUtils = jobUtils;
  }

  public MonitorJobTask(
      KatoService katoService,
      Registry registry,
      DynamicConfigService dynamicConfigService,
      RetrySupport retrySupport) {
    super(katoService, registry, dynamicConfigService, retrySupport);
    this.jobUtils = null;
  }

  @Override
  public @Nullable TaskResult onTimeout(@Nonnull StageExecution stage) {
    jobUtils.cancelWait(stage);

    return null;
  }

  @Override
  public void onCancel(@Nonnull StageExecution stage) {
    jobUtils.cancelWait(stage);
  }

  @Override
  protected void handleClouddriverRetries(
      StageExecution stage, Task katoTask, Map<String, Object> outputs) {
    log.debug("handling clouddriver retries is not supported for Jobs");
  }
}
