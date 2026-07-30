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
import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.demo.catalog.dto.BlastRadiusDto;
import com.callibrity.mocapi.demo.catalog.dto.ImpactedServiceDetail;
import com.callibrity.mocapi.demo.catalog.dto.MaintenanceCommsDto;
import com.callibrity.mocapi.demo.catalog.service.CatalogService;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.security.spring.RequiresScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The demo's one <em>interactive</em> tool: it asks the user two questions before it can answer.
 *
 * <p>MCP 2026-07-28 elicitation is multi-round-trip replay. Each unanswered {@code elicit(...)}
 * unwinds this method and returns an {@code input_required} result to the client; the client
 * re-sends the same call with the answers attached, and this method <b>runs again from the top</b>
 * with previously-answered elicitations returning immediately. Two questions therefore mean three
 * executions.
 *
 * <p>That makes the ordering rule below the whole point of this class: <b>everything before the
 * last {@code elicit(...)} re-runs on every round trip.</b> The catalog read is a query, so
 * repeating it is harmless. A write there would happen three times.
 */
@Component
@RequiredArgsConstructor
public class MaintenanceCommsTools {

  private final CatalogService catalog;

  @RequiresScope("catalog:analyze")
  @McpTool(
      name = "draft-maintenance-comms",
      title = "Draft Maintenance Comms",
      description = "${tools.catalog.draft-maintenance-comms.description}")
  public MaintenanceCommsDto draftMaintenanceComms(
      @Schema(description = "Short name of the service going down for maintenance")
          @NotBlank
          @Size(max = 100)
          @Pattern(regexp = "^[a-z0-9-]+$")
          String name,
      McpToolContext ctx) {

    // Read-only, and deliberately so: this line runs once per round trip.
    BlastRadiusDto radius = catalog.blastRadius(name);

    ElicitResult window =
        ctx.elicit(
            "When is the maintenance window for " + name + "?",
            schema -> schema.string("window", "e.g. 2026-08-05 22:00-23:00 UTC"));
    if (!window.isAccepted()) {
      return cancelled(name);
    }

    ElicitResult orphans =
        ctx.elicit(
            "Include unowned services in the notice?",
            schema -> schema.bool("include", "Include services with no owning team"));
    if (!orphans.isAccepted()) {
      return cancelled(name);
    }

    // Past the final elicit(): runs exactly once, on the last round trip.
    return render(radius, window.getString("window"), orphans.getBool("include"));
  }

  private static MaintenanceCommsDto render(
      BlastRadiusDto radius, String window, boolean includeOrphans) {
    List<String> notifications =
        radius.impactedServices().stream()
            .filter(s -> s.ownerTeam() != null)
            .collect(Collectors.groupingBy(ImpactedServiceDetail::ownerTeam))
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> notificationLine(e.getKey(), e.getValue()))
            .toList();

    List<String> unowned =
        radius.impactedServices().stream()
            .filter(s -> s.ownerTeam() == null)
            .map(ImpactedServiceDetail::name)
            .sorted()
            .toList();

    return new MaintenanceCommsDto(
        radius.target(),
        window,
        includeOrphans,
        notifications,
        includeOrphans ? unowned : List.of(),
        notice(radius.target(), window, includeOrphans ? unowned : List.of()));
  }

  private static String notificationLine(String team, List<ImpactedServiceDetail> services) {
    ImpactedServiceDetail first = services.getFirst();
    String affected =
        services.stream()
            .map(ImpactedServiceDetail::name)
            .sorted()
            .collect(Collectors.joining(", "));
    return "%s (%s, on-call %s): %s"
        .formatted(
            team,
            Objects.toString(first.slackChannel(), "no channel"),
            Objects.toString(first.onCallRotation(), "unassigned"),
            affected);
  }

  private static String notice(String target, String window, List<String> unowned) {
    String body =
        "Heads up: %s will be unavailable during %s. Services that depend on it may degrade."
            .formatted(target, window);
    if (unowned.isEmpty()) {
      return body;
    }
    return body + " No owning team could be found for: " + String.join(", ", unowned) + ".";
  }

  private static MaintenanceCommsDto cancelled(String name) {
    return new MaintenanceCommsDto(
        name, null, false, List.of(), List.of(), "Maintenance notice cancelled.");
  }
}
