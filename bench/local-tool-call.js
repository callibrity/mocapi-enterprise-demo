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

// k6 load test against a locally-running mocapi-enterprise-demo.
//
// Prerequisites:
//   - Local compose up (docker compose up -d) — Postgres + Keycloak required
//   - App running on :8080 (either `mvn spring-boot:run` or the native binary
//     `./target/mocapi-demo`)
//   - k6 installed (`brew install k6`)
//
// Runs:
//   k6 run bench/local-tool-call.js
//
// Environment overrides:
//   VUS=20 DURATION=30s k6 run bench/local-tool-call.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const MCP = 'http://localhost:8080/mcp';
const TOKEN_URL = 'http://localhost:8180/realms/mocapi-demo/protocol/openid-connect/token';
const CLIENT_ID = 'meridian-oncall';
const CLIENT_SECRET = 'meridian-oncall-secret';

const toolLatency = new Trend('mcp_tool_latency_ms', true);
const toolErrors = new Counter('mcp_tool_errors');

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS) || 10,
      duration: __ENV.DURATION || '30s',
    },
  },
  thresholds: {
    'mcp_tool_errors': ['count<1'],
  },
};

// Mint a token once and share it across all VUs.
export function setup() {
  const res = http.post(TOKEN_URL,
    `grant_type=client_credentials&client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}`,
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } });
  if (res.status !== 200) throw new Error(`Token mint failed: ${res.status} ${res.body}`);
  return { token: res.json('access_token') };
}

const PROTOCOL_VERSION = '2026-07-28';

// Every request carries the protocol envelope. MCP 2026-07-28 is stateless:
// no initialize handshake, no session id, so an iteration is just the tool
// calls themselves. clientInfo is optional and omitted.
const META = {
  'io.modelcontextprotocol/protocolVersion': PROTOCOL_VERSION,
  'io.modelcontextprotocol/clientCapabilities': {},
};

export default function ({ token }) {
  const auth = {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json',
    'Accept': 'application/json, text/event-stream',
    'MCP-Protocol-Version': PROTOCOL_VERSION,
  };

  // Cheap tool: single row lookup.
  timedCall(auth, 'service-lookup', { name: 'payment-processor' });

  // Expensive tool: transitive dependency traversal + team joins.
  timedCall(auth, 'blast-radius', { name: 'payment-processor' });
}

function timedCall(auth, name, args) {
  // Mcp-Method and Mcp-Name must match the body or the transport rejects the
  // request with -32020 HeaderMismatch before dispatch.
  const headers = { ...auth, 'Mcp-Method': 'tools/call', 'Mcp-Name': name };
  const t0 = Date.now();
  const r = http.post(MCP, JSON.stringify({
    jsonrpc: '2.0', id: 99, method: 'tools/call',
    params: { name, arguments: args, _meta: META },
  }), { headers, tags: { tool: name } });
  toolLatency.add(Date.now() - t0, { tool: name });
  if (!check(r, { [`${name} 200`]: (r) => r.status === 200 })) {
    toolErrors.add(1, { tool: name });
  }
}
