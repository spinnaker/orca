package com.netflix.spinnaker.orca.front50.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.DeliveryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit.RetrofitError;
import retrofit.client.Response;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

public class DeliveryConfigUtils {
  private Logger log = LoggerFactory.getLogger(getClass());
  private Front50Service front50Service;
  private ObjectMapper objectMapper;

  public DeliveryConfigUtils(Front50Service front50Service, ObjectMapper objectMapper) {
    this.front50Service = front50Service;
    this.objectMapper = objectMapper;
  }

  public Optional<DeliveryConfig> getDeliveryConfig(String id) {
    try {
      DeliveryConfig deliveryConfig = readResponse(front50Service.getDeliveryConfig(id));
      return deliveryConfig == null ? Optional.empty() : Optional.of(deliveryConfig);
    } catch (RetrofitError e) {
      //ignore an unknown (404) or unauthorized (403)
      if (e.getResponse() != null && Arrays.asList(404, 403).contains(e.getResponse().getStatus())) {
        return Optional.empty();
      } else {
        throw e;
      }
    }
  }

  public DeliveryConfig readResponse(Response response) {
    try {
      return objectMapper.readValue(response.getBody().in(), DeliveryConfig.class);
    } catch (IOException e) {
      log.error("Unable to deserialize delivery config, reason: ", e.getMessage());
    }
    return null;
  }

  public boolean configExists(String id) {
    return id != null && getDeliveryConfig(id).isPresent();
  }
}
