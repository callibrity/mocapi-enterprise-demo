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
package com.callibrity.mocapi.demo;

import static com.callibrity.mocapi.demo.McpTestSupport.ONCALL;
import static com.callibrity.mocapi.demo.McpTestSupport.OWNER;
import static com.callibrity.mocapi.demo.McpTestSupport.PROTOCOL_VERSION;
import static com.callibrity.mocapi.demo.McpTestSupport.VIEWER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Drives the real MCP wire protocol against a running application context and a real Postgres.
 *
 * <p>The unit tests cover handler logic; this covers everything between the socket and the handler
 * — auto-configuration, the OAuth2 filter chain, scope guards, the transport's header validation,
 * JSON-RPC framing, and JPA against the Liquibase-managed schema. Two production defects reached
 * this repo that a suite of pure unit tests could not see, and each has a test here:
 *
 * <ul>
 *   <li>An OpenTelemetry version skew made every <em>failing</em> tool call hang for 30s and return
 *       an HTML 503 instead of a JSON-RPC error. See {@link #throwingToolReturnsJsonRpcError()}.
 *   <li>A Spring Boot upgrade moved an auto-configuration class Mocapi referenced, so the context
 *       could not start at all. Every test here fails if that recurs, because none of them can run
 *       without a context.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(McpTestSupport.class)
@ActiveProfiles("test")
@DisplayName("MCP endpoint")
class McpEndpointIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;

  @Test
  @DisplayName("server/discover advertises only the 2026-07-28 protocol")
  void discoverAdvertisesProtocol() throws Exception {
    JsonNode result = call(VIEWER, "server/discover", null, discoverBody()).get("result");

    assertThat(result.path("supportedVersions").size()).isEqualTo(1);
    assertThat(result.path("supportedVersions").get(0).asString()).isEqualTo(PROTOCOL_VERSION);
    assertThat(
            result.path("_meta").path("io.modelcontextprotocol/serverInfo").path("name").asText())
        .isEqualTo("mocapi-demo");
  }

  @Test
  @DisplayName("a tool call returns catalog data")
  void toolCallReturnsData() throws Exception {
    JsonNode result =
        call(ONCALL, "tools/call", "blast-radius", toolBody("blast-radius", "payment-processor"))
            .get("result");

    assertThat(result.path("isError").asBoolean(false)).isFalse();
    assertThat(result.path("content").get(0).path("text").asText()).contains("payment-processor");
  }

  /**
   * The regression test for the OpenTelemetry skew. A tool that throws must fail as JSON-RPC,
   * promptly. When the logback appender was mismatched against the OTel API, logging the exception
   * threw {@code NoSuchMethodError}, the handler thread died, and the request hung until the
   * servlet container's async timeout and returned an HTML error page.
   */
  @Test
  @DisplayName("a throwing tool returns a JSON-RPC error, not an HTML 503")
  void throwingToolReturnsJsonRpcError() throws Exception {
    JsonNode body =
        call(VIEWER, "tools/call", "service-lookup", toolBody("service-lookup", "nope"));

    assertThat(body.path("jsonrpc").asText()).isEqualTo("2.0");
    assertThat(body.path("result").path("isError").asBoolean()).isTrue();
    assertThat(body.path("result").path("content").get(0).path("text").asText())
        .contains("Unknown service");
  }

  @Test
  @DisplayName("validation runs before the handler")
  void validationRejectsOutOfRangeArgument() throws Exception {
    JsonNode body =
        call(
            VIEWER,
            "tools/call",
            "services-list",
            """
            {"name":"services-list","arguments":{"pageSize":9999},%s}"""
                .formatted(meta()));

    assertThat(body.path("result").path("isError").asBoolean()).isTrue();
    assertThat(body.path("result").path("content").get(0).path("text").asText())
        .contains("must be less than or equal to 100");
  }

  @Test
  @DisplayName("a routing header that disagrees with the body is rejected")
  void headerMismatchIsRejected() throws Exception {
    MvcResult res =
        perform(
            post("/mcp")
                .header("Authorization", "Bearer " + VIEWER)
                .header("MCP-Protocol-Version", PROTOCOL_VERSION)
                .header("Mcp-Method", "tools/call")
                .header("Mcp-Name", "team-lookup") // body says service-lookup
                .contentType(MediaType.APPLICATION_JSON)
                .accept("application/json", "text/event-stream")
                .content(
                    """
                        {"jsonrpc":"2.0","id":1,"method":"tools/call","params":%s}"""
                        .formatted(toolBody("service-lookup", "cart-service"))));

    JsonNode body = json.readTree(res.getResponse().getContentAsString());
    assertThat(body.path("error").path("code").asInt()).isEqualTo(-32020);
  }

  @Test
  @DisplayName("GET is not part of the 2026-07-28 transport")
  void getIsMethodNotAllowed() throws Exception {
    mvc.perform(get("/mcp").header("Authorization", "Bearer " + VIEWER))
        .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(405));
  }

  @Test
  @DisplayName("clients must accept both JSON and SSE")
  void jsonOnlyAcceptIsRejected() throws Exception {
    MvcResult res =
        perform(
            post("/mcp")
                .header("Authorization", "Bearer " + VIEWER)
                .header("MCP-Protocol-Version", PROTOCOL_VERSION)
                .header("Mcp-Method", "server/discover")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(discoverEnvelope()));

    assertThat(res.getResponse().getStatus()).isEqualTo(406);
  }

  @Test
  @DisplayName("an unauthenticated call is rejected")
  void anonymousIsUnauthorized() throws Exception {
    mvc.perform(
            post("/mcp")
                .header("MCP-Protocol-Version", PROTOCOL_VERSION)
                .header("Mcp-Method", "server/discover")
                .contentType(MediaType.APPLICATION_JSON)
                .accept("application/json", "text/event-stream")
                .content(discoverEnvelope()))
        .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(401));
  }

  @Test
  @DisplayName("tool visibility is gated by the caller's scopes")
  void scopesGateToolVisibility() throws Exception {
    assertThat(toolNames(VIEWER))
        .hasSize(4)
        .contains("service-lookup")
        .doesNotContain("blast-radius");
    assertThat(toolNames(ONCALL)).hasSize(10).contains("blast-radius");
    assertThat(toolNames(OWNER)).hasSize(12).contains("submit-feedback");
  }

  // --- helpers ---------------------------------------------------------------

  /**
   * Performs a request and, when the handler went asynchronous, dispatches again to collect the
   * real response. The MCP transport returns a {@code CompletableFuture} so it can stream progress
   * over SSE; without the second dispatch MockMvc hands back an empty body.
   */
  private MvcResult perform(RequestBuilder request) throws Exception {
    MvcResult result = mvc.perform(request).andReturn();
    if (result.getRequest().isAsyncStarted()) {
      result = mvc.perform(asyncDispatch(result)).andReturn();
    }
    return result;
  }

  private List<String> toolNames(String scopes) throws Exception {
    JsonNode tools = call(scopes, "tools/list", null, listBody()).path("result").path("tools");
    return StreamSupport.stream(tools.spliterator(), false)
        .map(t -> t.path("name").asString())
        .toList();
  }

  private JsonNode call(String scopes, String method, String name, String params) throws Exception {
    var req =
        post("/mcp")
            .header("Authorization", "Bearer " + scopes)
            .header("MCP-Protocol-Version", PROTOCOL_VERSION)
            .header("Mcp-Method", method)
            .contentType(MediaType.APPLICATION_JSON)
            .accept("application/json", "text/event-stream")
            .content(
                """
                {"jsonrpc":"2.0","id":1,"method":"%s","params":%s}"""
                    .formatted(method, params));
    if (name != null) {
      req = req.header("Mcp-Name", name);
    }
    MvcResult res = perform(req);
    return json.readTree(res.getResponse().getContentAsString());
  }

  private static String meta() {
    return """
        "_meta":{"io.modelcontextprotocol/protocolVersion":"%s",
                 "io.modelcontextprotocol/clientCapabilities":{}}"""
        .formatted(PROTOCOL_VERSION);
  }

  private static String discoverBody() {
    return "{%s}".formatted(meta());
  }

  private static String listBody() {
    return "{%s}".formatted(meta());
  }

  private static String discoverEnvelope() {
    return """
        {"jsonrpc":"2.0","id":1,"method":"server/discover","params":{%s}}"""
        .formatted(meta());
  }

  private static String toolBody(String tool, String serviceName) {
    return """
        {"name":"%s","arguments":{"name":"%s"},%s}"""
        .formatted(tool, serviceName, meta());
  }
}
