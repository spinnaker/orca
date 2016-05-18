package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.clouddriver.pipeline.loadbalancer.UpsertLoadBalancerStage
import groovy.transform.CompileStatic
import org.springframework.stereotype.Component

/**
 * @deprecated use {@link UpsertLoadBalancerStage} instead.
 */
@Deprecated
@Component
@CompileStatic
class UpsertAmazonLoadBalancerStage extends UpsertLoadBalancerStage {
  public static final String PIPELINE_CONFIG_TYPE = "upsertAmazonLoadBalancer"

  @Override
  String getType() {
    return PIPELINE_CONFIG_TYPE
  }
}
