package com.netflix.spinnaker.orca.front50.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeliveryConfig {
  private String id;
  private String application;
  private Long lastModified;
  private Long createTs;
  private String lastModifiedBy;
  private List<Map<String,Object>> deliveryArtifacts;
  private List<Map<String,Object>> deliveryEnvironments;

  private Map<String,Object> details = new HashMap<>();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getApplication() {
    return application;
  }

  public void setApplication(String application) {
    this.application = application;
  }

  public Long getLastModified() {
    return lastModified;
  }

  public void setLastModified(Long lastModified) {
    this.lastModified = lastModified;
  }

  public Long getCreateTs() {
    return createTs;
  }

  public void setCreateTs(Long createTs) {
    this.createTs = createTs;
  }

  public String getLastModifiedBy() {
    return lastModifiedBy;
  }

  public void setLastModifiedBy(String lastModifiedBy) {
    this.lastModifiedBy = lastModifiedBy;
  }

  public List<Map<String, Object>> getDeliveryArtifacts() {
    return deliveryArtifacts;
  }

  public void setDeliveryArtifacts(List<Map<String, Object>> deliveryArtifacts) {
    this.deliveryArtifacts = deliveryArtifacts;
  }

  public List<Map<String, Object>> getDeliveryEnvironments() {
    return deliveryEnvironments;
  }

  public void setDeliveryEnvironments(List<Map<String, Object>> deliveryEnvironments) {
    this.deliveryEnvironments = deliveryEnvironments;
  }

  @JsonAnyGetter
  Map<String,Object> details() {
    return details;
  }

  @JsonAnySetter
  void set(String name, Object value) {
    details.put(name, value);
  }

  @Override
  public String toString() {
    return "DeliveryConfig{" +
      "id='" + id + '\'' +
      ", application='" + application + '\'' +
      ", lastModified=" + lastModified +
      ", createTs=" + createTs +
      ", lastModifiedBy='" + lastModifiedBy + '\'' +
      ", deliveryArtifacts=" + deliveryArtifacts +
      ", deliveryEnvironments=" + deliveryEnvironments +
      ", details=" + details +
      '}';
  }
}
