/*
 * Copyright 2020 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.echo.util;

import com.netflix.spinnaker.fiat.model.Authorization;

import java.util.List;
import java.util.Map;

public class ManualJudgmentAuthzGroupsUtil {

  public static boolean checkAuthorizedGroups(List<String> userRoles, List<String> stageRoles,
                                              Map<String, Object> permissions) {

    boolean isAuthorizedGroup = false;
    if (stageRoles == null || stageRoles.isEmpty()) {
      return true;
    }
    for (String role : userRoles) {
      if (stageRoles.contains(role)) {
        for (Map.Entry<String, Object> entry : permissions.entrySet()) {
          if (Authorization.CREATE.name().equals(entry.getKey()) ||
              Authorization.EXECUTE.name().equals(entry.getKey()) ||
              Authorization.WRITE.name().equals(entry.getKey())) {
            if (entry.getValue() != null && ((List<String>) entry.getValue()).contains(role)) {
              return true;
            }
          } else if (Authorization.READ.name().equals(entry.getKey())) {
            if (entry.getValue() != null && ((List<String>) entry.getValue()).contains(role)) {
              isAuthorizedGroup = false;
            }
          }
        }

      }
    }
    return isAuthorizedGroup;
  }
}
