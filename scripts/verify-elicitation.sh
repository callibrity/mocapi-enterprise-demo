#!/usr/bin/env bash
#
# Copyright © 2026 Callibrity, Inc. (contactus@callibrity.com)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Drives a full MCP 2026-07-28 elicitation round trip against draft-maintenance-comms.
#
# The tool asks two questions, so a complete exchange is three legs:
#
#   leg 1  tools/call                          -> input_required (asks for the window)
#   leg 2  retry + answer 1 + requestState     -> input_required (asks about orphans)
#   leg 3  retry + both answers + requestState -> the finished comms list
#
# Works against the JVM app (`mvn spring-boot:run`) or the native binary
# (`./target/mocapi-demo`) -- pass a different BASE_URL if it isn't on :8080.
# This is the only check that exercises mocapi's MRTR replay end to end, which
# is why it matters for verifying native images.
#
# All three legs must hit the SAME server process: mocapi generates an ephemeral
# requestState key at startup unless mocapi.mrtr.secret is set, so a restart
# mid-run invalidates the token.
#
# Usage:  scripts/verify-elicitation.sh [service-name]
# Env:    BASE_URL, KEYCLOAK_URL, CLIENT_ID, CLIENT_SECRET

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
CLIENT_ID="${CLIENT_ID:-meridian-oncall}"
CLIENT_SECRET="${CLIENT_SECRET:-meridian-oncall-secret}"
SERVICE="${1:-payment-processor}"
PROTOCOL_VERSION="2026-07-28"

fail() { printf '\033[31mFAIL\033[0m %s\n' "$1" >&2; exit 1; }
pass() { printf '\033[32m  ok\033[0m %s\n' "$1"; }

command -v jq >/dev/null 2>&1 || fail "jq is required (brew install jq)"

# The client must advertise the elicitation capability, or the server answers
# -32021 MissingRequiredClientCapabilityError instead of asking the question.
META=$(jq -nc --arg v "$PROTOCOL_VERSION" '{
  "io.modelcontextprotocol/protocolVersion": $v,
  "io.modelcontextprotocol/clientCapabilities": { "elicitation": {} },
  "io.modelcontextprotocol/clientInfo": { "name": "verify-elicitation.sh", "version": "1" }
}')

echo "Verifying elicitation round trip against ${BASE_URL} (service: ${SERVICE})"

TOKEN=$(curl -sS -X POST "${KEYCLOAK_URL}/realms/mocapi-demo/protocol/openid-connect/token" \
  -d "grant_type=client_credentials&client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}" \
  | jq -r '.access_token // empty')
[ -n "$TOKEN" ] || fail "could not mint a token from ${KEYCLOAK_URL} as ${CLIENT_ID}"
pass "token minted as ${CLIENT_ID}"

# Posts a tools/call whose params are supplied as JSON on stdin.
call() {
  curl -sS -X POST "${BASE_URL}/mcp" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H 'Content-Type: application/json' \
    -H 'Accept: application/json, text/event-stream' \
    -H "MCP-Protocol-Version: ${PROTOCOL_VERSION}" \
    -H 'Mcp-Method: tools/call' \
    -H 'Mcp-Name: draft-maintenance-comms' \
    -d @-
}

params() { # $1 = extra JSON merged into params (inputResponses / requestState)
  jq -nc --arg s "$SERVICE" --argjson meta "$META" --argjson extra "$1" \
    '{ name: "draft-maintenance-comms", arguments: { name: $s }, _meta: $meta } + $extra'
}

request() { jq -nc --argjson p "$1" '{ jsonrpc: "2.0", id: 1, method: "tools/call", params: $p }'; }

# --- leg 1: fresh call, expect the first question -----------------------------
RESP=$(request "$(params '{}')" | call)
[ "$(jq -r '.result.resultType // empty' <<<"$RESP")" = "input_required" ] \
  || fail "leg 1 expected input_required, got: $(head -c 300 <<<"$RESP")"
STATE=$(jq -r '.result.requestState // empty' <<<"$RESP")
KEY1=$(jq -r '.result.inputRequests | keys[0]' <<<"$RESP")
[ -n "$STATE" ] || fail "leg 1 returned no requestState"
pass "leg 1 asked for '${KEY1}' and issued a requestState"

# --- leg 2: answer the window, expect the second question ---------------------
ANSWERS=$(jq -nc --arg k "$KEY1" '{ ($k): { action: "accept", content: { window: "2026-08-05 22:00-23:00 UTC" } } }')
RESP=$(request "$(params "$(jq -nc --argjson a "$ANSWERS" --arg s "$STATE" '{ inputResponses: $a, requestState: $s }')")" | call)
[ "$(jq -r '.result.resultType // empty' <<<"$RESP")" = "input_required" ] \
  || fail "leg 2 expected a second input_required, got: $(head -c 300 <<<"$RESP")"
STATE=$(jq -r '.result.requestState // empty' <<<"$RESP")
KEY2=$(jq -r '.result.inputRequests | keys[0]' <<<"$RESP")
pass "leg 2 accepted the answer and asked for '${KEY2}'"

# --- leg 3: answer orphans, expect the finished result ------------------------
ANSWERS=$(jq -nc --arg k "$KEY2" '{ ($k): { action: "accept", content: { include: true } } }')
RESP=$(request "$(params "$(jq -nc --argjson a "$ANSWERS" --arg s "$STATE" '{ inputResponses: $a, requestState: $s }')")" | call)
[ "$(jq -r '.result.resultType // empty' <<<"$RESP")" = "complete" ] \
  || fail "leg 3 expected a completed result, got: $(head -c 300 <<<"$RESP")"

BODY=$(jq -r '.result.structuredContent // (.result.content[0].text | fromjson)' <<<"$RESP")
[ "$(jq -r '.window' <<<"$BODY")" = "2026-08-05 22:00-23:00 UTC" ] \
  || fail "the answered window did not survive replay: $(jq -c '.window' <<<"$BODY")"
[ "$(jq -r '.includedOrphans' <<<"$BODY")" = "true" ] \
  || fail "the orphan answer did not survive replay"
[ "$(jq -r '.notifications | length' <<<"$BODY")" -gt 0 ] \
  || fail "no notification lines were rendered"
pass "leg 3 completed; both answers survived replay"

echo
jq -r '"  window:        \(.window)\n  orphans:       \(.includedOrphans)\n  notify:        \(.notifications | length) team(s)\n  notice:        \(.notice)"' <<<"$BODY"
echo
printf '\033[32mPASS\033[0m elicitation round trip verified against %s\n' "$BASE_URL"
