package com.netflix.spinnaker.orca.front50.tasks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.DeliveryConfig;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class UpsertDeliveryConfigTask implements RetryableTask {

  private Logger log = LoggerFactory.getLogger(getClass());

  private Front50Service front50Service;
  private ObjectMapper objectMapper;
  private DeliveryConfigUtils deliveryConfigUtils;

  @Autowired
  public UpsertDeliveryConfigTask(Front50Service front50Service, ObjectMapper objectMapper) {
    this.front50Service = front50Service;
    this.objectMapper = objectMapper;
    deliveryConfigUtils = new DeliveryConfigUtils(front50Service, objectMapper);
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    if (!stage.getContext().containsKey("delivery")) {
      throw new IllegalArgumentException("Key 'delivery' must be provided.");
    }

    //todo eb: base64 encode this if it will have expressions
    DeliveryConfig deliveryConfig = objectMapper
      .convertValue(stage.getContext().get("delivery"), new TypeReference<DeliveryConfig>(){});

    log.debug("Received delivery config: " + deliveryConfig);

    Response response;
    if (deliveryConfigUtils.configExists(deliveryConfig.getId())) {
      response = front50Service.updateDeliveryConfig(deliveryConfig.getId(), deliveryConfig);
    } else {
      response = front50Service.createDeliveryConfig(deliveryConfig);
    }

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("application", deliveryConfig.getApplication());

    DeliveryConfig savedConfig;
    try {
      savedConfig = objectMapper.readValue(response.getBody().in(), DeliveryConfig.class);
      outputs.put("deliveryConfig", savedConfig);
    } catch (IOException e) {
      log.error("Unable to deserialize saved delivery config, reason: ", e.getMessage());
    }

    return new TaskResult(
      (response.getStatus() == HttpStatus.OK.value()) ? ExecutionStatus.SUCCEEDED : ExecutionStatus.TERMINAL,
      outputs
    );
  }

  @Override
  public long getBackoffPeriod() {
    return 1000;
  }

  @Override
  public long getTimeout() {
    return TimeUnit.SECONDS.toMillis(30);
  }
}
