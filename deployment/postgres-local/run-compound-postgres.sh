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
# backend from the CLI. Policies + Things + Connectivity persist to Postgres; Things-Search runs the OPT-IN
# PostgreSQL search backend (search-postgres.conf overlay; the search-r2dbc module jar + its runtime deps are
# prepended before the allinone — the CLI equivalent of mounting the base + search extension JARs); Gateway is
# stateless routing (no persistence) and just needs the dev config with pre-authentication enabled so HTTP
# PUT/GET work without a token.
#
# Each service logs to /tmp/<svc>-pg.log. Postgres + a one-time build are prerequisites (see README).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"
MVN="${MVN:-mvn}"
OVERLAY_DIR="${REPO_ROOT}/deployment/postgres-local"

# Fail fast if Postgres is not reachable — every downstream failure (crash-looping root actors, gateway
# pub/sub AskTimeouts, auth failures) is harder to diagnose than this one-line check. See README Step 1.
if ! (exec 3<>"/dev/tcp/localhost/5432") 2>/dev/null; then
  echo "ERROR: nothing is listening on localhost:5432 — start Postgres first (README Step 1):" >&2
  echo "  docker compose -f deployment/postgres-local/docker-compose.postgres.yml up -d" >&2
  exit 1
fi
exec 3>&- 2>/dev/null || true

# DEPS CLASSPATHS: generated WITHOUT org.eclipse.ditto artifacts (-DexcludeGroupIds — NOTE: the scope/group
# filter properties of dependency:build-classpath carry NO `mdep.` prefix, unlike mdep.outputFile; the old
# `-Dmdep.includeScope` spelling was silently ignored, which is how test-scope jars leaked in). Ditto classes must
# come from THIS worktree's target/ jars (module jar + allinone), never from ~/.m2 — the local repo's
# 0-SNAPSHOT artifacts are shared across ALL worktrees, so a sibling-branch `mvn install` silently replaces
# them with jars that predate this branch's code (observed: a stale .m2 thingsearch-service jar shadowed the
# allinone and booted the search service on the MONGO backend despite the Postgres overlay). The deps list
# carries ONLY third-party jars (r2dbc stack, reactor-core 3.5.x, netty, scram, ...).
# Caches are keyed by worktree path for the same reason (sibling worktrees share /tmp).
POSTGRES_CLIENT_JAR="internal/utils/postgres-client/target/ditto-internal-utils-postgres-client-0-SNAPSHOT.jar"
WT_KEY="$(echo "$REPO_ROOT" | cksum | cut -d' ' -f1)"

R2DBC_JAR="internal/utils/persistence-r2dbc/target/ditto-internal-utils-persistence-r2dbc-0-SNAPSHOT.jar"
CP_CACHE="/tmp/ditto-r2dbc-cp-${WT_KEY}.txt"
if [[ ! -s "$CP_CACHE" ]]; then
  "$MVN" -q -pl :ditto-internal-utils-persistence-r2dbc dependency:build-classpath \
    -Dmdep.outputFile="$CP_CACHE" -DincludeScope=runtime \
    -DexcludeGroupIds=org.eclipse.ditto >/dev/null
fi
R2DBC_DEPS="$(cat "$CP_CACHE")"

# Things-Search: the search backend lives in search-r2dbc (NOT persistence-r2dbc — the search service must not
# carry the event-sourcing journal plugins); its third-party runtime deps are the r2dbc driver stack +
# reactor-core 3.5.x; the ditto-side deps (postgres-client, persistence-api, rql-parser) come from the
# worktree module jars / the allinone.
SEARCH_R2DBC_JAR="internal/utils/search-r2dbc/target/ditto-internal-utils-search-r2dbc-0-SNAPSHOT.jar"
SEARCH_CP_CACHE="/tmp/ditto-search-r2dbc-cp-${WT_KEY}.txt"
if [[ ! -s "$SEARCH_CP_CACHE" ]]; then
  "$MVN" -q -pl :ditto-internal-utils-search-r2dbc dependency:build-classpath \
    -Dmdep.outputFile="$SEARCH_CP_CACHE" -DincludeScope=runtime \
    -DexcludeGroupIds=org.eclipse.ditto >/dev/null
fi
SEARCH_R2DBC_DEPS="$(cat "$SEARCH_CP_CACHE")"

common_pg_env() {
  export POSTGRES_SSL_MODE=disable
  export POSTGRES_URI="${POSTGRES_URI:-r2dbc:postgresql://localhost:5432/ditto}"
  export POSTGRES_USER="${POSTGRES_USER:-ditto}"
  export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-ditto}"
  export POSTGRES_DDL_USER="${POSTGRES_DDL_USER:-ditto}"
  export POSTGRES_DDL_PASSWORD="${POSTGRES_DDL_PASSWORD:-ditto}"
}

start_pg_service() { # name allinone-jar main-class overlay-file extra-env-assignments...
  local name="$1" jar="$2" main="$3" overlay="$4"; shift 4
  # Worktree module jars first, then third-party deps (newer reactor-core etc. must win over the uber-jar's
  # bundled old copy), then the allinone. postgres-client is explicit: it is banned from the allinone and,
  # being a ditto artifact, deliberately excluded from the generated deps list.
  local cp="${R2DBC_JAR}:${POSTGRES_CLIENT_JAR}:${R2DBC_DEPS}:${jar}"
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
start_pg_service policies \
  "policies/service/target/ditto-policies-service-0-SNAPSHOT-allinone.jar" \
  org.eclipse.ditto.policies.service.starter.PoliciesService \
  "${OVERLAY_DIR}/policies-postgres.conf"

echo "Starting Things (Postgres) ..."
# No snapshot-adapter override: the Postgres provider's JSONB snapshot codec is selected automatically.
# things-postgres.conf joins the Policies-founded cluster at 2552 (no seed-nodes override).
start_pg_service things \
  "things/service/target/ditto-things-service-0-SNAPSHOT-allinone.jar" \
  org.eclipse.ditto.things.service.starter.ThingsService \
  "${OVERLAY_DIR}/things-postgres.conf"

echo "Starting Connectivity (Postgres) ..."
# connectivity-pg-dev.conf is the committed dev profile shipped in the connectivity service sources; it joins
# the Policies-founded cluster at 2552 like the other overlays.
start_pg_service connectivity \
  "connectivity/service/target/ditto-connectivity-service-0-SNAPSHOT-allinone.jar" \
  org.eclipse.ditto.connectivity.service.ConnectivityService \
  "${REPO_ROOT}/connectivity/service/src/main/resources/connectivity-pg-dev.conf"

echo "Starting Gateway (dev, pre-auth, CORS for the local UI) ..."
HOSTING_ENVIRONMENT="" start_dev_service gateway \
  "gateway/service/target/ditto-gateway-service-0-SNAPSHOT-allinone.jar" \
  org.eclipse.ditto.gateway.service.starter.GatewayService \
  "ENABLE_PRE_AUTHENTICATION=true" "DEVOPS_SECURED=false" "ENABLE_CORS=true"

echo "Starting Things-Search (Postgres) ..."
# search-postgres.conf = search-dev + the opt-in ditto-postgres-search include (provider swap + shared client
# config). The search-r2dbc jar + deps are prepended INSTEAD of persistence-r2dbc — deliberately: the search
# service needs no event-sourcing journal plugins, only the search backend + postgres-client + r2dbc stack.
( common_pg_env
  export HOSTING_ENVIRONMENT=filebased
  export HOSTING_ENVIRONMENT_FILE_LOCATION="${OVERLAY_DIR}/search-postgres.conf"
  exec java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED \
    -Xms512m -Xmx512m -cp "${SEARCH_R2DBC_JAR}:${POSTGRES_CLIENT_JAR}:${SEARCH_R2DBC_DEPS}:thingsearch/service/target/ditto-thingsearch-service-0-SNAPSHOT-allinone.jar" \
    org.eclipse.ditto.thingsearch.service.starter.SearchService
) > /tmp/search-pg.log 2>&1 &
echo "  search pid $! -> /tmp/search-pg.log"

echo "All started. Gateway REST on http://localhost:8080  (use header 'x-ditto-pre-authenticated: nginx:ditto')."
echo "Search API: http://localhost:8080/api/2/search/things?filter=...   Search health: http://localhost:8130/status/health"
