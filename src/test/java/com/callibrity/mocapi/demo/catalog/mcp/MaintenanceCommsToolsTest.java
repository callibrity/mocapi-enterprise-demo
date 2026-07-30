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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.demo.catalog.domain.LifecycleStage;
import com.callibrity.mocapi.demo.catalog.dto.BlastRadiusDto;
import com.callibrity.mocapi.demo.catalog.dto.ImpactedServiceDetail;
import com.callibrity.mocapi.demo.catalog.dto.MaintenanceCommsDto;
import com.callibrity.mocapi.demo.catalog.service.CatalogService;
import com.callibrity.mocapi.model.ElicitAction;
import com.callibrity.mocapi.model.ElicitResult;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Covers the rendering logic and the accept/decline branches. The replay mechanics themselves
 * belong to mocapi and are exercised end-to-end by {@code scripts/verify-elicitation.sh}, which
 * drives all three round trips over HTTP against a running server.
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceCommsToolsTest {

  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

  @Mock CatalogService catalog;

  @InjectMocks MaintenanceCommsTools tools;

  @Test
  void groupsImpactedServicesByOwningTeam() {
    when(catalog.blastRadius("payment-processor")).thenReturn(radius());
    McpToolContext ctx = ctxAnswering("2026-08-05 22:00 UTC", false);

    MaintenanceCommsDto result = tools.draftMaintenanceComms("payment-processor", ctx);

    assertThat(result.service()).isEqualTo("payment-processor");
    assertThat(result.window()).isEqualTo("2026-08-05 22:00 UTC");
    assertThat(result.includedOrphans()).isFalse();
    assertThat(result.notifications())
        .containsExactly(
            "checkout (#checkout, on-call pd://checkout): cart-service, checkout-ui",
            "ledger (#ledger, on-call pd://ledger): billing-api");
    assertThat(result.unownedServices()).isEmpty();
    assertThat(result.notice()).doesNotContain("No owning team");
  }

  @Test
  void includesUnownedServicesWhenAsked() {
    when(catalog.blastRadius("payment-processor")).thenReturn(radius());
    McpToolContext ctx = ctxAnswering("tonight", true);

    MaintenanceCommsDto result = tools.draftMaintenanceComms("payment-processor", ctx);

    assertThat(result.includedOrphans()).isTrue();
    assertThat(result.unownedServices()).containsExactly("legacy-invoicing");
    assertThat(result.notice()).contains("No owning team could be found for: legacy-invoicing");
  }

  @Test
  void returnsCancelledWhenUserDeclines() {
    when(catalog.blastRadius("payment-processor")).thenReturn(radius());
    McpToolContext ctx = mock(McpToolContext.class);
    when(ctx.elicit(anyString(), any())).thenReturn(declined());

    MaintenanceCommsDto result = tools.draftMaintenanceComms("payment-processor", ctx);

    assertThat(result.notice()).isEqualTo("Maintenance notice cancelled.");
    assertThat(result.window()).isNull();
    assertThat(result.notifications()).isEmpty();
  }

  private static McpToolContext ctxAnswering(String window, boolean includeOrphans) {
    McpToolContext ctx = mock(McpToolContext.class);
    when(ctx.elicit(anyString(), any()))
        .thenReturn(accepted(NODES.objectNode().put("window", window)))
        .thenReturn(accepted(NODES.objectNode().put("include", includeOrphans)));
    return ctx;
  }

  private static ElicitResult accepted(ObjectNode content) {
    return new ElicitResult(ElicitAction.ACCEPT, content);
  }

  private static ElicitResult declined() {
    return new ElicitResult(ElicitAction.DECLINE, NODES.objectNode());
  }

  private static BlastRadiusDto radius() {
    return new BlastRadiusDto(
        "payment-processor",
        List.of(
            impacted("checkout-ui", "checkout"),
            impacted("cart-service", "checkout"),
            impacted("billing-api", "ledger"),
            impacted("legacy-invoicing", null)),
        1);
  }

  private static ImpactedServiceDetail impacted(String name, String team) {
    return new ImpactedServiceDetail(
        name,
        name,
        "checkout",
        LifecycleStage.ACTIVE,
        team,
        team == null ? null : "pd://" + team,
        team == null ? null : "#" + team,
        Set.of());
  }
}
