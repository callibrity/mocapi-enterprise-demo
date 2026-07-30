# Changelog

All notable changes to this project are documented in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Breaking changes

- **The server now speaks MCP revision `2026-07-28` exclusively, via Mocapi 1.0.0.**
  Clients built for `2025-11-25` will not work — this is a protocol break, not a
  negotiation. What changed on the wire:

  - **No handshake.** `initialize` and `notifications/initialized` are gone. Call tools
    directly; use `server/discover` if you want capabilities first.
  - **No sessions.** `Mcp-Session-Id` is gone in both directions. Every request carries
    its own `protocolVersion` and `clientCapabilities` in a `_meta` envelope.
  - **POST only.** `GET /mcp` and `DELETE /mcp` return `405`. The standalone SSE stream
    and `Last-Event-ID` resumption no longer exist; SSE is per-request only.
  - **Routing headers are mandatory.** `MCP-Protocol-Version` and `Mcp-Method` on every
    call, plus `Mcp-Name` on `tools/call` and `prompts/get`. They must agree with the
    body or the server answers `-32020 HeaderMismatch`.
  - **Clients must accept both** `application/json` and `text/event-stream`, or the
    request is rejected with `406`.

  Migration: drop the handshake, drop session handling, add the three headers, and move
  protocol metadata into `params._meta`. The demo's own Java needed no changes — it uses
  only `@McpTool`, `@RequiresScope`, and two filter-chain customizers, all unchanged.

- **`MOCAPI_SESSION_ENCRYPTION_MASTER_KEY` and `SUBSTRATE_CRYPTO_SHARED_KEY` are gone.**
  Neither property exists any more. Deployments setting them should remove them; set
  `MOCAPI_MRTR_SECRET` instead if you add interactive tools and run more than one replica.

- **Local observability endpoints moved.** Jaeger's UI on `:16686` is replaced by Grafana
  on `:3000`. OTLP ingest stays on `:4318`.

### Changed

- **Local observability moved from Jaeger to `grafana/otel-lgtm`.** Jaeger ingests traces
  only, so the metrics and logs this app has always exported were 404ing — metrics every
  60 seconds, with a stack trace. The Grafana image bundles an OTel Collector with Tempo,
  Prometheus, Loki, and Grafana and accepts all three signals on the same `:4318`, so no
  application configuration changed. Grafana UI is at `:3000`; `:16686` is gone.
- **`DefaultCatalogService` is `@Transactional(readOnly = true)`.** Each repository call
  previously checked out its own connection — five per `blast-radius` traversal. Now one.

### Added

- **`draft-maintenance-comms`**, an interactive tool demonstrating MCP's multi round-trip
  replay (MRTR) elicitation: it asks for a maintenance window and whether to include
  unowned services, then renders a per-team notification list. Shows the idempotency
  contract — everything before the final `elicit(...)` re-runs on every round trip.
- **`scripts/run-native.sh`** — builds and runs the native image in one command via
  buildpacks, requiring Docker but no local GraalVM, using the same `spring-boot:build-image`
  invocation as the release workflow.
- **`scripts/verify-elicitation.sh`** — drives a full three-leg elicitation round trip and
  asserts the answers survive replay. Runs against the JVM app or the native container.
- README: a "start here" routing table, a "write your first tool" walkthrough, and a
  troubleshooting section.

### Removed

- **Substrate** (`substrate-postgresql`, `substrate-crypto`). It existed solely to persist
  Mocapi session state, which the stateless protocol eliminated.
- **`EncryptionBootstrap`** and its `spring.factories` registration. Both properties it
  generated are gone: `mocapi.session-encryption-master-key` no longer exists, and
  `substrate.crypto.shared-key` left with Substrate. Mocapi generates its own ephemeral
  MRTR key when `mocapi.mrtr.secret` is unset.

### Fixed

- **OpenTelemetry version skew that broke every error path.** The logback appender was
  pinned to `2.26.1-alpha`, which calls the stable `LogRecordBuilder.setException()` added
  in otel-java 1.60.1, while Spring Boot's BOM wins import ordering and pins
  `opentelemetry-api` to 1.55.0. Every logged exception threw `NoSuchMethodError` from
  inside the logging call, stranding the request until the async timeout and returning an
  HTML 503 instead of a JSON-RPC error. Now `2.21.0-alpha`, which routes through the
  guarded incubator interface present in 1.55.0. This predated the upgrade.
- Stale Keycloak client credentials in the OAuth2 configuration comment — the realm ships
  `meridian-viewer` / `meridian-oncall` / `meridian-owner`, not `mcp-demo-client`.

## [1.0.2]

### Fixed

- `deploy-staging` passes `--container-name` when rolling the Azure Container Apps revision;
  the pod runs an OpenTelemetry sidecar, so the target container has to be named explicitly.

### Documentation

- Recorded why CSRF is disabled on the actuator chain, and pinned `azure/login` to a commit SHA.

## [1.0.1]

### Added

- `deploy-staging` job: rolls the Azure Container Apps revision to the newly published image
  on release.
- "Seeing it in action" section with two Claude transcript screenshots.

### Changed

- Tightened runtime behavior found during load testing.
- Aligned the README title and the pom project name on "Mocapi Enterprise Demo".

## [1.0.0]

Initial release of `mocapi-demo`, the enterprise-grade MCP server reference implementation. See [README.md](README.md) for the full pillar story.

[Unreleased]: https://github.com/callibrity/mocapi-enterprise-demo/compare/1.0.2...HEAD
[1.0.2]: https://github.com/callibrity/mocapi-enterprise-demo/releases/tag/1.0.2
[1.0.1]: https://github.com/callibrity/mocapi-enterprise-demo/releases/tag/1.0.1
[1.0.0]: https://github.com/callibrity/mocapi-enterprise-demo/releases/tag/1.0.0
