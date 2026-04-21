# What this is

**Mocapi Enterprise Demo** is a working reference for what it takes to put a Model Context Protocol (MCP) server into production at an enterprise. The business demo is a fictitious mid-size company's service catalog — called "Meridian" — with 36 services, 8 teams, and 86 dependencies deliberately structured to look like a real engineering org, warts and all: deprecated services still in production, orphaned PII services, and the kind of "what breaks if X goes down" question that normally takes 20 minutes of tab-hopping to answer.

The point isn't the catalog. The point is what sits around it.

---

# The question behind it

Most MCP examples stop at "here's a tool that returns hardcoded data." That's fine for a tutorial. It falls apart the moment an enterprise asks:

- **Who's allowed to use this server?** (OAuth2, audience binding, scope gating)
- **Can we see what it did?** (three-pillar OTLP observability)
- **Are sessions safe?** (encrypted at rest, survive restarts)
- **Can we run it cheaply?** (GraalVM native, ~150 ms cold start)
- **Does the LLM only see tools it's authorized to call?** (scope-gated tool visibility, not runtime 403s)

This demo answers every one of those with working code.

---

# What's in the box

### AUTHORIZATION

The `/mcp` endpoint is a Spring Security OAuth2 resource server. Every request carries a JWT; issuer, audience, and signature get validated before the handler runs. Audience binding follows RFC 8707 — tokens issued for this resource can't be replayed elsewhere. Tool visibility is gated by `@RequiresScope` annotations, so a caller without the right scope doesn't see the tool in their `tools/list` response at all. The LLM's tool schema is a function of the caller's identity, not of hand-authored allow-lists.

### OBSERVABILITY

Traces, metrics, and logs leave the app via OTLP. Every tool call is a named span (e.g. `blast-radius`) nested under the HTTP request span. Every JDBC statement is its own span with `db.query.text` and `db.response.returned_rows` attributes — N+1 patterns are visible in the trace, not inferred from logs. MDC keys from `mocapi-logging` (`mcp.session`, `mcp.handler.name`, `mcp.request.id`) propagate as log attributes, so a single session's conversation is one log filter away. No OTel Java agent, no bytecode weaving, no custom annotations.

### PERSISTENCE

Sessions live in Postgres via Substrate. A pod roll doesn't lose in-flight conversations. The notifier uses Postgres `LISTEN/NOTIFY` for cross-replica fan-out. Two independent AES-256 keys handle encryption: one for Mocapi's SSE resume-token integrity, one for Substrate's at-rest payload encryption. Both bootstrap ephemerally in dev and come from Key Vault in production.

### NATIVE IMAGE

Full native build via GraalVM 25 in ~1.5 minutes. Resulting binary: ~200 MB on disk, ~100 MB resident, starts in ~150 ms. Same OAuth2 + OTel + scope guards as the JVM build — nothing strips out under native. CI pushes the image to GHCR on every tagged release.

---

# What makes this feel different

### SAME CODE, DIFFERENT TARGET

A single Maven build produces either a JVM fat-JAR or a GraalVM native image. Spring Boot + Mocapi + the whole enterprise wrapper work under either. In our benchmarks, native wins median latency (~2× faster per request) and all the resource / startup metrics; JVM wins peak throughput (~10% higher after JIT warmup). The point is you get to pick on workload, not on what the framework allows.

### SMALL CODE, LARGE SURFACE

The tool implementations are one-liners. `CatalogTools.java` is ~200 lines for 9 tools. Everything else — transport, schema, validation, auth, spans, MDC, retry-safe session storage — is auto-configuration. The story a developer reads here is "look how little code I actually wrote."

### AUDITABLE SECURITY POSTURE

Scope-gating happens at tool-list time, not just at invocation. Audience binding is spec-correct. Every token validation failure is a WWW-Authenticate response with protected-resource metadata pointing clients at the authorization server. Any MCP client that implements the spec — Claude Desktop, MCP Inspector, custom agent runtimes — discovers how to auth without custom integration code.

---

# How to read this repo

Clone it, run `mvn spring-boot:run`, and the `spring-boot-docker-compose` starter brings up Postgres, Keycloak (with a pre-imported realm), and Jaeger. Hit `/mcp` with any MCP client or the included curl Quickstart. The whole thing is a reference you can study, fork, and swap the tool implementations for your domain.

For how Mocapi itself works — tool dispatch, schema generation, MDC correlation, Observation-to-OTel bridging — read the [Mocapi docs](https://github.com/callibrity/mocapi). This repo picks up where those leave off: the enterprise wrapper you build *around* Mocapi.

---

> **The real takeaway:** an LLM with structured tool access, audience-bound tokens, scope-gated visibility, encrypted sessions, and correlated traces is a production artifact — not a demo. This repo shows it fitting in a codebase small enough to read in an afternoon.
