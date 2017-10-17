package com.netflix.spinnaker.orca.locks;

import com.netflix.spinnaker.orca.pipeline.AcquireLockStage;
import com.netflix.spinnaker.orca.pipeline.ReleaseLockStage;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner;
import com.netflix.spinnaker.orca.pipeline.model.Trigger;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface LockableStageSupport extends StageDefinitionBuilder {

  String LOCK_FEATURE_FLAG = "lockingEnabled";

  default boolean useLocking(Stage stage) {
    return useLocking(stage, getLockingConfiguration());
  }

  static boolean useLocking(Stage stage, LockingConfigurationProperties lockingConfiguration) {
    return lockingConfiguration.isEnabled() && Boolean.valueOf(
      stage.getExecution().getExecutionFlag(LOCK_FEATURE_FLAG, Boolean.toString(lockingConfiguration.isExecutionDefault())));
  }

  LockingConfigurationProperties getLockingConfiguration();

  List<String> getLockNames(Stage stage);

  @Override
  default List<Stage> aroundStages(Stage stage) {
    return buildAroundStages(stage, this);
  }

  static List<Stage> buildAroundStages(Stage stage, LockableStageSupport stageBuilder) {
    if (!stageBuilder.useLocking(stage)) {
      return Collections.emptyList();
    }

    return buildLockContexts(stage, stageBuilder)
      .stream()
      .flatMap(lockContext ->
        Stream.of(buildAcquireLockStage(stage, lockContext), buildReleaseLockStage(stage, lockContext)))
      .collect(Collectors.toList());
  }

  static Collection<LockContext> buildLockContexts(Stage stage, LockableStageSupport stageBuilder) {
    return stageBuilder.getLockNames(stage).stream().map(ln -> buildLockContext(stage, ln)).collect(Collectors.toList());
  }

  static LockContext buildLockContext(Stage stage, String lockName) {
    LockValues values = getLockValue(stage);
    return new LockContext(lockName, values.application, values.type, values.value, LockContext.DEFAULT_TTL, stage.getId());
  }

  static class LockValues {
    String application;
    String type;
    String value;

    public LockValues(String application, String type, String value) {
      this.application = application;
      this.type = type;
      this.value = value;
    }
  }

  static LockValues getLockValue(Stage stage) {
    Trigger trigger = stage.getExecution().getTrigger();
    if (trigger != null && "pipeline".equals(trigger.getType())) {
      Map<String, Object> parameters = trigger.getParameters();
      if (parameters != null && Boolean.TRUE.equals(parameters.getOrDefault("strategy", false))) {
        String parentPipelineId = (String) parameters.get("parentPipelineId");
        if (parentPipelineId == null) {
          throw new IllegalStateException("Found strategy but no parentPipelineId");
        }
        String parentPipelineApplication = (String) parameters.get("parentPipelineApplication");
        String parentTriggerType = Boolean.TRUE.equals(trigger.getOtherProperties().getOrDefault("isPipeline", true)) ? "pipeline" : "orchestration";
        return new LockValues(parentPipelineApplication, parentTriggerType, parentPipelineId);
      }
    }
    return new LockValues(stage.getExecution().getApplication(), stage.getExecution().getType().toString(), stage.getExecution().getId());
  }

  static Stage buildAcquireLockStage(Stage stage, LockContext lockContext) {
    return buildAcquireLockStage(stage, lockContext, SyntheticStageOwner.STAGE_BEFORE);
  }
  static Stage buildAcquireLockStage(Stage stage, LockContext lockContext, SyntheticStageOwner stageOwner) {
    return StageDefinitionBuilder.newStage(stage.getExecution(), AcquireLockStage.PIPELINE_TYPE, "Lock " + lockContext.getLockName(), Collections.singletonMap("lock", lockContext), stage, stageOwner);
  }

  static Stage buildReleaseLockStage(Stage stage, LockContext lockContext) {
    return StageDefinitionBuilder.newStage(stage.getExecution(), ReleaseLockStage.PIPELINE_TYPE, "Unlock " + lockContext.getLockName(), Collections.singletonMap("lock", lockContext), stage, SyntheticStageOwner.STAGE_AFTER);
  }

}
