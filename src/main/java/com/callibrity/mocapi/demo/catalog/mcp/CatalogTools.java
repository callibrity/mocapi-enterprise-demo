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

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.demo.catalog.domain.LifecycleStage;
import com.callibrity.mocapi.demo.catalog.dto.BlastRadiusDto;
import com.callibrity.mocapi.demo.catalog.dto.DeprecatedUsageDto;
import com.callibrity.mocapi.demo.catalog.dto.RelatedServicesDto;
import com.callibrity.mocapi.demo.catalog.dto.ServiceDto;
import com.callibrity.mocapi.demo.catalog.dto.ServiceSummaryDto;
import com.callibrity.mocapi.demo.catalog.dto.TeamDto;
import com.callibrity.mocapi.demo.catalog.dto.TeamSummaryDto;
import com.callibrity.mocapi.demo.catalog.service.CatalogService;
import com.callibrity.mocapi.security.spring.RequiresScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.jwcarman.jpa.pagination.PageDto;
import org.springframework.stereotype.Component;

/**
 * Thin MCP adapter over {@link CatalogService}. Each {@code @ToolMethod} here is a one-liner that
 * delegates to the service layer, so the adapter carries only MCP concerns (tool names, titles,
 * parameter schemas) and the service stays transport-agnostic. A future REST adapter would look
 * symmetrically thin over the same {@code CatalogService}.
 */
@Component
@RequiredArgsConstructor
public class CatalogTools {

  private final CatalogService catalog;

  @RequiresScope("catalog:read")
  @McpTool(
      name = "service-lookup",
      title = "Service Lookup",
      description = "${tools.catalog.service-lookup.description}")
  public ServiceDto serviceLookup(
      @Schema(description = "Short name of the service, e.g. 'payment-processor'")
          @NotBlank
          @Size(max = 100)
          @Pattern(regexp = "^[a-z0-9-]+$")
          String name) {
    return catalog.lookupService(name);
  }

  @RequiresScope("catalog:read")
  @McpTool(
      name = "team-lookup",
      title = "Team Lookup",
      description = "${tools.catalog.team-lookup.description}")
  public TeamDto teamLookup(
      @Schema(description = "Short name of the team, e.g. 'platform'")
          @NotBlank
          @Size(max = 100)
          @Pattern(regexp = "^[a-z0-9-]+$")
          String name) {
    return catalog.lookupTeam(name);
  }

  @RequiresScope("catalog:read")
  @McpTool(
      name = "services-list",
      title = "Services List",
      description = "${tools.catalog.services-list.description}")
  public PageDto<ServiceSummaryDto> servicesList(
      @Schema(
              requiredMode = Schema.RequiredMode.NOT_REQUIRED,
              description = "Filter by business domain (e.g. 'checkout'). Omit for all domains.")
          @Size(max = 100)
          String domain,
      @Schema(
              requiredMode = Schema.RequiredMode.NOT_REQUIRED,
              description = "Filter by tag (e.g. 'pii', 'pci', 'soc2-scope'). Omit for all tags.")
          @Size(max = 100)
          String tag,
      @Schema(
              requiredMode = Schema.RequiredMode.NOT_REQUIRED,
              description =
                  "Filter by lifecycle stage: ACTIVE, DEPRECATED, RETIRING. Omit for all.")
          LifecycleStage lifecycle,
      @Schema(
              requiredMode = Schema.RequiredMode.NOT_REQUIRED,
              description = "Zero-based page index. Defaults to 0.")
          @Min(0)
          Integer pageIndex,
      @Schema(
              requiredMode = Schema.RequiredMode.NOT_REQUIRED,
              description = "Page size. Defaults to 20, capped at 100.")
          @Min(1)
          @Max(100)
          Integer pageSize) {
    return catalog.listServices(domain, tag, lifecycle, pageIndex, pageSize);
  }

  @RequiresScope("catalog:read")
  @McpTool(
      name = "teams-list",
      title = "Teams List",
      description = "${tools.catalog.teams-list.description}")
  public PageDto<TeamSummaryDto> teamsList(
      @Schema(
              requiredMode = Schema.RequiredMode.NOT_REQUIRED,
              description = "Zero-based page index. Defaults to 0.")
          @Min(0)
          Integer pageIndex,
      @Schema(
              requiredMode = Schema.RequiredMode.NOT_REQUIRED,
              description = "Page size. Defaults to 20, capped at 100.")
          @Min(1)
          @Max(100)
          Integer pageSize) {
    return catalog.listTeams(pageIndex, pageSize);
  }

  @RequiresScope("catalog:analyze")
  @McpTool(
      name = "service-dependencies",
      title = "Service Dependencies",
      description = "${tools.catalog.service-dependencies.description}")
  public RelatedServicesDto serviceDependencies(
      @Schema(description = "Short name of the service")
          @NotBlank
          @Size(max = 100)
          @Pattern(regexp = "^[a-z0-9-]+$")
          String name,
      @Schema(
              requiredMode = Schema.RequiredMode.NOT_REQUIRED,
              description = "If true, follow dependency edges recursively. Defaults to false.")
          Boolean transitive) {
    return catalog.serviceDependencies(name, Boolean.TRUE.equals(transitive));
  }

  @RequiresScope("catalog:analyze")
  @McpTool(
      name = "service-dependents",
      title = "Service Dependents",
      description = "${tools.catalog.service-dependents.description}")
  public RelatedServicesDto serviceDependents(
      @Schema(description = "Short name of the service")
          @NotBlank
          @Size(max = 100)
          @Pattern(regexp = "^[a-z0-9-]+$")
          String name,
      @Schema(
              requiredMode = Schema.RequiredMode.NOT_REQUIRED,
              description = "If true, follow dependent edges recursively. Defaults to false.")
          Boolean transitive) {
    return catalog.serviceDependents(name, Boolean.TRUE.equals(transitive));
  }

  @RequiresScope("catalog:analyze")
  @McpTool(
      name = "blast-radius",
      title = "Blast Radius",
      description = "${tools.catalog.blast-radius.description}")
  public BlastRadiusDto blastRadius(
      @Schema(description = "Short name of the service whose failure is being assessed")
          @NotBlank
          @Size(max = 100)
          @Pattern(regexp = "^[a-z0-9-]+$")
          String name) {
    return catalog.blastRadius(name);
  }

  @RequiresScope("catalog:analyze")
  @McpTool(
      name = "orphaned-services",
      title = "Orphaned Services",
      description = "${tools.catalog.orphaned-services.description}")
  public PageDto<ServiceSummaryDto> orphanedServices(
      @Schema(
              requiredMode = Schema.RequiredMode.NOT_REQUIRED,
              description = "Zero-based page index. Defaults to 0.")
          @Min(0)
          Integer pageIndex,
      @Schema(
              requiredMode = Schema.RequiredMode.NOT_REQUIRED,
              description = "Page size. Defaults to 20, capped at 100.")
          @Min(1)
          @Max(100)
          Integer pageSize) {
    return catalog.orphanedServices(pageIndex, pageSize);
  }

  @RequiresScope("catalog:analyze")
  @McpTool(
      name = "deprecated-in-use",
      title = "Deprecated Services Still In Use",
      description = "${tools.catalog.deprecated-in-use.description}")
  public PageDto<DeprecatedUsageDto> deprecatedInUse(
      @Schema(
              requiredMode = Schema.RequiredMode.NOT_REQUIRED,
              description = "Zero-based page index. Defaults to 0.")
          @Min(0)
          Integer pageIndex,
      @Schema(
              requiredMode = Schema.RequiredMode.NOT_REQUIRED,
              description = "Page size. Defaults to 20, capped at 100.")
          @Min(1)
          @Max(100)
          Integer pageSize) {
    return catalog.deprecatedInUse(pageIndex, pageSize);
  }
}
