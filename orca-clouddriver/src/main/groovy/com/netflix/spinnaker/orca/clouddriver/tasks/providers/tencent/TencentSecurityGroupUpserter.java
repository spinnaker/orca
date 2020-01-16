/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.tencent;

import com.netflix.spinnaker.orca.clouddriver.MortService;
import com.netflix.spinnaker.orca.clouddriver.MortService.SecurityGroup;
import com.netflix.spinnaker.orca.clouddriver.tasks.securitygroup.SecurityGroupUpserter;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

@Slf4j
@Component
@Data
public class TencentSecurityGroupUpserter implements SecurityGroupUpserter, CloudProviderAware {

  private final String cloudProvider = "tencent";

  @Autowired private MortService mortService;

  @Override
  public SecurityGroupUpserter.OperationContext getOperationContext(Stage stage) {
    List<Map> ops =
        new ArrayList<Map>() {
          {
            add(
                new HashMap<String, Object>() {
                  {
                    put(SecurityGroupUpserter.OPERATION, stage.getContext());
                  }
                });
          }
        };

    MortService.SecurityGroup securityGroup = new MortService.SecurityGroup();
    securityGroup.setName((String) stage.getContext().get("securityGroupName"));
    securityGroup.setRegion((String) stage.getContext().get("region"));
    securityGroup.setAccountName(getCredentials(stage));

    List<MortService.SecurityGroup> targets = new ArrayList<SecurityGroup>();
    targets.add(securityGroup);
    Map context = new HashMap();
    context.put("targets", targets);
    OperationContext operationContext = new SecurityGroupUpserter.OperationContext();
    operationContext.setOperations(ops);
    operationContext.setExtraOutput(context);
    return operationContext;
  }

  public boolean isSecurityGroupUpserted(
      MortService.SecurityGroup upsertedSecurityGroup, Stage stage) {
    log.info("Enter tencent isSecurityGroupUpserted with ${upsertedSecurityGroup.properties}");
    try {
      mortService.getSecurityGroup(
          upsertedSecurityGroup.getAccountName(),
          cloudProvider,
          upsertedSecurityGroup.getName(),
          upsertedSecurityGroup.getRegion());
      return true;
    } catch (RetrofitError e) {
      if (Optional.ofNullable(e.getResponse()).map(it -> it.getStatus()).orElse(null) != 404) {
        throw e;
      }
    }
    return false;
  }
}
