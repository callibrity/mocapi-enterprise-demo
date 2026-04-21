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
 * Generates an ephemeral 32-byte session encryption master key when {@code
 * mocapi.session-encryption-master-key} is not configured. Bound as an {@link
 * EnvironmentPostProcessor} via {@code META-INF/spring.factories} so the value is in place before
 * mocapi's auto-config binds its properties.
 */
public class SessionKeyBootstrap implements EnvironmentPostProcessor {

  private static final DeferredLog log = new DeferredLog();
  private static final String PROPERTY = "mocapi.session-encryption-master-key";
  private static final String SOURCE_NAME = "mocapiDemoEphemeralMasterKey";
  private static final int KEY_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication app) {
    if (environment.containsProperty(PROPERTY)) {
      return;
    }
    byte[] key = new byte[KEY_BYTES];
    RANDOM.nextBytes(key);
    String encoded = Base64.getEncoder().encodeToString(key);
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource(SOURCE_NAME, Map.of(PROPERTY, encoded)));
    log.warn(
        AnsiOutput.toString(
            AnsiStyle.BOLD,
            AnsiColor.RED,
            "Session encryption master key not set, generating an ephemeral one!!!"));
    app.addListeners(
        (ApplicationListener<ApplicationPreparedEvent>)
            event -> log.replayTo(SessionKeyBootstrap.class));
  }
}
