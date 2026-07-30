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
package com.callibrity.mocapi.demo;

import java.time.Instant;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Infrastructure for the MCP integration tests: a real Postgres, and a {@link JwtDecoder} that
 * turns a bare scope string into a token.
 *
 * <p><b>Postgres, not H2.</b> Liquibase owns the schema and Hibernate runs {@code
 * ddl-auto=validate} against it, so testing on a different engine would validate a schema this app
 * never runs on. {@code @ServiceConnection} points the context at the container, overriding the
 * compose-provided datasource for tests only.
 *
 * <p><b>A stub decoder, not a real IdP.</b> These tests are about the MCP endpoint's behavior, not
 * Keycloak's. Swapping the decoder lets a test say "a caller holding catalog:read" without running
 * an authorization server; token signature and issuer validation are Spring Security's job and are
 * exercised for real by the running demo. The scopes land as {@code SCOPE_*} authorities through
 * Boot's default converter, which is exactly what {@code @RequiresScope} reads.
 */
@TestConfiguration(proxyBeanMethods = false)
public class McpTestSupport {

  /** Header value every request needs; the transport rejects anything else. */
  public static final String PROTOCOL_VERSION = "2026-07-28";

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return new PostgreSQLContainer("postgres:16-alpine");
  }

  /** The three service-account personas from {@code keycloak/mocapi-demo-realm.json}. */
  public static final String VIEWER = "meridian-viewer";

  public static final String ONCALL = "meridian-oncall";
  public static final String OWNER = "meridian-owner";

  /** Scopes per persona, mirroring the realm so there is one place to update when it changes. */
  private static final Map<String, String> SCOPES =
      Map.of(
          VIEWER, "catalog:read",
          ONCALL, "catalog:read catalog:analyze",
          OWNER, "catalog:read catalog:analyze feedback:write");

  /**
   * Resolves a persona name, used as the bearer token, into that persona's scopes.
   *
   * <p>The token carries the persona rather than the scope list because a bearer token may not
   * contain spaces: RFC 6750 restricts it to a base64-ish alphabet, and Spring Security's filter
   * simply will not match a header that has one. An unrecognized token resolves to no scopes, which
   * is what gives the negative visibility assertions their meaning.
   */
  @Bean
  JwtDecoder jwtDecoder() {
    return token ->
        Jwt.withTokenValue(token)
            .header("alg", "none")
            .subject(token)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claims(
                c ->
                    c.putAll(
                        Map.of(
                            "scope",
                            SCOPES.getOrDefault(token, ""),
                            "aud",
                            "http://localhost:8080/mcp")))
            .build();
  }
}
