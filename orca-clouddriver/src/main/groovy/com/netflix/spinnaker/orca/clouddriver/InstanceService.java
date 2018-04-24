package com.netflix.spinnaker.orca.clouddriver;

import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.PATCH;
import retrofit.http.Path;

public interface InstanceService {
  @GET("/tasks") Response listTasks();

  @GET("/tasks/{id}") Response listTask(@Path("id") String id);

  @PATCH("/{app}/{version}")
  Response patchInstance(
    @Path("app") String app,
    @Path("version") String version,
    @Body String body
  );

  @GET("/{app}/current")
  Response getCurrentVersion(@Path("app") String app);

  @GET("/{healthCheckPath}")
  Response healthCheck(
    @Path(value = "healthCheckPath", encode = false) String healthCheckPath);

  @GET("/v1/platform/base/jars") Response getJars();
}
