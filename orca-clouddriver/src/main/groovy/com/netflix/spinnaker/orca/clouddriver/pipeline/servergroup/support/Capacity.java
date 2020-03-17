package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import javax.annotation.Nonnegative;
import lombok.Value;

@Value
public class Capacity {
  @Nonnegative int min;

  @Nonnegative int max;

  @Nonnegative int desired;

  /**
   * @return true if the capacity of this server group is fixed, i.e min, max and desired are all
   *     the same
   */
  public boolean isPinned() {
    return (max == desired) && (desired == min);
  }

  public Map<String, Integer> asMap() {
    return ImmutableMap.of("min", min, "max", max, "desired", desired);
  }
}
