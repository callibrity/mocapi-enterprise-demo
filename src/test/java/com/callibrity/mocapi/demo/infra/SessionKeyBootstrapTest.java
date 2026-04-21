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

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class SessionKeyBootstrapTest {

  private static final String PROPERTY = "mocapi.session-encryption-master-key";

  @Test
  void leavesPropertyAloneWhenAlreadySet() {
    MockEnvironment env = new MockEnvironment().withProperty(PROPERTY, "preset-value");

    new SessionKeyBootstrap().postProcessEnvironment(env, new SpringApplication());

    assertThat(env.getProperty(PROPERTY)).isEqualTo("preset-value");
  }

  @Test
  void generates32ByteBase64KeyWhenMissing() {
    MockEnvironment env = new MockEnvironment();

    new SessionKeyBootstrap().postProcessEnvironment(env, new SpringApplication());

    String generated = env.getProperty(PROPERTY);
    assertThat(generated).isNotBlank();
    assertThat(Base64.getDecoder().decode(generated)).hasSize(32);
  }
}
