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

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.callibrity.mocapi.demo.feedback.dto.FeedbackAckDto;
import com.callibrity.mocapi.demo.feedback.dto.Frequency;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies the tool-proposal endpoint emits one structured log event per call. The marker is
 * deliberately distinct from {@code MCP_FEEDBACK:} so an aggregator can split the two streams —
 * proposals get clustered into "should we add this tool?" decisions, friction reports get clustered
 * into "should we change an existing tool?" decisions.
 */
class ToolProposalToolsTest {

  private static final String MARKER = "MCP_TOOL_PROPOSAL: ";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private ToolProposalTools tools;
  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    tools = new ToolProposalTools(objectMapper);
    logger = (Logger) LoggerFactory.getLogger(ToolProposalTools.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(appender);
  }

  @Test
  void proposeToolEmitsSingleInfoLogWithMarkedJsonPayload() throws Exception {
    tools.proposeTool(
        "service-runbook-history",
        "Returns the recent runbook revision history for a service so the LLM can summarize what changed and when.",
        "name: string (required), since: ISO date (optional)",
        "List of {revision, author, date, summary} ordered newest-first",
        "User asked 'what changed in the auth-service runbook in the last quarter?'",
        "service-lookup returns only the current runbook URL with no history; no other tool surfaces revision data",
        Frequency.RECURRING_PATTERN);

    assertThat(appender.list).hasSize(1);
    ILoggingEvent event = appender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.INFO);
    assertThat(event.getFormattedMessage()).startsWith(MARKER);

    JsonNode payload =
        objectMapper.readTree(event.getFormattedMessage().substring(MARKER.length()));
    assertThat(payload.get("proposedName").asString()).isEqualTo("service-runbook-history");
    assertThat(payload.get("purpose").asString())
        .startsWith("Returns the recent runbook revision history");
    assertThat(payload.get("inputs").asString()).contains("name: string");
    assertThat(payload.get("output").asString()).contains("revision");
    assertThat(payload.get("motivatingQuestion").asString()).contains("auth-service");
    assertThat(payload.get("existingToolGap").asString()).contains("service-lookup");
    assertThat(payload.get("frequency").asString()).isEqualTo("RECURRING_PATTERN");
  }

  @Test
  void proposeToolReturnsAckConfirmingReceipt() {
    FeedbackAckDto ack =
        tools.proposeTool(
            "team-runbook-coverage",
            "Returns runbook coverage stats per team",
            "team: string (required)",
            "{covered: int, total: int, percentage: double}",
            "User asked 'which teams need to write more runbooks?'",
            "no current tool aggregates runbook URL presence across a team's owned services",
            Frequency.FOUNDATIONAL);

    assertThat(ack).isNotNull();
    assertThat(ack.received()).isTrue();
  }

  @Test
  void proposeToolEmitsOneEventPerCall() {
    tools.proposeTool("a", "p", "i", "o", "q", "g", Frequency.ONCE_THIS_SESSION);
    tools.proposeTool("b", "p", "i", "o", "q", "g", Frequency.RECURRING_PATTERN);
    tools.proposeTool("c", "p", "i", "o", "q", "g", Frequency.FOUNDATIONAL);

    assertThat(appender.list).hasSize(3);
  }

  @Test
  void proposeToolEmitsNothingWhenInfoLoggingDisabled() {
    Level previous = logger.getLevel();
    try {
      logger.setLevel(Level.WARN);
      FeedbackAckDto ack =
          tools.proposeTool("svc", "p", "i", "o", "q", "g", Frequency.ONCE_THIS_SESSION);
      assertThat(appender.list).isEmpty();
      assertThat(ack.received()).isTrue();
    } finally {
      logger.setLevel(previous);
    }
  }

  @Test
  void proposeToolSerializesEveryFrequencyWithoutLoss() throws Exception {
    for (Frequency frequency : Frequency.values()) {
      appender.list.clear();

      tools.proposeTool("svc", "p", "i", "o", "q", "g", frequency);

      assertThat(appender.list).hasSize(1);
      JsonNode payload =
          objectMapper.readTree(
              appender.list.get(0).getFormattedMessage().substring(MARKER.length()));
      assertThat(payload.get("frequency").asString())
          .as("Frequency %s should round-trip through JSON", frequency)
          .isEqualTo(frequency.name());
    }
  }
}
