/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline.model;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import com.google.common.collect.ForwardingMap;

public class StageContext extends ForwardingMap<String, Object> {

  private final Stage<?> stage;
  private final Map<String, Object> delegate = new HashMap<>();

  public StageContext(Stage<?> stage) {
    this.stage = stage;
  }

  public StageContext(Stage<?> stage, Map<String, Object> content) {
    this.stage = stage;
    delegate.putAll(content);
  }

  @Override protected Map<String, Object> delegate() {
    return delegate;
  }

  @Override public Object get(@Nullable Object key) {
    if (delegate().containsKey(key)) {
      return super.get(key);
    } else {
      return stage
        .ancestors()
        .stream()
        .filter(it -> it.getContext().containsKey(key))
        .findFirst()
        .map(it -> it.getContext().get(key))
        .orElse(null);
    }
  }
}
