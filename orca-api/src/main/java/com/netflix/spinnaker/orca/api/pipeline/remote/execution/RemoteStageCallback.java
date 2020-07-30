package com.netflix.spinnaker.orca.api.pipeline.remote.execution;

import com.netflix.spinnaker.kork.annotations.Alpha;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/**
 * Supports {@link Http} and {@link Pubsub} callback options.
 */
@Alpha
@Data
@Builder
public class RemoteStageCallback {

  /** {@link Http} callback configuration. */
  @Nonnull private Http http;

  /** {@link Pubsub} callback configuration. */
  @Nonnull
  private Pubsub pubsub;

  @Data
  @Builder
  public static class Http {

    /** Http callback URL. */
    @Nonnull
    private String url;

    /** Http headers to include in callback. */
    @Nullable
    private Map<String, Object> headers;
  }

  @Data
  @Builder
  public static class Pubsub {

    /** Identifier of queue, can be ARN if using SQS. */
    @Nonnull
    private String id;

    /** Queue provider (sqs, google-pubsub, etc) */
    @Nonnull
    private String provider;

    /** Provider specific configuration necessary for queue */
    @Nullable
    private Map<String, Object> providerConfig;

    /** Queue specific headers ("attributes" if using SQS) to include in callback message. */
    @Nullable
    private Map<String, Object> headers;
  }
}
