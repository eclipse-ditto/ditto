#!/usr/bin/env bash
# Copyright (c) 2026 Contributors to the Eclipse Foundation
#
# See the NOTICE file(s) distributed with this work for additional
# information regarding copyright ownership.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0
# Launch the COMPOUND (Policies + Things + Connectivity + Gateway + Things-Search) on the local PostgreSQL
# backend from the CLI. Policies + Things + Connectivity persist to Postgres; Things-Search runs the PostgreSQL
# search backend. Each of the four gets the PostgreSQL backend JARs the Docker images ship in
# /opt/ditto/backends/postgresql/, staged by stage-backends.sh (the single service -> JAR mapping) from this
# worktree's build. They are put BEFORE the allinone on the classpath; the images put them after /opt/ditto/*.
# Gateway is stateless routing (no persistence) and just needs the dev config with pre-authentication enabled so
# HTTP PUT/GET work without a token.
#
# Each service logs to /tmp/<svc>-pg.log. Postgres + a one-time build are prerequisites (see README).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"
OVERLAY_DIR="${REPO_ROOT}/deployment/postgres-local"

# Fail fast if Postgres is not reachable — every downstream failure (crash-looping root actors, gateway
# pub/sub AskTimeouts, auth failures) is harder to diagnose than this one-line check. See README Step 1.
if ! (exec 3<>"/dev/tcp/localhost/5432") 2>/dev/null; then
  echo "ERROR: nothing is listening on localhost:5432 — start Postgres first (README Step 1):" >&2
  echo "  docker compose -f deployment/postgres-local/docker-compose.postgres.yml up -d" >&2
  exit 1
fi

# BACKEND JARS: stage-backends.sh copies the five shaded extension JARs of THIS worktree's build (see README
# Step 2) into <dest>/{mongodb,postgresql}/, the layout of /opt/ditto/backends/ in the images. A missing JAR makes
# it exit 1 (naming the Maven command) before anything is staged; `set -e` then stops this script before any JVM
# starts. All four services are staged before the first launch. The cache is keyed by worktree path, because
# sibling worktrees share the temp dir.
WT_KEY="$(echo "$REPO_ROOT" | cksum | cut -d' ' -f1)"
BACKENDS_CACHE="${TMPDIR:-/tmp}/ditto-backends-${WT_KEY}"
for svc in policies things connectivity thingsearch; do
  ./stage-backends.sh "$svc" 0-SNAPSHOT "${BACKENDS_CACHE}/${svc}" >/dev/null
done

common_pg_env() {
  export POSTGRES_SSL_MODE=disable
  export POSTGRES_URI="${POSTGRES_URI:-r2dbc:postgresql://localhost:5432/ditto}"
  export POSTGRES_USER="${POSTGRES_USER:-ditto}"
  export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-ditto}"
  export POSTGRES_DDL_USER="${POSTGRES_DDL_USER:-ditto}"
  export POSTGRES_DDL_PASSWORD="${POSTGRES_DDL_PASSWORD:-ditto}"
}

start_pg_service() { # name stage-service allinone-jar main-class overlay-file extra-env-assignments...
  local name="$1" svc="$2" jar="$3" main="$4" overlay="$5"; shift 5
  # The staged PostgreSQL backend JARs first, then the allinone (which carries no persistence backend).
  local cp="${BACKENDS_CACHE}/${svc}/postgresql/*:${jar}"
  ( common_pg_env
    export HOSTING_ENVIRONMENT=filebased
    export HOSTING_ENVIRONMENT_FILE_LOCATION="$overlay"
    for kv in "$@"; do export "$kv"; done
    exec java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED \
      -Xms512m -Xmx512m -cp "$cp" "$main"
  ) > "/tmp/${name}-pg.log" 2>&1 &
  echo "  ${name} pid $! -> /tmp/${name}-pg.log"
}

start_dev_service() { # name allinone-jar main-class extra-env...
  # -cp + explicit main class (NOT -jar): keeps the launch style identical to start_pg_service, so jps lists
  # every service by its main class instead of showing the gateway as a jar file name.
  local name="$1" jar="$2" main="$3"; shift 3
  ( for kv in "$@"; do export "$kv"; done
    exec java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED \
      -Xms512m -Xmx512m -cp "$jar" "$main"
  ) > "/tmp/${name}-pg.log" 2>&1 &
  echo "  ${name} pid $! -> /tmp/${name}-pg.log"
}

# All services start concurrently: every dev profile seeds on pekko://ditto-cluster@127.0.0.1:2552 (Policies'
# artery port). Policies is the FIRST entry in its own seed list, so it self-joins and founds ditto-cluster;
# the others retry that seed until it answers (shutdown-after-unsuccessful-join-seed-nodes = 60s, far longer
# than a local Policies boot) — no staged waits needed.
echo "Starting Policies (Postgres) ..."
# No snapshot-adapter override: AbstractPersistenceActor composes the per-service snapshot-adapter (serializer) with
# the Postgres provider's JSONB snapshot codec automatically once the Postgres persistence profile is included.
start_pg_service policies policies \
  "policies/service/target/ditto-policies-service-0-SNAPSHOT-allinone.jar" \
  org.eclipse.ditto.policies.service.starter.PoliciesService \
  "${OVERLAY_DIR}/policies-postgres.conf"

echo "Starting Things (Postgres) ..."
# No snapshot-adapter override: the Postgres provider's JSONB snapshot codec is selected automatically.
# things-postgres.conf joins the Policies-founded cluster at 2552 (no seed-nodes override).
start_pg_service things things \
  "things/service/target/ditto-things-service-0-SNAPSHOT-allinone.jar" \
  org.eclipse.ditto.things.service.starter.ThingsService \
  "${OVERLAY_DIR}/things-postgres.conf"

echo "Starting Connectivity (Postgres) ..."
# connectivity-postgres.conf layers connectivity-dev only; it joins the Policies-founded cluster at 2552 like the
# other overlays, and the staged PostgreSQL JARs select the backend.
start_pg_service connectivity connectivity \
  "connectivity/service/target/ditto-connectivity-service-0-SNAPSHOT-allinone.jar" \
  org.eclipse.ditto.connectivity.service.ConnectivityService \
  "${OVERLAY_DIR}/connectivity-postgres.conf"

echo "Starting Gateway (dev, pre-auth, CORS for the local UI) ..."
HOSTING_ENVIRONMENT="" start_dev_service gateway \
  "gateway/service/target/ditto-gateway-service-0-SNAPSHOT-allinone.jar" \
  org.eclipse.ditto.gateway.service.starter.GatewayService \
  "ENABLE_PRE_AUTHENTICATION=true" "DEVOPS_SECURED=false" "ENABLE_CORS=true"

echo "Starting Things-Search (Postgres) ..."
# search-postgres.conf = search-dev only; search.conf's ditto-search-backend include finds the PostgreSQL search
# profile in the staged PostgreSQL search JAR (with the PostgreSQL base JAR; no event-sourcing journal plugins).
start_pg_service search thingsearch \
  "thingsearch/service/target/ditto-thingsearch-service-0-SNAPSHOT-allinone.jar" \
  org.eclipse.ditto.thingsearch.service.starter.SearchService \
  "${OVERLAY_DIR}/search-postgres.conf"

echo "All started. Gateway REST on http://localhost:8080  (use header 'x-ditto-pre-authenticated: nginx:ditto')."
echo "Search API: http://localhost:8080/api/2/search/things?filter=...   Search health: http://localhost:8130/status/health"
