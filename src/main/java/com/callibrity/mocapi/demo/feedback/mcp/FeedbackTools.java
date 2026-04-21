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
import com.callibrity.mocapi.demo.feedback.dto.FrictionType;
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
 * One MCP tool that lets the calling LLM submit a single piece of friction-feedback about this
 * server's tool surface. The whole feature is the log line: each call emits an INFO event prefixed
 * with {@code MCP_FEEDBACK:} followed by a JSON payload, ready to be scraped from Log Analytics and
 * fed into a downstream aggregator.
 */
@Component
@RequiredArgsConstructor
public class FeedbackTools {

  private static final Logger log = LoggerFactory.getLogger(FeedbackTools.class);
  private static final String MARKER = "MCP_FEEDBACK: ";

  private final ObjectMapper objectMapper;

  @RequiresScope("feedback:write")
  @McpTool(
      name = "submit-feedback",
      title = "Submit Tool-Friction Feedback",
      description = "${tools.feedback.submit-feedback.description}")
  public FeedbackAckDto submitFeedback(
      @Schema(description = "${tools.feedback.submit-feedback.tool-name.description}")
          @NotBlank
          @Size(max = 100)
          String toolName,
      @Schema(description = "${tools.feedback.submit-feedback.type.description}") @NotNull
          FrictionType type,
      @Schema(description = "${tools.feedback.submit-feedback.description-param.description}")
          @NotBlank
          @Size(max = 2000)
          String description,
      @Schema(description = "${tools.feedback.submit-feedback.suggested-change.description}")
          @NotBlank
          @Size(max = 2000)
          String suggestedChange) {
    if (log.isInfoEnabled()) {
      FeedbackPayload payload = new FeedbackPayload(toolName, type, description, suggestedChange);
      log.info("{}{}", MARKER, objectMapper.writeValueAsString(payload));
    }
    return new FeedbackAckDto(true);
  }

  private record FeedbackPayload(
      String toolName, FrictionType type, String description, String suggestedChange) {}
}
