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

  public List<String> listKeelManifests(
      String scmType, String projectKey, String repositorySlug, String at) {
    return igorService.listKeelManifests(scmType, projectKey, repositorySlug, at);
  }

  public Map<String, Object> getKeelManifest(
      String scmType, String projectKey, String repositorySlug, String manifest, String at) {
    return igorService.getKeelManifest(scmType, projectKey, repositorySlug, manifest, at);
  }

  private final IgorService igorService;
}
