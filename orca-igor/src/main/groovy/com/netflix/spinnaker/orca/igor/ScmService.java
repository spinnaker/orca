package com.netflix.spinnaker.orca.igor;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScmService {
  public List compareCommits(
      String repoType,
      String projectKey,
      String repositorySlug,
      Map<String, String> requestParams) {
    return igorService.compareCommits(repoType, projectKey, repositorySlug, requestParams);
  }

  public Map<String, Object> getDeliveryConfigManifest(
      String scmType,
      String project,
      String repository,
      String directory,
      String manifest,
      String ref) {
    return igorService.getDeliveryConfigManifest(
        scmType, project, repository, directory, manifest, ref);
  }

  private final IgorService igorService;
}
