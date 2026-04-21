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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.demo.catalog.domain.Dependency;
import com.callibrity.mocapi.demo.catalog.domain.DependencyType;
import com.callibrity.mocapi.demo.catalog.domain.LifecycleStage;
import com.callibrity.mocapi.demo.catalog.domain.Service;
import com.callibrity.mocapi.demo.catalog.domain.Team;
import com.callibrity.mocapi.demo.catalog.dto.BlastRadiusDto;
import com.callibrity.mocapi.demo.catalog.dto.DeprecatedUsageDto;
import com.callibrity.mocapi.demo.catalog.dto.ImpactedServiceDetail;
import com.callibrity.mocapi.demo.catalog.dto.RelatedServicesDto;
import com.callibrity.mocapi.demo.catalog.dto.ServiceDto;
import com.callibrity.mocapi.demo.catalog.dto.ServiceSummaryDto;
import com.callibrity.mocapi.demo.catalog.dto.TeamDto;
import com.callibrity.mocapi.demo.catalog.repository.DependencyRepository;
import com.callibrity.mocapi.demo.catalog.repository.ServiceRepository;
import com.callibrity.mocapi.demo.catalog.repository.TeamRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.jpa.pagination.PageDto;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultCatalogServiceTest {

  @Mock ServiceRepository serviceRepo;

  @Mock TeamRepository teamRepo;

  @Mock DependencyRepository dependencyRepo;

  @InjectMocks DefaultCatalogService catalog;

  private Team platform;
  private Team identity;
  private final Map<String, Service> services = new HashMap<>();
  private final List<Dependency> edges = new ArrayList<>();

  @BeforeEach
  void setUp() {
    platform = new Team("platform", "Platform Engineering", "pd://platform", "#eng-platform");
    identity = new Team("identity", "Identity & Access", "pd://identity", "#eng-identity");

    registerService(
        "auth-service",
        "Auth Service",
        "platform",
        platform,
        LifecycleStage.ACTIVE,
        Set.of("foundation"));
    registerService(
        "accounts-api", "Accounts API", "identity", identity, LifecycleStage.ACTIVE, Set.of("pii"));
    registerService(
        "sso-broker", "SSO Broker", "identity", identity, LifecycleStage.ACTIVE, Set.of("pii"));
    registerService(
        "cart-service", "Cart Service", "checkout", null, LifecycleStage.ACTIVE, Set.of("pii"));
    registerService(
        "legacy", "Legacy Service", "platform", platform, LifecycleStage.DEPRECATED, Set.of());
    registerService(
        "legacy-caller", "Legacy Caller", "identity", identity, LifecycleStage.ACTIVE, Set.of());

    addEdge("accounts-api", "auth-service", DependencyType.CALLS);
    addEdge("sso-broker", "accounts-api", DependencyType.CALLS);
    addEdge("sso-broker", "auth-service", DependencyType.CALLS);
    addEdge("cart-service", "auth-service", DependencyType.CALLS);
    addEdge("legacy-caller", "legacy", DependencyType.CALLS);

    when(serviceRepo.findByName(any()))
        .thenAnswer(inv -> Optional.ofNullable(services.get(inv.<String>getArgument(0))));
    when(serviceRepo.findAllByOwnerName(any()))
        .thenAnswer(
            inv -> {
              String teamName = inv.getArgument(0);
              return services.values().stream()
                  .filter(s -> s.getOwner() != null && s.getOwner().getName().equals(teamName))
                  .toList();
            });
    when(dependencyRepo.findAllByFromService(any()))
        .thenAnswer(
            inv -> {
              Service from = inv.getArgument(0);
              return edges.stream().filter(d -> d.getFromService().equals(from)).toList();
            });
    when(dependencyRepo.findAllByToService(any()))
        .thenAnswer(
            inv -> {
              Service to = inv.getArgument(0);
              return edges.stream().filter(d -> d.getToService().equals(to)).toList();
            });
  }

  @Nested
  class Lookup {

    @Test
    void lookupServiceReturnsFullDto() {
      ServiceDto dto = catalog.lookupService("accounts-api");
      assertThat(dto.name()).isEqualTo("accounts-api");
      assertThat(dto.owner().name()).isEqualTo("identity");
      assertThat(dto.tags()).containsExactly("pii");
      assertThat(dto.lifecycleStage()).isEqualTo(LifecycleStage.ACTIVE);
      assertThat(dto.directDependencyCount()).isEqualTo(1);
      assertThat(dto.directDependentCount()).isEqualTo(1);
    }

    @Test
    void lookupServiceReturnsNullOwnerForOrphan() {
      ServiceDto dto = catalog.lookupService("cart-service");
      assertThat(dto.owner()).isNull();
    }

    @Test
    void lookupServiceUnknownThrows() {
      assertThatThrownBy(() -> catalog.lookupService("nope"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown service");
    }

    @Test
    void lookupTeamIncludesServiceCount() {
      when(teamRepo.findByName("identity")).thenReturn(Optional.of(identity));
      TeamDto dto = catalog.lookupTeam("identity");
      assertThat(dto.name()).isEqualTo("identity");
      assertThat(dto.onCallRotation()).isEqualTo("pd://identity");
      assertThat(dto.serviceCount()).isEqualTo(3);
    }

    @Test
    void lookupTeamUnknownThrows() {
      when(teamRepo.findByName("nope")).thenReturn(Optional.empty());
      assertThatThrownBy(() -> catalog.lookupTeam("nope"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown team");
    }
  }

  @Nested
  class Listing {

    @Test
    void listServicesPassesFiltersToRepo() {
      when(serviceRepo.search(
              eq("checkout"), eq(LifecycleStage.ACTIVE), eq("pii"), any(Pageable.class)))
          .thenReturn(pageOf(List.of(services.get("cart-service"))));
      PageDto<ServiceSummaryDto> page =
          catalog.listServices("checkout", "pii", LifecycleStage.ACTIVE, null, null);
      assertThat(page.data()).extracting(ServiceSummaryDto::name).containsExactly("cart-service");
      assertThat(page.pagination().totalElementCount()).isEqualTo(1);
    }

    @Test
    void listServicesTreatsBlankFiltersAsNull() {
      when(serviceRepo.search(eq(null), eq(null), eq(null), any(Pageable.class)))
          .thenReturn(pageOf(List.copyOf(services.values())));
      catalog.listServices("  ", "", null, null, null);
      verify(serviceRepo).search(eq(null), eq(null), eq(null), any(Pageable.class));
    }

    @Test
    void listServicesTreatsNullFiltersAsNull() {
      when(serviceRepo.search(eq(null), eq(null), eq(null), any(Pageable.class)))
          .thenReturn(pageOf(List.copyOf(services.values())));
      catalog.listServices(null, null, null, null, null);
      verify(serviceRepo).search(eq(null), eq(null), eq(null), any(Pageable.class));
    }

    @Test
    void orphanedServicesFindsServicesWithoutOwner() {
      when(serviceRepo.findAllByOwnerIsNull(any(Pageable.class)))
          .thenReturn(pageOf(List.of(services.get("cart-service"))));
      PageDto<ServiceSummaryDto> page = catalog.orphanedServices(null, null);
      assertThat(page.data()).hasSize(1);
      assertThat(page.data().getFirst().ownerTeam()).isNull();
    }

    @Test
    void deprecatedInUseIncludesCallers() {
      when(serviceRepo.findDeprecatedInUse(any(Pageable.class)))
          .thenReturn(pageOf(List.of(services.get("legacy"))));
      PageDto<DeprecatedUsageDto> page = catalog.deprecatedInUse(null, null);
      assertThat(page.data()).hasSize(1);
      DeprecatedUsageDto usage = page.data().getFirst();
      assertThat(usage.deprecatedService().name()).isEqualTo("legacy");
      assertThat(usage.callers())
          .extracting(ServiceSummaryDto::name)
          .containsExactly("legacy-caller");
    }
  }

  @Nested
  class Traversal {

    @Test
    void directDependenciesReturnsImmediateChildren() {
      RelatedServicesDto deps = catalog.serviceDependencies("sso-broker", false);
      assertThat(deps.rootService()).isEqualTo("sso-broker");
      assertThat(deps.transitive()).isFalse();
      assertThat(deps.services())
          .extracting(ServiceSummaryDto::name)
          .containsExactlyInAnyOrder("accounts-api", "auth-service");
    }

    @Test
    void transitiveDependenciesReturnsFullDownstreamTree() {
      RelatedServicesDto deps = catalog.serviceDependencies("sso-broker", true);
      assertThat(deps.transitive()).isTrue();
      assertThat(deps.services())
          .extracting(ServiceSummaryDto::name)
          .containsExactlyInAnyOrder("accounts-api", "auth-service");
    }

    @Test
    void transitiveDependentsReturnsCallerTree() {
      RelatedServicesDto callers = catalog.serviceDependents("auth-service", true);
      assertThat(callers.services())
          .extracting(ServiceSummaryDto::name)
          .containsExactlyInAnyOrder("accounts-api", "sso-broker", "cart-service");
    }

    @Test
    void directDependentsOnlyReturnsImmediateCallers() {
      RelatedServicesDto callers = catalog.serviceDependents("auth-service", false);
      assertThat(callers.services())
          .extracting(ServiceSummaryDto::name)
          .containsExactlyInAnyOrder("accounts-api", "sso-broker", "cart-service");
    }

    @Test
    void traversalHandlesCyclesWithoutInfiniteLoop() {
      addEdge("auth-service", "accounts-api", DependencyType.CALLS);
      RelatedServicesDto deps = catalog.serviceDependencies("auth-service", true);
      assertThat(deps.services())
          .extracting(ServiceSummaryDto::name)
          .containsExactly("accounts-api");
    }
  }

  @Nested
  class BlastRadius {

    @Test
    void flatImpactedRowsCarryOwnerAndOnCallInline() {
      BlastRadiusDto radius = catalog.blastRadius("auth-service");
      assertThat(radius.target()).isEqualTo("auth-service");
      assertThat(radius.impactedServices())
          .extracting(ImpactedServiceDetail::name)
          .containsExactlyInAnyOrder("accounts-api", "sso-broker", "cart-service");

      ImpactedServiceDetail accounts =
          radius.impactedServices().stream()
              .filter(d -> d.name().equals("accounts-api"))
              .findFirst()
              .orElseThrow();
      assertThat(accounts.ownerTeam()).isEqualTo("identity");
      assertThat(accounts.onCallRotation()).isEqualTo("pd://identity");
      assertThat(accounts.slackChannel()).isEqualTo("#eng-identity");
    }

    @Test
    void orphansReturnNullOwnerFieldsAndCountSeparately() {
      BlastRadiusDto radius = catalog.blastRadius("auth-service");
      assertThat(radius.orphanedImpactedCount()).isEqualTo(1);

      ImpactedServiceDetail cart =
          radius.impactedServices().stream()
              .filter(d -> d.name().equals("cart-service"))
              .findFirst()
              .orElseThrow();
      assertThat(cart.ownerTeam()).isNull();
      assertThat(cart.onCallRotation()).isNull();
      assertThat(cart.slackChannel()).isNull();
    }
  }

  @Nested
  class Pagination {

    @Test
    void negativePageIndexClampsToZero() {
      when(teamRepo.findAll(any(Pageable.class))).thenReturn(pageOf(List.of()));
      catalog.listTeams(-5, 50);
      verify(teamRepo).findAll(argThat((Pageable p) -> p.getPageNumber() == 0));
    }

    @Test
    void nullPageSizeUsesDefault() {
      when(teamRepo.findAll(any(Pageable.class))).thenReturn(pageOf(List.of()));
      catalog.listTeams(0, null);
      verify(teamRepo).findAll(argThat((Pageable p) -> p.getPageSize() == 20));
    }

    @Test
    void oversizedPageSizeClampsToMax() {
      when(teamRepo.findAll(any(Pageable.class))).thenReturn(pageOf(List.of()));
      catalog.listTeams(0, 9999);
      verify(teamRepo).findAll(argThat((Pageable p) -> p.getPageSize() == 100));
    }
  }

  // ------------------------------------------------------------------ helpers

  private void registerService(
      String name,
      String displayName,
      String domain,
      Team owner,
      LifecycleStage lifecycle,
      Set<String> tags) {
    Service s = new Service();
    s.setName(name);
    s.setDisplayName(displayName);
    s.setDescription("desc");
    s.setDomain(domain);
    s.setOwner(owner);
    s.setLifecycleStage(lifecycle);
    s.setRepoUrl("https://repo/" + name);
    s.setRunbookUrl("https://runbook/" + name);
    s.setTags(tags);
    services.put(name, s);
  }

  private void addEdge(String from, String to, DependencyType type) {
    edges.add(new Dependency(services.get(from), services.get(to), type));
  }

  private <T> Page<T> pageOf(List<T> content) {
    return new PageImpl<>(content);
  }
}
