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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.mock.env.MockEnvironment;

class SessionKeyBootstrapTest {

  private static final String PROPERTY = "mocapi.session-encryption-master-key";

  @Test
  void leavesPropertyAloneWhenAlreadySet() {
    MockEnvironment env = new MockEnvironment().withProperty(PROPERTY, "preset-value");
    SpringApplication app = new SpringApplication();
    Set<ApplicationListener<?>> before = new HashSet<>(app.getListeners());

    new SessionKeyBootstrap().postProcessEnvironment(env, app);

    assertThat(env.getProperty(PROPERTY)).isEqualTo("preset-value");
    assertThat(app.getListeners()).containsExactlyInAnyOrderElementsOf(before);
  }

  @Test
  void generates32ByteBase64KeyWhenMissing() {
    MockEnvironment env = new MockEnvironment();
    SpringApplication app = new SpringApplication();
    Set<ApplicationListener<?>> before = new HashSet<>(app.getListeners());

    new SessionKeyBootstrap().postProcessEnvironment(env, app);

    String generated = env.getProperty(PROPERTY);
    assertThat(generated).isNotBlank();
    assertThat(Base64.getDecoder().decode(generated)).hasSize(32);

    // Fire an ApplicationPreparedEvent at the listener the bootstrap just registered,
    // so the deferred-log replay lambda executes. Filter to newly-added listeners only
    // because SpringApplication auto-loads other listeners from spring.factories that
    // would NPE on a mocked event.
    Set<ApplicationListener<?>> added = new HashSet<>(app.getListeners());
    added.removeAll(before);
    assertThat(added).hasSize(1);

    SimpleApplicationEventMulticaster multicaster = new SimpleApplicationEventMulticaster();
    added.forEach(multicaster::addApplicationListener);
    multicaster.multicastEvent(mock(ApplicationPreparedEvent.class));
  }
}
