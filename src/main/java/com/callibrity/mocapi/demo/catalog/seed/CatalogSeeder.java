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
package com.callibrity.mocapi.demo.catalog.seed;

import static com.callibrity.mocapi.demo.catalog.domain.DependencyType.CALLS;
import static com.callibrity.mocapi.demo.catalog.domain.DependencyType.CONSUMES_FROM;
import static com.callibrity.mocapi.demo.catalog.domain.DependencyType.PUBLISHES_TO;
import static com.callibrity.mocapi.demo.catalog.domain.DependencyType.READS_FROM;
import static com.callibrity.mocapi.demo.catalog.domain.LifecycleStage.ACTIVE;
import static com.callibrity.mocapi.demo.catalog.domain.LifecycleStage.DEPRECATED;
import static com.callibrity.mocapi.demo.catalog.domain.LifecycleStage.RETIRING;

import com.callibrity.mocapi.demo.catalog.domain.Dependency;
import com.callibrity.mocapi.demo.catalog.domain.DependencyType;
import com.callibrity.mocapi.demo.catalog.domain.LifecycleStage;
import com.callibrity.mocapi.demo.catalog.domain.Service;
import com.callibrity.mocapi.demo.catalog.domain.Team;
import com.callibrity.mocapi.demo.catalog.repository.DependencyRepository;
import com.callibrity.mocapi.demo.catalog.repository.ServiceRepository;
import com.callibrity.mocapi.demo.catalog.repository.TeamRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the in-memory H2 with a fictitious mid-size company ("Meridian") on startup. The data is
 * intentionally messy — deprecated services still in use, a naming-drift cluster, an orphaned
 * compliance-scoped service — to make catalog queries return answers that resonate with real client
 * pain.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogSeeder implements ApplicationRunner {

  private final TeamRepository teamRepo;
  private final ServiceRepository serviceRepo;
  private final DependencyRepository dependencyRepo;

  private final Map<String, Team> teams = new HashMap<>();
  private final Map<String, Service> services = new HashMap<>();

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (serviceRepo.count() > 0) {
      log.info("Catalog already populated ({} services); skipping seed.", serviceRepo.count());
      return;
    }
    seedTeams();
    seedServices();
    seedDependencies();
    log.info(
        "Seeded catalog: {} teams, {} services, {} dependencies.",
        teams.size(),
        services.size(),
        dependencyRepo.count());
  }

  private void seedTeams() {
    team("platform", "Platform Engineering", "pagerduty://platform-oncall", "#eng-platform");
    team("identity", "Identity & Access", "pagerduty://identity-oncall", "#eng-identity");
    team("checkout", "Checkout Experience", "pagerduty://checkout-oncall", "#eng-checkout");
    team("catalog", "Product Catalog", "pagerduty://catalog-oncall", "#eng-catalog");
    team(
        "fulfillment",
        "Fulfillment & Logistics",
        "pagerduty://fulfillment-oncall",
        "#eng-fulfillment");
    team("data", "Data Platform", "pagerduty://data-oncall", "#eng-data");
    team(
        "notifications",
        "Customer Messaging",
        "pagerduty://notifications-oncall",
        "#eng-notifications");
    team(
        "integrations",
        "Partner Integrations",
        "pagerduty://integrations-oncall",
        "#eng-integrations");
  }

  private void seedServices() {
    // platform — foundational services consumed by the rest of the org
    svc(
        "auth-service",
        "Auth Service",
        "JWT issuance and validation for all first-party traffic.",
        "platform",
        "platform",
        ACTIVE,
        tags("pii", "customer-facing", "foundation"));
    svc(
        "feature-flags",
        "Feature Flags",
        "Runtime flag evaluation with percentage rollouts and per-tenant overrides.",
        "platform",
        "platform",
        ACTIVE,
        tags("foundation"));
    svc(
        "kafka-gateway",
        "Kafka Gateway",
        "Internal Kafka client with schema registry enforcement.",
        "platform",
        "platform",
        ACTIVE,
        tags("foundation"));
    svc(
        "log-aggregator",
        "Log Aggregator",
        "Ships structured logs from every service to the SIEM.",
        "platform",
        "platform",
        ACTIVE,
        tags("soc2-scope", "foundation"));
    svc(
        "secrets-manager",
        "Secrets Manager",
        "Central secrets store with audit trail.",
        "platform",
        "platform",
        ACTIVE,
        tags("soc2-scope", "foundation"));

    // identity
    svc(
        "accounts-api",
        "Accounts API",
        "Customer account CRUD including profile data.",
        "identity",
        "identity",
        ACTIVE,
        tags("pii", "customer-facing", "soc2-scope", "gdpr"));
    svc(
        "sso-broker",
        "SSO Broker",
        "OIDC/SAML broker for partner-federated logins.",
        "identity",
        "identity",
        ACTIVE,
        tags("pii", "soc2-scope"));
    svc(
        "session-store",
        "Session Store",
        "Redis-backed session persistence.",
        "identity",
        "identity",
        ACTIVE,
        tags("pii"));
    svc(
        "password-reset",
        "Password Reset",
        "Self-service password reset flow.",
        "identity",
        "identity",
        ACTIVE,
        tags("pii", "customer-facing"));

    // checkout — concentrates PII and PCI
    svc(
        "cart-service",
        "Cart Service",
        "Session-scoped shopping cart.",
        "checkout",
        "checkout",
        ACTIVE,
        tags("pii", "customer-facing"));
    svc(
        "payment-processor",
        "Payment Processor",
        "Tokenizes cards and forwards authorizations to the PSP.",
        "checkout",
        "checkout",
        ACTIVE,
        tags("pii", "pci", "soc2-scope", "customer-facing"));
    svc(
        "order-coordinator",
        "Order Coordinator",
        "Orchestrates cart -> payment -> fulfillment.",
        "checkout",
        "checkout",
        ACTIVE,
        tags("customer-facing"));
    svc(
        "tax-calculator",
        "Tax Calculator",
        "Computes sales tax per SKU and destination.",
        "checkout",
        "checkout",
        ACTIVE,
        tags());
    svc(
        "checkout-ui",
        "Checkout UI",
        "Server-side-rendered checkout pages.",
        "checkout",
        "checkout",
        ACTIVE,
        tags("customer-facing"));
    svc(
        "cart-v1",
        "Cart Service (v1)",
        "Predecessor to cart-service. Kept alive for the mobile v4 client.",
        "checkout",
        "checkout",
        DEPRECATED,
        tags("pii"));

    // catalog
    svc(
        "products-api",
        "Products API",
        "Read API for product metadata, pricing, and availability.",
        "catalog",
        "catalog",
        ACTIVE,
        tags("customer-facing"));
    svc(
        "search-indexer",
        "Search Indexer",
        "Maintains Elasticsearch indexes from product mutations.",
        "catalog",
        "catalog",
        ACTIVE,
        tags());
    svc(
        "inventory-service",
        "Inventory Service",
        "Real-time stock levels across warehouses.",
        "catalog",
        "catalog",
        ACTIVE,
        tags());
    svc(
        "catalog-admin",
        "Catalog Admin",
        "Internal tool for merchandising and price updates.",
        "catalog",
        "catalog",
        ACTIVE,
        tags("soc2-scope"));

    // fulfillment
    svc(
        "shipping-api",
        "Shipping API",
        "Orchestrates outbound shipments across carriers.",
        "fulfillment",
        "fulfillment",
        ACTIVE,
        tags("pii", "customer-facing"));
    svc(
        "label-generator",
        "Label Generator",
        "Produces carrier-compliant shipping labels.",
        "fulfillment",
        "fulfillment",
        ACTIVE,
        tags("pii"));
    svc(
        "returns-processor",
        "Returns Processor",
        "Refund and restock workflow for returned goods.",
        "fulfillment",
        "fulfillment",
        ACTIVE,
        tags("customer-facing"));
    svc(
        "carrier-integration",
        "Carrier Integration",
        "Adapter layer for FedEx, UPS, and DHL APIs.",
        "fulfillment",
        "fulfillment",
        ACTIVE,
        tags("pii"));

    // data — has the naming-drift cluster
    svc(
        "analytics-ingester",
        "Analytics Ingester",
        "Consumes events-bus and fans out to warehouse sinks.",
        "data",
        "data",
        ACTIVE,
        tags());
    svc(
        "events-bus",
        "Events Bus",
        "Kafka-backed domain event topics.",
        "data",
        "data",
        ACTIVE,
        tags("foundation"));
    svc(
        "reports-v2",
        "Reports v2",
        "The current reporting layer used by catalog-admin.",
        "data",
        "data",
        ACTIVE,
        tags());
    svc(
        "reports-v2-new",
        "Reports v2 (New)",
        "The replacement for reports-v2. Rollout was paused in Q3.",
        "data",
        "data",
        ACTIVE,
        tags());
    svc(
        "reporting-legacy",
        "Reporting (Legacy)",
        "Original reporting service. Scheduled for removal but still holds the tax summary endpoint.",
        "data",
        "data",
        DEPRECATED,
        tags("soc2-scope"));
    svc(
        "etl-orchestrator",
        "ETL Orchestrator",
        "Airflow-equivalent batch scheduler.",
        "data",
        "data",
        ACTIVE,
        tags());

    // notifications
    svc(
        "email-dispatcher",
        "Email Dispatcher",
        "Transactional email via the ESP.",
        "notifications",
        "notifications",
        ACTIVE,
        tags("pii", "customer-facing"));
    svc(
        "push-dispatcher",
        "Push Dispatcher",
        "APNs / FCM fan-out.",
        "notifications",
        "notifications",
        ACTIVE,
        tags("customer-facing"));
    svc(
        "sms-dispatcher",
        "SMS Dispatcher",
        "Transactional SMS via Twilio.",
        "notifications",
        "notifications",
        ACTIVE,
        tags("pii", "customer-facing"));
    svc(
        "template-renderer",
        "Template Renderer",
        "Mustache rendering for notification bodies.",
        "notifications",
        "notifications",
        ACTIVE,
        tags());

    // integrations
    svc(
        "partner-gateway",
        "Partner Gateway",
        "Inbound API for partner B2B integrations.",
        "integrations",
        "integrations",
        ACTIVE,
        tags("customer-facing"));
    svc(
        "webhook-relay",
        "Webhook Relay",
        "Retries and dead-letters outbound webhook deliveries.",
        "integrations",
        "integrations",
        RETIRING,
        tags());

    // orphan — no owner, PCI-scoped, still in use
    svcOrphan(
        "legacy-invoicing",
        "Legacy Invoicing",
        "Pre-Meridian invoicing system. Owner left in 2023; team was never reassigned.",
        "finance",
        DEPRECATED,
        tags("pci", "soc2-scope"));
  }

  private void seedDependencies() {
    // foundational fan-in — nearly everyone calls auth-service
    String[] authCallers = {
      "accounts-api",
      "sso-broker",
      "password-reset",
      "cart-service",
      "payment-processor",
      "order-coordinator",
      "checkout-ui",
      "products-api",
      "catalog-admin",
      "shipping-api",
      "returns-processor",
      "email-dispatcher",
      "push-dispatcher",
      "sms-dispatcher",
      "partner-gateway",
      "webhook-relay"
    };
    for (String caller : authCallers) dep(caller, "auth-service", CALLS);

    // feature-flags is consulted widely
    for (String caller :
        new String[] {
          "checkout-ui",
          "order-coordinator",
          "products-api",
          "search-indexer",
          "email-dispatcher",
          "partner-gateway",
          "catalog-admin"
        }) {
      dep(caller, "feature-flags", CALLS);
    }

    // kafka-gateway publishers
    for (String pub :
        new String[] {
          "order-coordinator",
          "payment-processor",
          "inventory-service",
          "shipping-api",
          "returns-processor",
          "accounts-api",
          "partner-gateway"
        }) {
      dep(pub, "kafka-gateway", PUBLISHES_TO);
    }

    // everyone logs
    for (String caller :
        new String[] {
          "auth-service",
          "accounts-api",
          "sso-broker",
          "payment-processor",
          "order-coordinator",
          "shipping-api",
          "partner-gateway",
          "webhook-relay"
        }) {
      dep(caller, "log-aggregator", PUBLISHES_TO);
    }

    // identity internal
    dep("accounts-api", "session-store", CALLS);
    dep("sso-broker", "accounts-api", CALLS);
    dep("password-reset", "accounts-api", CALLS);
    dep("password-reset", "email-dispatcher", CALLS);

    // checkout flow
    dep("checkout-ui", "cart-service", CALLS);
    dep("checkout-ui", "products-api", CALLS);
    dep("checkout-ui", "payment-processor", CALLS);
    dep("checkout-ui", "order-coordinator", CALLS);
    dep("order-coordinator", "cart-service", CALLS);
    dep("order-coordinator", "payment-processor", CALLS);
    dep("order-coordinator", "inventory-service", CALLS);
    dep("order-coordinator", "shipping-api", CALLS);
    dep("order-coordinator", "tax-calculator", CALLS);
    dep("order-coordinator", "email-dispatcher", CALLS);
    dep("payment-processor", "accounts-api", CALLS);
    dep("payment-processor", "tax-calculator", CALLS);
    // the uncomfortable one — a deprecated dep still being called
    dep("payment-processor", "reporting-legacy", CALLS);

    // catalog
    dep("products-api", "inventory-service", CALLS);
    dep("products-api", "search-indexer", CALLS);
    dep("catalog-admin", "products-api", CALLS);
    dep("catalog-admin", "inventory-service", CALLS);
    dep("catalog-admin", "reports-v2", CALLS);
    dep("search-indexer", "events-bus", CONSUMES_FROM);

    // fulfillment
    dep("shipping-api", "carrier-integration", CALLS);
    dep("shipping-api", "label-generator", CALLS);
    dep("returns-processor", "order-coordinator", CALLS);
    dep("returns-processor", "shipping-api", CALLS);

    // data
    dep("analytics-ingester", "events-bus", CONSUMES_FROM);
    dep("analytics-ingester", "reports-v2", CALLS);
    dep("analytics-ingester", "reports-v2-new", CALLS);
    dep("analytics-ingester", "reporting-legacy", CALLS);
    dep("etl-orchestrator", "events-bus", READS_FROM);
    dep("etl-orchestrator", "analytics-ingester", CALLS);

    // notifications
    dep("email-dispatcher", "events-bus", CONSUMES_FROM);
    dep("email-dispatcher", "template-renderer", CALLS);
    dep("push-dispatcher", "events-bus", CONSUMES_FROM);
    dep("push-dispatcher", "template-renderer", CALLS);
    dep("sms-dispatcher", "events-bus", CONSUMES_FROM);
    dep("sms-dispatcher", "template-renderer", CALLS);

    // integrations
    dep("partner-gateway", "products-api", CALLS);
    dep("partner-gateway", "inventory-service", CALLS);
    dep("partner-gateway", "accounts-api", CALLS);
    dep("webhook-relay", "events-bus", CONSUMES_FROM);

    // the deprecated-in-use case — cart-v1 still wired to the mobile adapter
    // (checkout-ui uses cart-service, but cart-v1 lingers for the mobile v4 client via
    // partner-gateway)
    dep("partner-gateway", "cart-v1", CALLS);

    // the orphan still depends on a deprecated dep — the compliance nightmare
    dep("legacy-invoicing", "cart-v1", READS_FROM);
    dep("legacy-invoicing", "accounts-api", CALLS);
    dep("legacy-invoicing", "reporting-legacy", CALLS);

    // retiring service deps
    dep("webhook-relay", "kafka-gateway", PUBLISHES_TO);
  }

  private void team(String name, String displayName, String onCall, String slack) {
    teams.put(name, teamRepo.save(new Team(name, displayName, onCall, slack)));
  }

  private void svc(
      String name,
      String displayName,
      String description,
      String domain,
      String ownerName,
      LifecycleStage lifecycle,
      Set<String> tags) {
    Team owner = teams.get(ownerName);
    if (owner == null) {
      throw new IllegalStateException("Unknown team: " + ownerName);
    }
    services.put(
        name,
        serviceRepo.save(
            new Service(
                name,
                displayName,
                description,
                domain,
                owner,
                lifecycle,
                "https://github.com/meridian/" + name,
                "https://runbooks.meridian.internal/" + name,
                tags)));
  }

  private void svcOrphan(
      String name,
      String displayName,
      String description,
      String domain,
      LifecycleStage lifecycle,
      Set<String> tags) {
    services.put(
        name,
        serviceRepo.save(
            new Service(
                name,
                displayName,
                description,
                domain,
                null,
                lifecycle,
                "https://github.com/meridian/" + name,
                "https://runbooks.meridian.internal/" + name,
                tags)));
  }

  private void dep(String from, String to, DependencyType type) {
    Service fromSvc = requireService(from);
    Service toSvc = requireService(to);
    dependencyRepo.save(new Dependency(fromSvc, toSvc, type));
  }

  private Service requireService(String name) {
    Service s = services.get(name);
    if (s == null) {
      throw new IllegalStateException("Unknown service: " + name);
    }
    return s;
  }

  private static Set<String> tags(String... values) {
    return values.length == 0 ? Set.of() : Set.of(values);
  }
}
