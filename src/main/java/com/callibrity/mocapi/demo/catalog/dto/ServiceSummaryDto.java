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

import com.callibrity.mocapi.demo.catalog.domain.LifecycleStage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

public record ServiceSummaryDto(
    @Schema(description = "Unique short name used in dependency references") String name,
    @Schema(description = "Human-readable name") String displayName,
    @Schema(description = "Business domain the service belongs to") String domain,
    @Schema(description = "Short name of the owning team, or null for orphaned services")
        String ownerTeam,
    @Schema(description = "Current lifecycle stage") LifecycleStage lifecycleStage,
    @Schema(description = "Tags such as 'pii', 'pci', 'soc2-scope', 'customer-facing'")
        Set<String> tags) {}
