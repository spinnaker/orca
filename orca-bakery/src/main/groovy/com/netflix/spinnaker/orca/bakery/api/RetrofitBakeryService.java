package com.netflix.spinnaker.orca.bakery.api;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.bakery.api.manifests.BakeManifestRequest;
import com.netflix.spinnaker.orca.retrofit2.NetworkingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit2.Call;
import retrofit2.HttpException;
import retrofit2.Response;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * An interface to the Bakery's REST API.
 */
@Component
public final class RetrofitBakeryService implements BakeryService {

  private final BakeryApi api;

  public @Autowired RetrofitBakeryService(BakeryApi api) {
    this.api = api;
  }

  @Override public Artifact bakeManifest(BakeManifestRequest bakeRequest) {
    return retrofit(() -> api.bakeManifest(bakeRequest));
  }

  @Override
  public BakeStatus createBake(String region, BakeRequest bake, String rebake) {
    return retrofit(() -> api.createBake(region, bake, rebake));
  }

  @Override public BakeStatus lookupStatus(String region, String statusId) {
    return retrofit(() -> api.lookupStatus(region, statusId));
  }

  @Override public Bake lookupBake(String region, String bakeId) {
    return retrofit(() -> api.lookupBake(region, bakeId));
  }

  //
  // Methods below this line are not supported by the Netflix Bakery, and are only available
  // iff bakery.roscoApisEnabled is true.
  //

  @Override
  public BaseImage getBaseImage(String cloudProvider, String imageId) {
    return retrofit(() -> api.getBaseImage(cloudProvider, imageId));
  }

  private <T> T retrofit(Supplier<Call<T>> action) {
    Call<T> call = action.get();
    try {
      Response<T> response = call.execute();
      if (response.isSuccessful()) {
        return response.body();
      } else {
        throw new HttpException(response);
      }
    } catch (IOException e) {
      throw new NetworkingException(call.request(), e);
    }
  }
}
