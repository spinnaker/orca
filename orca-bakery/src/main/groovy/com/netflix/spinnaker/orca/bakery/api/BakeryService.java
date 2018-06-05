package com.netflix.spinnaker.orca.bakery.api;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.bakery.api.manifests.BakeManifestRequest;

/**
 * An interface to the Bakery's REST API.
 */
public interface BakeryService {

  Artifact bakeManifest(BakeManifestRequest bakeRequest);

  BakeStatus createBake(String region, BakeRequest bake, String rebake);

  BakeStatus lookupStatus(String region, String statusId);

  Bake lookupBake(String region, String bakeId);

  //
  // Methods below this line are not supported by the Netflix Bakery, and are only available
  // iff bakery.roscoApisEnabled is true.
  //

  BaseImage getBaseImage(String cloudProvider, String imageId);
}
