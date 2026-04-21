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
import com.callibrity.mocapi.demo.feedback.dto.FrictionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies the feedback tool emits exactly one structured log event per call. The log line is the
 * feature — downstream aggregation greps for the {@code MCP_FEEDBACK:} marker and parses the JSON
 * payload — so testing the log shape is testing the contract.
 */
class FeedbackToolsTest {

  private static final String MARKER = "MCP_FEEDBACK: ";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private FeedbackTools tools;
  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    tools = new FeedbackTools(objectMapper);
    logger = (Logger) LoggerFactory.getLogger(FeedbackTools.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(appender);
  }

  @Test
  void submitFeedbackEmitsSingleInfoLogWithMarkedJsonPayload() throws Exception {
    tools.submitFeedback(
        "services-list",
        FrictionType.MISSING_FIELD,
        "had to call services-list 3 times to find one service",
        "add a name-prefix filter to services-list");

    assertThat(appender.list).hasSize(1);
    ILoggingEvent event = appender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.INFO);
    assertThat(event.getFormattedMessage()).startsWith(MARKER);

    JsonNode payload =
        objectMapper.readTree(event.getFormattedMessage().substring(MARKER.length()));
    assertThat(payload.get("toolName").asString()).isEqualTo("services-list");
    assertThat(payload.get("type").asString()).isEqualTo("MISSING_FIELD");
    assertThat(payload.get("description").asString())
        .isEqualTo("had to call services-list 3 times to find one service");
    assertThat(payload.get("suggestedChange").asString())
        .isEqualTo("add a name-prefix filter to services-list");
  }

  @Test
  void submitFeedbackReturnsAckConfirmingReceipt() {
    FeedbackAckDto ack =
        tools.submitFeedback(
            "team-lookup",
            FrictionType.AMBIGUOUS_NAMING,
            "field 'onCall' wasn't clear if it was a person or a rotation handle",
            "rename to 'onCallRotation'");

    assertThat(ack).isNotNull();
    assertThat(ack.received()).isTrue();
  }

  @Test
  void submitFeedbackEmitsOneEventPerCall() {
    tools.submitFeedback("a", FrictionType.MISSING_FIELD, "x", "y");
    tools.submitFeedback("b", FrictionType.AWKWARD_RESPONSE_SHAPE, "x", "y");
    tools.submitFeedback("c", FrictionType.AWKWARD_SCHEMA, "x", "y");

    assertThat(appender.list).hasSize(3);
  }

  @Test
  void submitFeedbackEmitsNothingWhenInfoLoggingDisabled() {
    Level previous = logger.getLevel();
    try {
      logger.setLevel(Level.WARN);
      FeedbackAckDto ack =
          tools.submitFeedback("svc", FrictionType.MISSING_FIELD, "desc", "change");
      assertThat(appender.list).isEmpty();
      assertThat(ack.received()).isTrue();
    } finally {
      logger.setLevel(previous);
    }
  }

  @Test
  void submitFeedbackSerializesEveryFrictionTypeWithoutLoss() throws Exception {
    for (FrictionType type : FrictionType.values()) {
      appender.list.clear();

      tools.submitFeedback("svc", type, "desc", "change");

      assertThat(appender.list).hasSize(1);
      JsonNode payload =
          objectMapper.readTree(
              appender.list.get(0).getFormattedMessage().substring(MARKER.length()));
      assertThat(payload.get("type").asString())
          .as("FrictionType %s should round-trip through JSON", type)
          .isEqualTo(type.name());
    }
  }
}
