/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf.manifest;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.bakery.api.BakeryService;
import com.netflix.spinnaker.orca.bakery.api.manifests.BakeManifestRequest;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Component
@Slf4j
public class CloudFoundryCreateManifestTask implements RetryableTask {

    @Nullable private final BakeryService bakery;

    private final ArtifactUtils artifactUtils;

    private final ContextParameterProcessor contextParameterProcessor;

    @Autowired
    public CloudFoundryCreateManifestTask(
            ArtifactUtils artifactUtils,
            ContextParameterProcessor contextParameterProcessor,
            Optional<BakeryService> bakery) {
        this.artifactUtils = artifactUtils;
        this.contextParameterProcessor = contextParameterProcessor;
        this.bakery = bakery.orElse(null);
    }


    @Override
    public long getBackoffPeriod() {
        return 30000;
    }

    @Override
    public long getTimeout() {
        return 300000;
    }

    @Nonnull
    @Override
    public TaskResult execute(@Nonnull StageExecution stage) {

        if (bakery == null) {
            throw new IllegalStateException(
                    "A BakeryService must be configured in order to run a Bake Manifest task.");
        }

        CloudFoundryCreateManifestContext context = stage.mapTo(CloudFoundryCreateManifestContext.class);

        if (context.getManifestTemplate() == null || context.getVarsArtifacts().isEmpty()) {
            throw new IllegalArgumentException("There must be one manifest template and at least one variables artifact supplied");
        }

        Artifact resolvedManifestTemplate =
                artifactUtils.getBoundArtifactForStage(stage, context.getManifestTemplate().getId(), context.getManifestTemplate().getArtifact());

        if(resolvedManifestTemplate != null) {
            resolvedManifestTemplate = ArtifactUtils.withAccount(resolvedManifestTemplate, context.getManifestTemplate().getAccount());
        } else {
            throw new IllegalArgumentException(
                    String.format(
                            "Input artifact (id: %s, account: %s) could not be found in execution (id: %s).",
                            context.getManifestTemplate().getId(), context.getManifestTemplate().getAccount(), stage.getExecution().getId()));
        }

        List<Artifact> resolvedVarsArtifacts =
                context.getVarsArtifacts().stream()
                        .map(
                                p -> {
                                    Artifact a =
                                            artifactUtils.getBoundArtifactForStage(stage, p.getId(), p.getArtifact());
                                    if (a == null) {
                                        throw new IllegalArgumentException(
                                                String.format(
                                                        "Input artifact (id: %s, account: %s) could not be found in execution (id: %s).",
                                                        p.getId(), p.getAccount(), stage.getExecution().getId()));
                                    }
                                    return ArtifactUtils.withAccount(a, p.getAccount());
                                })
                        .collect(Collectors.toList());

        ExpectedArtifact expectedArtifacts = context.getExpectedArtifact();

        if (expectedArtifacts == null) {
            throw new IllegalArgumentException(
                    "The CreateCloudFoundryManifest stage produces one embedded base64 artifact.  Please ensure that your stage"
                            + " config's `Produces Artifacts` section (`expectedArtifacts` field) contains exactly one artifact.");
        }

        String outputArtifactName = expectedArtifacts.getMatchArtifact().getName();

        BakeManifestRequest request = new CloudFoundryBakeManifestRequest(context, resolvedManifestTemplate, resolvedVarsArtifacts, outputArtifactName);

        Artifact result = bakery.bakeManifest(request.getTemplateRenderer(), request);

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("artifacts", Collections.singleton(result));
        outputs.put("cloudProvider", "cloudfoundry"); // Needed for stat collection.

        return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).outputs(outputs).build();
    }

    @Data
    static class InputArtifact {
        String id;
        String account;
        Artifact artifact;
    }
}
