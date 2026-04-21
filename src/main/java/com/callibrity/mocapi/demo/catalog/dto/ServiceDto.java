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

public record ServiceDto(
    @Schema(description = "Unique short name") String name,
    @Schema(description = "Human-readable name") String displayName,
    @Schema(description = "Longer description of the service's purpose") String description,
    @Schema(description = "Business domain") String domain,
    @Schema(description = "Owning team summary, or null if orphaned") TeamSummaryDto owner,
    @Schema(description = "Current lifecycle stage") LifecycleStage lifecycleStage,
    @Schema(description = "Source repository URL") String repoUrl,
    @Schema(description = "Runbook URL") String runbookUrl,
    @Schema(description = "Tags such as 'pii', 'pci', 'soc2-scope', 'customer-facing'")
        Set<String> tags,
    @Schema(description = "Count of direct outbound dependencies") int directDependencyCount,
    @Schema(description = "Count of direct inbound dependents") int directDependentCount) {}
