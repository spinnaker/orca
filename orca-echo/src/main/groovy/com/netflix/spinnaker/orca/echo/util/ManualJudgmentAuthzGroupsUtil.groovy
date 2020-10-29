/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.orca.echo.util

import com.netflix.spinnaker.fiat.model.Authorization;

public class ManualJudgmentAuthzGroupsUtil {

  public static boolean checkAuthorizedGroups(def userRoles, def stageRoles,
                                              def permissions) {

    def isAuthorizedGroup = false
    if (!stageRoles) {
      return true
    }
    for (role in userRoles) {
      if (stageRoles.contains(role)) {
        for (perm in permissions) {
          def permKey = perm.getKey()
          if (Authorization.CREATE.name().equals(permKey) ||
              Authorization.EXECUTE.name().equals(permKey) ||
              Authorization.WRITE.name().equals(permKey)) {
            if (perm.getValue() && perm.getValue().contains(role)) {
              return true
            }
          } else if (Authorization.READ.name().equals(permKey)) {
            if (perm.getValue() && perm.getValue().contains(role)) {
              isAuthorizedGroup = false
            }
          }
        }
      }
    }
    return isAuthorizedGroup
  }
}
