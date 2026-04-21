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
package com.callibrity.mocapi.demo.feedback.mcp;

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.demo.feedback.dto.FeedbackAckDto;
import com.callibrity.mocapi.demo.feedback.dto.Frequency;
import com.callibrity.mocapi.security.spring.RequiresScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * One MCP tool that lets the calling LLM propose a brand-new tool the server should add. Companion
 * to {@link FeedbackTools} — friction with existing tools goes there, missing-tool proposals go
 * here. Each call emits an INFO log line prefixed with {@code MCP_TOOL_PROPOSAL:} followed by a
 * JSON payload, intended to be aggregated by a downstream agent that drafts PRs adding the
 * highest-conviction proposals.
 */
@Component
@RequiredArgsConstructor
public class ToolProposalTools {

  private static final Logger log = LoggerFactory.getLogger(ToolProposalTools.class);
  private static final String MARKER = "MCP_TOOL_PROPOSAL: ";

  private final ObjectMapper objectMapper;

  @RequiresScope("feedback:write")
  @McpTool(
      name = "suggest-tool",
      title = "Suggest a New Tool",
      description = "${tools.feedback.suggest-tool.description}")
  public FeedbackAckDto proposeTool(
      @Schema(description = "${tools.feedback.suggest-tool.proposed-name.description}")
          @NotBlank
          @Size(max = 100)
          String proposedName,
      @Schema(description = "${tools.feedback.suggest-tool.purpose.description}")
          @NotBlank
          @Size(max = 2000)
          String purpose,
      @Schema(description = "${tools.feedback.suggest-tool.inputs.description}")
          @NotBlank
          @Size(max = 2000)
          String inputs,
      @Schema(description = "${tools.feedback.suggest-tool.output.description}")
          @NotBlank
          @Size(max = 2000)
          String output,
      @Schema(description = "${tools.feedback.suggest-tool.motivating-question.description}")
          @NotBlank
          @Size(max = 2000)
          String motivatingQuestion,
      @Schema(description = "${tools.feedback.suggest-tool.existing-tool-gap.description}")
          @NotBlank
          @Size(max = 2000)
          String existingToolGap,
      @Schema(description = "${tools.feedback.suggest-tool.frequency.description}") @NotNull
          Frequency frequency) {
    if (log.isInfoEnabled()) {
      ProposalPayload payload =
          new ProposalPayload(
              proposedName,
              purpose,
              inputs,
              output,
              motivatingQuestion,
              existingToolGap,
              frequency);
      log.info("{}{}", MARKER, objectMapper.writeValueAsString(payload));
    }
    return new FeedbackAckDto(true);
  }

  private record ProposalPayload(
      String proposedName,
      String purpose,
      String inputs,
      String output,
      String motivatingQuestion,
      String existingToolGap,
      Frequency frequency) {}
}
