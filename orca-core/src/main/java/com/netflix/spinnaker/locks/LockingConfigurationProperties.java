package com.netflix.spinnaker.orca.locks;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("locking")
public class LockingConfigurationProperties {
  private boolean learningMode = true;
  private boolean enabled = false;
  private boolean executionDefault = false;

  public boolean isLearningMode() {
    return learningMode;
  }

  public void setLearningMode(boolean learningMode) {
    this.learningMode = learningMode;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isExecutionDefault() {
    return executionDefault;
  }

  public void setExecutionDefault(boolean executionDefault) {
    this.executionDefault = executionDefault;
  }
}
