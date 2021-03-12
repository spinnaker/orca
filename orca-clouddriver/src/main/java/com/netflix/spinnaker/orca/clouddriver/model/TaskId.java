package com.netflix.spinnaker.orca.clouddriver.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskId implements Serializable {
  private String id;
}
