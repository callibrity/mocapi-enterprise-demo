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

import com.callibrity.mocapi.demo.catalog.domain.Dependency;
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
import com.callibrity.mocapi.demo.catalog.dto.TeamSummaryDto;
import com.callibrity.mocapi.demo.catalog.repository.DependencyRepository;
import com.callibrity.mocapi.demo.catalog.repository.ServiceRepository;
import com.callibrity.mocapi.demo.catalog.repository.TeamRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.jwcarman.jpa.pagination.PageDto;
import org.jwcarman.jpa.spring.page.Pages;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultCatalogService implements CatalogService {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final ServiceRepository serviceRepo;
  private final TeamRepository teamRepo;
  private final DependencyRepository dependencyRepo;

  @Override
  public ServiceDto lookupService(String name) {
    Service service = requireService(name);
    int directDeps = dependencyRepo.findAllByFromService(service).size();
    int directDependents = dependencyRepo.findAllByToService(service).size();
    return toServiceDto(service, directDeps, directDependents);
  }

  @Override
  public TeamDto lookupTeam(String name) {
    Team team =
        teamRepo
            .findByName(name)
            .orElseThrow(() -> new IllegalArgumentException("Unknown team: " + name));
    long serviceCount = serviceRepo.findAllByOwnerName(team.getName()).size();
    return new TeamDto(
        team.getName(),
        team.getDisplayName(),
        team.getOnCallRotation(),
        team.getSlackChannel(),
        serviceCount);
  }

  @Override
  public PageDto<ServiceSummaryDto> listServices(
      String domain, String tag, LifecycleStage lifecycle, Integer pageIndex, Integer pageSize) {
    Pageable pageable = pageable(pageIndex, pageSize, Sort.by("name"));
    return Pages.pageDtoOf(
        serviceRepo
            .search(blankToNull(domain), lifecycle, blankToNull(tag), pageable)
            .map(this::toServiceSummary));
  }

  @Override
  public PageDto<TeamSummaryDto> listTeams(Integer pageIndex, Integer pageSize) {
    Pageable pageable = pageable(pageIndex, pageSize, Sort.by("name"));
    return Pages.pageDtoOf(teamRepo.findAll(pageable).map(this::toTeamSummary));
  }

  @Override
  public RelatedServicesDto serviceDependencies(String name, boolean transitive) {
    Service start = requireService(name);
    List<ServiceSummaryDto> services =
        traverse(start, transitive, dependencyRepo::findAllByFromService, Dependency::getToService)
            .stream()
            .map(this::toServiceSummary)
            .toList();
    return new RelatedServicesDto(start.getName(), transitive, services);
  }

  @Override
  public RelatedServicesDto serviceDependents(String name, boolean transitive) {
    Service start = requireService(name);
    List<ServiceSummaryDto> services =
        traverse(start, transitive, dependencyRepo::findAllByToService, Dependency::getFromService)
            .stream()
            .map(this::toServiceSummary)
            .toList();
    return new RelatedServicesDto(start.getName(), transitive, services);
  }

  @Override
  public BlastRadiusDto blastRadius(String name) {
    Service target = requireService(name);
    List<Service> impacted =
        traverse(target, true, dependencyRepo::findAllByToService, Dependency::getFromService);

    int orphans = 0;
    List<ImpactedServiceDetail> details = new ArrayList<>(impacted.size());
    for (Service s : impacted) {
      Team owner = s.getOwner();
      if (owner == null) {
        orphans++;
      }
      details.add(
          new ImpactedServiceDetail(
              s.getName(),
              s.getDisplayName(),
              s.getDomain(),
              s.getLifecycleStage(),
              owner == null ? null : owner.getName(),
              owner == null ? null : owner.getOnCallRotation(),
              owner == null ? null : owner.getSlackChannel(),
              s.getTags()));
    }

    return new BlastRadiusDto(target.getName(), details, orphans);
  }

  @Override
  public PageDto<ServiceSummaryDto> orphanedServices(Integer pageIndex, Integer pageSize) {
    Pageable pageable = pageable(pageIndex, pageSize, Sort.by("name"));
    return Pages.pageDtoOf(serviceRepo.findAllByOwnerIsNull(pageable).map(this::toServiceSummary));
  }

  @Override
  public PageDto<DeprecatedUsageDto> deprecatedInUse(Integer pageIndex, Integer pageSize) {
    Pageable pageable = pageable(pageIndex, pageSize, Sort.by("name"));
    return Pages.pageDtoOf(
        serviceRepo
            .findDeprecatedInUse(pageable)
            .map(
                svc -> {
                  List<ServiceSummaryDto> callers =
                      dependencyRepo.findAllByToService(svc).stream()
                          .map(Dependency::getFromService)
                          .map(this::toServiceSummary)
                          .toList();
                  return new DeprecatedUsageDto(toServiceSummary(svc), callers);
                }));
  }

  private Service requireService(String name) {
    return serviceRepo
        .findByName(name)
        .orElseThrow(() -> new IllegalArgumentException("Unknown service: " + name));
  }

  private List<Service> traverse(
      Service start,
      boolean transitive,
      Function<Service, List<Dependency>> edges,
      Function<Dependency, Service> next) {
    if (!transitive) {
      return edges.apply(start).stream().map(next).toList();
    }
    Set<Service> visited = new LinkedHashSet<>();
    Deque<Service> queue = new ArrayDeque<>();
    queue.add(start);
    visited.add(start);
    List<Service> result = new ArrayList<>();
    while (!queue.isEmpty()) {
      Service current = queue.poll();
      for (Dependency edge : edges.apply(current)) {
        Service neighbor = next.apply(edge);
        if (visited.add(neighbor)) {
          queue.offer(neighbor);
          result.add(neighbor);
        }
      }
    }
    return result;
  }

  private ServiceSummaryDto toServiceSummary(Service s) {
    Team owner = s.getOwner();
    return new ServiceSummaryDto(
        s.getName(),
        s.getDisplayName(),
        s.getDomain(),
        owner == null ? null : owner.getName(),
        s.getLifecycleStage(),
        s.getTags());
  }

  private ServiceDto toServiceDto(Service s, int directDeps, int directDependents) {
    Team owner = s.getOwner();
    return new ServiceDto(
        s.getName(),
        s.getDisplayName(),
        s.getDescription(),
        s.getDomain(),
        owner == null ? null : toTeamSummary(owner),
        s.getLifecycleStage(),
        s.getRepoUrl(),
        s.getRunbookUrl(),
        s.getTags(),
        directDeps,
        directDependents);
  }

  private TeamSummaryDto toTeamSummary(Team t) {
    long count = serviceRepo.findAllByOwnerName(t.getName()).size();
    return new TeamSummaryDto(t.getName(), t.getDisplayName(), count);
  }

  private static Pageable pageable(Integer pageIndex, Integer pageSize, Sort sort) {
    int idx = pageIndex == null || pageIndex < 0 ? 0 : pageIndex;
    int size = pageSize == null ? DEFAULT_PAGE_SIZE : Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
    return PageRequest.of(idx, size, sort);
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
