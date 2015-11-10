package com.netflix.spinnaker.orca.kato.pipeline

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.kato.tasks.UpsertAmazonLoadBalancerForceRefreshTask
import com.netflix.spinnaker.orca.kato.tasks.UpsertAmazonLoadBalancerResultObjectExtrapolationTask
import com.netflix.spinnaker.orca.kato.tasks.UpsertAmazonLoadBalancerTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

/**
 * @deprecated New code should invoke the TYPE found in {@link UpsertAmazonLoadBalancerStage}
 */
@Component
@CompileStatic
@Deprecated
class UpsertLegacyAmazonLoadBalancerStage extends LinearStage {

  public static final String PIPELINE_CONFIG_TYPE = "upsertAmazonLoadBalancer"

  UpsertLegacyAmazonLoadBalancerStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    def step1 = buildStep(stage, "upsertAmazonLoadBalancer", UpsertAmazonLoadBalancerTask)
    def step2 = buildStep(stage, "monitorUpsert", MonitorKatoTask)
    def step3 = buildStep(stage, "extrapolateUpsertResult", UpsertAmazonLoadBalancerResultObjectExtrapolationTask)
    def step4 = buildStep(stage, "forceCacheRefresh", UpsertAmazonLoadBalancerForceRefreshTask)
    [step1, step2, step3, step4]
  }
}
