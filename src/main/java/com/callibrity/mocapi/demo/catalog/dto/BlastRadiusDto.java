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

public record BlastRadiusDto(
    @Schema(
            description =
                "Short name of the service whose failure is being assessed (echoed from the request for LLM context; call service-lookup if you need the full record)")
        String target,
    @Schema(
            description =
                "Services transitively impacted if the target fails, one row per service with owner team and on-call info inline so the LLM can group or filter however the question wants")
        List<ImpactedServiceDetail> impactedServices,
    @Schema(
            description =
                "Count of orphaned services in the blast radius — each one has no on-call to page")
        int orphanedImpactedCount) {}
