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
package com.callibrity.mocapi.demo.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** A drafted maintenance-window notice: who to tell, and what to tell them. */
public record MaintenanceCommsDto(
    @Schema(description = "Service going down for maintenance") String service,
    @Schema(description = "Maintenance window, as supplied by the user") String window,
    @Schema(description = "Whether unowned services were included in the notice")
        boolean includedOrphans,
    @Schema(description = "One notification line per owning team") List<String> notifications,
    @Schema(description = "Services with no owning team to notify") List<String> unownedServices,
    @Schema(description = "Ready-to-send notice body") String notice) {}
