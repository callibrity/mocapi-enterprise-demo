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
package com.callibrity.mocapi.demo.catalog.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.demo.catalog.domain.LifecycleStage;
import com.callibrity.mocapi.demo.catalog.dto.BlastRadiusDto;
import com.callibrity.mocapi.demo.catalog.dto.DeprecatedUsageDto;
import com.callibrity.mocapi.demo.catalog.dto.RelatedServicesDto;
import com.callibrity.mocapi.demo.catalog.dto.ServiceDto;
import com.callibrity.mocapi.demo.catalog.dto.ServiceSummaryDto;
import com.callibrity.mocapi.demo.catalog.dto.TeamDto;
import com.callibrity.mocapi.demo.catalog.dto.TeamSummaryDto;
import com.callibrity.mocapi.demo.catalog.service.CatalogService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.jpa.pagination.PageDto;
import org.jwcarman.jpa.pagination.PaginationDto;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies each MCP tool method is a plain delegation onto {@link CatalogService}. Because {@code
 * CatalogTools} carries no business logic — only MCP adapter concerns — testing it reduces to
 * "given a mocked service, the right method is called with the right arguments and the return value
 * passes through unchanged." Business-logic coverage lives in {@code DefaultCatalogServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class CatalogToolsTest {

  @Mock CatalogService catalog;

  @InjectMocks CatalogTools tools;

  @Test
  void serviceLookupDelegates() {
    ServiceDto expected = stubServiceDto("auth-service");
    when(catalog.lookupService("auth-service")).thenReturn(expected);

    assertThat(tools.serviceLookup("auth-service")).isSameAs(expected);

    verify(catalog).lookupService("auth-service");
    verifyNoMoreInteractions(catalog);
  }

  @Test
  void teamLookupDelegates() {
    TeamDto expected = new TeamDto("platform", "Platform", "pd://", "#ch", 5);
    when(catalog.lookupTeam("platform")).thenReturn(expected);

    assertThat(tools.teamLookup("platform")).isSameAs(expected);

    verify(catalog).lookupTeam("platform");
  }

  @Test
  void servicesListForwardsAllFilterAndPageArgs() {
    PageDto<ServiceSummaryDto> expected = emptyPage();
    when(catalog.listServices("checkout", "pii", LifecycleStage.ACTIVE, 2, 50))
        .thenReturn(expected);

    assertThat(tools.servicesList("checkout", "pii", LifecycleStage.ACTIVE, 2, 50))
        .isSameAs(expected);

    verify(catalog).listServices("checkout", "pii", LifecycleStage.ACTIVE, 2, 50);
  }

  @Test
  void servicesListForwardsNullsForAbsentFilters() {
    when(catalog.listServices(null, null, null, null, null)).thenReturn(emptyPage());

    tools.servicesList(null, null, null, null, null);

    verify(catalog).listServices(null, null, null, null, null);
  }

  @Test
  void teamsListDelegates() {
    PageDto<TeamSummaryDto> expected = new PageDto<>(List.of(), zeroPagination());
    when(catalog.listTeams(0, 20)).thenReturn(expected);

    assertThat(tools.teamsList(0, 20)).isSameAs(expected);
  }

  @Test
  void serviceDependenciesUnboxesTransitiveFlag() {
    RelatedServicesDto expected = new RelatedServicesDto("sso-broker", true, List.of());
    when(catalog.serviceDependencies("sso-broker", true)).thenReturn(expected);

    assertThat(tools.serviceDependencies("sso-broker", Boolean.TRUE)).isSameAs(expected);

    verify(catalog).serviceDependencies("sso-broker", true);
  }

  @Test
  void serviceDependenciesDefaultsToDirectWhenTransitiveIsNull() {
    when(catalog.serviceDependencies("sso-broker", false))
        .thenReturn(new RelatedServicesDto("sso-broker", false, List.of()));

    tools.serviceDependencies("sso-broker", null);

    verify(catalog).serviceDependencies("sso-broker", false);
  }

  @Test
  void serviceDependentsDelegates() {
    RelatedServicesDto expected = new RelatedServicesDto("auth-service", false, List.of());
    when(catalog.serviceDependents("auth-service", false)).thenReturn(expected);

    assertThat(tools.serviceDependents("auth-service", Boolean.FALSE)).isSameAs(expected);
  }

  @Test
  void blastRadiusDelegates() {
    BlastRadiusDto expected = new BlastRadiusDto("auth-service", List.of(), 0);
    when(catalog.blastRadius("auth-service")).thenReturn(expected);

    assertThat(tools.blastRadius("auth-service")).isSameAs(expected);
  }

  @Test
  void orphanedServicesDelegates() {
    PageDto<ServiceSummaryDto> expected = emptyPage();
    when(catalog.orphanedServices(null, null)).thenReturn(expected);

    assertThat(tools.orphanedServices(null, null)).isSameAs(expected);
  }

  @Test
  void deprecatedInUseDelegates() {
    PageDto<DeprecatedUsageDto> expected = new PageDto<>(List.of(), zeroPagination());
    when(catalog.deprecatedInUse(null, null)).thenReturn(expected);

    assertThat(tools.deprecatedInUse(null, null)).isSameAs(expected);
  }

  // ------------------------------------------------------------------ helpers

  private static ServiceDto stubServiceDto(String name) {
    return new ServiceDto(
        name, name, "", "platform", null, LifecycleStage.ACTIVE, null, null, null, 0, 0);
  }

  private static PageDto<ServiceSummaryDto> emptyPage() {
    return new PageDto<>(List.of(), zeroPagination());
  }

  private static PaginationDto zeroPagination() {
    return new PaginationDto(0, 20, 0, 0, false, false);
  }
}
