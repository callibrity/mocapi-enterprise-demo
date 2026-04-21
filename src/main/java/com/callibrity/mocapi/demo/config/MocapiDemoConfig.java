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
package com.callibrity.mocapi.demo.config;

import com.callibrity.mocapi.oauth2.McpFilterChainCustomizer;
import com.callibrity.mocapi.oauth2.McpMetadataFilterChainCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * App-level configuration for the demo. Binds {@link MocapiDemoProperties}, wires a shared {@link
 * CorsConfigurationSource} fed from those properties, and declares the two security concerns the
 * app owns directly: a CORS-layering customizer on Mocapi's {@code /mcp/**} chain, and the actuator
 * chain (permit-all so health probes and the {@code /actuator/mcp} inventory endpoint stay
 * reachable without a token).
 */
@Configuration
@EnableConfigurationProperties(MocapiDemoProperties.class)
public class MocapiDemoConfig {

  @Bean
  public CorsConfigurationSource corsConfigurationSource(MocapiDemoProperties props) {
    return _ -> props.getCors();
  }

  @Bean
  public McpFilterChainCustomizer mcpCorsCustomizer() {
    return http -> http.cors(Customizer.withDefaults());
  }

  @Bean
  public McpMetadataFilterChainCustomizer metadataCorsCustomizer() {
    return http -> http.cors(Customizer.withDefaults());
  }

  /**
   * Actuator endpoints are intentionally unauthenticated so Kubernetes / ACA probes can hit {@code
   * /actuator/health/**} without a bearer token. The chain disables CSRF protection because:
   *
   * <ul>
   *   <li><b>No ambient credentials.</b> CSRF exploits sessions carried automatically by the
   *       browser (cookies, HTTP Basic). This chain uses neither — requests are anonymous. There is
   *       no authenticated state for an attacker to ride on, so the CSRF class of attack does not
   *       apply.
   *   <li><b>Endpoints are read-only.</b> We expose {@code health}, {@code info}, and {@code mcp}
   *       (see {@code management.endpoints.web.exposure.include}). All are GET-only and
   *       side-effect-free.
   *   <li><b>CSRF would break probes.</b> Spring Security's CSRF filter rejects non-safe HTTP
   *       methods without a token. Liveness / readiness / startup probes are issued by the platform
   *       and do not carry CSRF tokens; enabling CSRF would cause the orchestrator to mark the
   *       container unhealthy and crash-loop.
   * </ul>
   */
  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) {
    return http.securityMatcher(EndpointRequest.toAnyEndpoint())
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .cors(Customizer.withDefaults())
        .csrf(CsrfConfigurer::disable)
        .build();
  }
}
