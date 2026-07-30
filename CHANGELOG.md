# Changelog

All notable changes to this project are documented in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- **Upgraded to Mocapi 1.0.0, which implements MCP revision `2026-07-28` exclusively.**
  The protocol is now stateless: the `initialize` handshake, sessions, `Mcp-Session-Id`,
  and the standalone `GET /mcp` SSE stream are gone. Every request carries its protocol
  version and client capabilities in a `_meta` envelope, and routing headers
  (`MCP-Protocol-Version`, `Mcp-Method`, `Mcp-Name`) must agree with the body or the
  server answers `-32020 HeaderMismatch`. `server/discover` replaces the handshake as an
  optional capability probe. The demo's Java needed no changes — it uses only `@McpTool`,
  `@RequiresScope`, and two filter-chain customizers, all unchanged in 1.0.
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

## [1.0.0]

Initial release of `mocapi-demo`, the enterprise-grade MCP server reference implementation. See [README.md](README.md) for the full pillar story.

[Unreleased]: https://github.com/callibrity/mocapi-enterprise-demo/compare/1.0.0...HEAD
[1.0.0]: https://github.com/callibrity/mocapi-enterprise-demo/releases/tag/1.0.0
