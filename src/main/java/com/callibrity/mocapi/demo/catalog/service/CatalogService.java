/*
 * Copyright © 2026 Callibrity, Inc. (contactus@callibrity.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.callibrity.mocapi.demo.catalog.service;

import com.callibrity.mocapi.demo.catalog.domain.LifecycleStage;
import com.callibrity.mocapi.demo.catalog.dto.BlastRadiusDto;
import com.callibrity.mocapi.demo.catalog.dto.DeprecatedUsageDto;
import com.callibrity.mocapi.demo.catalog.dto.RelatedServicesDto;
import com.callibrity.mocapi.demo.catalog.dto.ServiceDto;
import com.callibrity.mocapi.demo.catalog.dto.ServiceSummaryDto;
import com.callibrity.mocapi.demo.catalog.dto.TeamDto;
import com.callibrity.mocapi.demo.catalog.dto.TeamSummaryDto;
import org.jwcarman.jpa.pagination.PageDto;

/**
 * Application-layer port for catalog queries. Adapter implementations — the MCP `CatalogTools` bean
 * today, a hypothetical REST controller tomorrow — call through this interface and stay
 * transport-agnostic. The service itself knows nothing about MCP, HTTP, Jackson, or Spring Data's
 * pagination types.
 */
public interface CatalogService {

  ServiceDto lookupService(String name);

  TeamDto lookupTeam(String name);

  PageDto<ServiceSummaryDto> listServices(
      String domain, String tag, LifecycleStage lifecycle, Integer pageIndex, Integer pageSize);

  PageDto<TeamSummaryDto> listTeams(Integer pageIndex, Integer pageSize);

  RelatedServicesDto serviceDependencies(String name, boolean transitive);

  RelatedServicesDto serviceDependents(String name, boolean transitive);

  BlastRadiusDto blastRadius(String name);

  PageDto<ServiceSummaryDto> orphanedServices(Integer pageIndex, Integer pageSize);

  PageDto<DeprecatedUsageDto> deprecatedInUse(Integer pageIndex, Integer pageSize);
}
