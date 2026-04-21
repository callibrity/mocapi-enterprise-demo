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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.cors.CorsConfiguration;

/**
 * Demo-app configuration bound under the {@code mocapi.demo} prefix. Currently holds a single
 * {@link CorsConfiguration} populated from {@code mocapi.demo.cors.*} properties and consumed by
 * {@link MocapiDemoConfig#corsConfigurationSource}.
 */
@ConfigurationProperties(prefix = "mocapi.demo")
@Data
public class MocapiDemoProperties {

  private CorsConfiguration cors = new CorsConfiguration();
}
