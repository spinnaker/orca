package com.netflix.spinnaker.orca.clouddriver;

import retrofit.http.GET;
import retrofit.http.Path;

import java.util.Collection;
import java.util.Map;

public interface CloudDriverCacheStatusService {
  @GET("/cache/{cloudProvider}/{type}")
  Collection<Map> pendingForceCacheUpdates(
    @Path("cloudProvider") String cloudProvider,
    @Path("type") String type
  );
}
