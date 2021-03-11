package com.netflix.spinnaker.orca.api.pipeline.models;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Signals that the annotated element supports a specific {@link ExecutionEngine} version. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
public @interface ExecutionEngineVersion {
  ExecutionEngine value();
}
