/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver;

import com.netflix.spinnaker.orca.clouddriver.config.SelectableService;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DelegatingMortService
  extends DelegatingClouddriverService<MortService>
  implements MortService {

  public DelegatingMortService(SelectableService selectableService) {
    super(selectableService);
  }

  @Override
  public SecurityGroup getSecurityGroup(String account, String type, String securityGroupName, String region) {
    return getService().getSecurityGroup(account, type, securityGroupName, region);
  }

  @Override
  public SecurityGroup getSecurityGroup(String account, String type, String securityGroupName, String region, String vpcId) {
    return getService().getSecurityGroup(account, type, securityGroupName, region, vpcId);
  }

  @Override
  public Collection<VPC> getVPCs() {
    return getService().getVPCs();
  }

  @Override
  public List<SearchResult> getSearchResults(String searchTerm, String type) {
    return getService().getSearchResults(searchTerm, type);
  }

  @Override
  public AccountDetails getAccountDetails(String account) {
    return getService().getAccountDetails(account);
  }

  @Override
  public List<AccountDetails> getAllAccountDetails() {
    return getService().getAllAccountDetails();
  }
}
