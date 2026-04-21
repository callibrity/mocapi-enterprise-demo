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
package com.callibrity.mocapi.demo.infra;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.ansi.AnsiStyle;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Generates ephemeral 32-byte AES keys for any encryption property the app needs but the operator
 * hasn't configured. Bound as an {@link EnvironmentPostProcessor} via {@code
 * META-INF/spring.factories} so generated values are in place before mocapi's and substrate's
 * auto-config bind their properties.
 *
 * <p>Two independent properties are covered:
 *
 * <ul>
 *   <li>{@code mocapi.session-encryption-master-key} — Mocapi encrypts the SSE {@code
 *       streamName:eventId} pair into the opaque {@code Last-Event-Id} token clients present on
 *       {@code GET /mcp} to resume an interrupted stream.
 *   <li>{@code substrate.crypto.shared-key} — Substrate's {@code AesGcmPayloadTransformer} uses
 *       this to encrypt atom / mailbox / journal payloads at rest in Postgres.
 * </ul>
 *
 * <p>Ephemeral keys are fine for local dev (they rotate on every restart, invalidating any
 * outstanding sessions or resume tokens). For any non-dev deployment, provide stable values via
 * {@code MOCAPI_SESSION_ENCRYPTION_MASTER_KEY} and {@code SUBSTRATE_CRYPTO_SHARED_KEY}.
 */
public class EncryptionBootstrap implements EnvironmentPostProcessor {

  private static final DeferredLog log = new DeferredLog();
  private static final String SSE_KEY_PROPERTY = "mocapi.session-encryption-master-key";
  private static final String AT_REST_KEY_PROPERTY = "substrate.crypto.shared-key";
  private static final String SOURCE_NAME = "mocapiDemoEphemeralEncryptionKeys";
  private static final int KEY_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication app) {
    Map<String, Object> generated = new LinkedHashMap<>();
    addIfMissing(environment, generated, SSE_KEY_PROPERTY);
    addIfMissing(environment, generated, AT_REST_KEY_PROPERTY);
    if (generated.isEmpty()) {
      return;
    }
    environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, generated));
    log.warn(
        AnsiOutput.toString(
            AnsiStyle.BOLD,
            AnsiColor.RED,
            "Ephemeral encryption key(s) generated for "
                + generated.keySet()
                + " — sessions and SSE resume tokens will not survive a restart!!!"));
    app.addListeners(new ReplayOnApplicationPrepared());
  }

  /**
   * Named class — not a lambda — so Spring can resolve the {@code ApplicationPreparedEvent} type
   * argument at registration and pre-filter invocations. A lambda here erases to {@code
   * ApplicationListener<?>} and would be invoked for every application event; the synthetic bridge
   * method's cast then throws {@link ClassCastException} on anything that isn't an {@code
   * ApplicationPreparedEvent}. Spring's multicaster catches and logs it silently — but the
   * per-event exception-plus-stack-capture cost is measurable under load (~280 CCEs/sec during a
   * 50-VU benchmark, visible in JFR as a hotspot under Spring Security's {@code
   * publishAuthenticationSuccess}).
   */
  private static final class ReplayOnApplicationPrepared
      implements ApplicationListener<ApplicationPreparedEvent> {
    @Override
    public void onApplicationEvent(ApplicationPreparedEvent event) {
      log.replayTo(EncryptionBootstrap.class);
    }
  }

  private static void addIfMissing(
      ConfigurableEnvironment environment, Map<String, Object> generated, String property) {
    if (environment.containsProperty(property)) {
      return;
    }
    byte[] key = new byte[KEY_BYTES];
    RANDOM.nextBytes(key);
    generated.put(property, Base64.getEncoder().encodeToString(key));
  }
}
