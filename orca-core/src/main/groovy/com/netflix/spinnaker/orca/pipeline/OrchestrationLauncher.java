/*
 * Copyright 2017 Netflix, Inc.
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
 */

package com.netflix.spinnaker.orca.pipeline;

import java.io.IOException;
import java.io.Serializable;
import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.pipeline.model.Execution.AuthenticationDetails;
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionEngine;
import com.netflix.spinnaker.orca.pipeline.model.Orchestration;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionEngine.v2;
import static java.lang.String.format;

@Component
public class OrchestrationLauncher extends ExecutionLauncher<Orchestration> {

  private final Clock clock;
  private final ExecutionEngine executionEngine;
  private final Pool<Jedis> pool;

  @Autowired
  public OrchestrationLauncher(
    ObjectMapper objectMapper,
    String currentInstanceId,
    ExecutionRepository executionRepository,
    Collection<ExecutionRunner> runners,
    Clock clock,
    @Qualifier("jedisPool") Pool<Jedis> pool,
    @Value("${orchestration.executionEngine:v3}")
      ExecutionEngine executionEngine) {
    super(objectMapper, currentInstanceId, executionRepository, runners);
    this.clock = clock;
    this.executionEngine = executionEngine;
    this.pool = pool;
  }

  @Override
  protected Orchestration parse(String configJson) throws IOException {
    @SuppressWarnings("unchecked")
    Map<String, Serializable> config = objectMapper.readValue(configJson, Map.class);
    Orchestration orchestration = new Orchestration();
    if (config.containsKey("application")) {
      orchestration.setApplication(getString(config, "application"));
    }
    if (config.containsKey("name")) {
      orchestration.setDescription(getString(config, "name"));
    }
    if (config.containsKey("description")) {
      orchestration.setDescription(getString(config, "description"));
      orchestration.setDescription(getString(config, "description"));
    }
    if (config.containsKey("appConfig")) {
      orchestration.getAppConfig().putAll(getMap(config, "appConfig"));
    }
    orchestration.setExecutionEngine(executionEngineForApp(orchestration.getApplication()));

    for (Map<String, Object> context : getList(config, "stages")) {
      String type = context.remove("type").toString();

      String providerType = getString(context, "providerType");
      if (providerType != null && !providerType.equals("aws") && !providerType.equals("titus")) {
        type += format("_%s", providerType);
      }

      // TODO: need to check it's valid?
      Stage<Orchestration> stage = new Stage<>(orchestration, type, context);
      orchestration.getStages().add(stage);
    }

    orchestration.setBuildTime(clock.millis());
    orchestration.setAuthentication(AuthenticationDetails.build().orElse(new AuthenticationDetails()));
    orchestration.setExecutingInstance(currentInstanceId);

    return orchestration;
  }

  private ExecutionEngine executionEngineForApp(String application) {
    try (Jedis redis = pool.getResource()) {
      return redis.sismember("orchestration.executionEngine.v2", application) ? v2 : executionEngine;
    }
  }

  @Override protected void persistExecution(Orchestration execution) {
    executionRepository.store(execution);
  }
}
