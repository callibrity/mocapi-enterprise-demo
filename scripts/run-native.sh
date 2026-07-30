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
# Builds and runs the native image locally, the same way CI does.
#
# You do NOT need GraalVM installed. Cloud Native Buildpacks run the
# native-image compilation inside a container, so Docker is the only
# prerequisite -- this is the identical command .github/workflows/release.yml
# uses to produce the published image, so what you run here is what ships.
#
# The container joins the compose network and talks to Postgres and Keycloak by
# service name. Only the JWKS lookup is rewritten: tokens are still minted via
# localhost:8180, so the issuer the app validates stays localhost, while the
# certs are fetched over the internal network.
#
# Usage:
#   scripts/run-native.sh           # build if missing, then run
#   scripts/run-native.sh --build   # force a rebuild
#   scripts/run-native.sh --stop    # stop the app container and compose stack

set -euo pipefail
cd "$(dirname "$0")/.."

IMAGE=mocapi-demo:native-local
CONTAINER=mocapi-demo-native
NETWORK=mocapi-demo_default
DB=mocapi-demo-native

info() { printf '\033[36m==>\033[0m %s\n' "$1"; }
fail() { printf '\033[31mERROR\033[0m %s\n' "$1" >&2; exit 1; }

docker info >/dev/null 2>&1 || fail "Docker is not running."

if [ "${1:-}" = "--stop" ]; then
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  docker compose down
  info "Stopped."
  exit 0
fi

# --- build (buildpacks; no local GraalVM) ------------------------------------
if [ "${1:-}" = "--build" ] || ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  # native-image compiles inside the container, so it gets Docker's memory
  # allocation rather than the host's. Below ~12GB it is killed partway through
  # with a bare "exit status 137" -- worth catching before a multi-minute wait.
  MEM_GB=$(docker info --format '{{.MemTotal}}' | awk '{printf "%d", $1/1024/1024/1024}')
  if [ "${MEM_GB:-0}" -lt 12 ]; then
    fail "Docker has only ${MEM_GB}GB of memory; the native build needs ~12GB.
       Raise it in Docker Desktop -> Settings -> Resources -> Memory, or build
       on the JVM instead with 'mvn spring-boot:run'."
  fi
  info "Building $IMAGE with buildpacks (Docker memory: ${MEM_GB}GB)."
  info "First run pulls the builder and takes several minutes."
  mvn --batch-mode -Pnative spring-boot:build-image -DskipTests \
    -Dspring-boot.build-image.imageName="$IMAGE" \
    -Dspring-boot.build-image.env.BP_NATIVE_IMAGE=true
else
  info "Using existing $IMAGE (pass --build to rebuild)"
fi

# --- infrastructure -----------------------------------------------------------
info "Starting Postgres / Keycloak / Jaeger"
docker compose up -d

info "Waiting for Postgres"
for _ in $(seq 1 60); do
  docker compose exec -T postgres pg_isready -U mocapi >/dev/null 2>&1 && break
  sleep 1
done

# The native image and the JVM build compute different Liquibase checksums, so a
# schema created by one is rejected by the other. Giving the native build its own
# database lets you switch between them freely. See the README troubleshooting section.
if ! docker compose exec -T postgres psql -U mocapi -d postgres -tAc \
      "SELECT 1 FROM pg_database WHERE datname = '${DB}'" | grep -q 1; then
  info "Creating database ${DB}"
  docker compose exec -T postgres createdb -U mocapi "$DB"
fi

info "Waiting for Keycloak"
for _ in $(seq 1 60); do
  curl -sf -o /dev/null "http://localhost:8180/realms/mocapi-demo" && break
  sleep 2
done

# --- run ----------------------------------------------------------------------
docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
info "Starting $CONTAINER on http://localhost:8080/mcp"
echo

exec docker run --rm --name "$CONTAINER" --network "$NETWORK" -p 8080:8080 \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://postgres:5432/${DB}" \
  -e SPRING_DATASOURCE_USERNAME=mocapi \
  -e SPRING_DATASOURCE_PASSWORD=mocapi-dev \
  -e SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI="http://keycloak:8180/realms/mocapi-demo/protocol/openid-connect/certs" \
  -e MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT="http://jaeger:4318/v1/traces" \
  "$IMAGE"
