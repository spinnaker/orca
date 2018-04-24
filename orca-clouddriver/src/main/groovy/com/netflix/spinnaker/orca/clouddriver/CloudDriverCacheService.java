package com.netflix.spinnaker.orca.clouddriver;

import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.POST;
import retrofit.http.Path;

import java.util.Map;

public interface CloudDriverCacheService {
  @POST("/cache/{cloudProvider}/{type}")
  Response forceCacheUpdate(
    @Path("cloudProvider") String cloudProvider,
    @Path("type") String type,
    @Body Map<String, ?> data
  );
}
