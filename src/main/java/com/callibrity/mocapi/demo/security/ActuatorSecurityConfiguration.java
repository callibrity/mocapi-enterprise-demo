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
package com.callibrity.mocapi.demo.security;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permits unauthenticated access to actuator endpoints. Without this, Spring Security's default
 * filter chain requires a bearer token on every path not claimed by mocapi-oauth2's {@code /mcp/**}
 * + metadata chain — including the Kubernetes/Azure liveness and readiness probes at {@code
 * /actuator/health/**}. Probes 401 under that default, the orchestrator marks the container
 * unhealthy, and the revision crash-loops.
 *
 * <p>Uses {@link EndpointRequest#toAnyEndpoint()} so the matcher moves with {@code
 * management.endpoints.web.base-path} if that ever changes. Ordered ahead of mocapi-oauth2's chain
 * so the actuator security matcher wins for actuator paths.
 */
@Configuration(proxyBeanMethods = false)
public class ActuatorSecurityConfiguration {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
    return http.securityMatcher(EndpointRequest.toAnyEndpoint())
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .csrf(CsrfConfigurer::disable)
        .build();
  }
}
