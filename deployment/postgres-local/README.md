# Running Eclipse Ditto on PostgreSQL locally

This directory contains everything needed to boot Ditto microservices against the **PostgreSQL persistence
backend** locally and prove it works — without editing any shipped `*.conf`. Activation is done entirely with
NEW overlay config files (in this directory) injected via environment variables.

All files here are additive; no committed service config is modified.

---

## What's here

| File | Purpose |
|------|---------|
| `docker-compose.postgres.yml` | A standalone Postgres 16 (db `ditto`, user/pw `ditto`). Does NOT touch `deployment/docker/docker-compose.yml`. |
| `things-postgres.conf` | Overlay that activates Postgres for the Things service (joins the Policies-founded cluster at 2552). |
| `policies-postgres.conf` | Overlay that activates Postgres for the Policies service (cluster founder). |
| `search-postgres.conf` | Overlay that activates the OPT-IN PostgreSQL search backend for the Things-Search service (joins the cluster at 2552; artery port 2557). |
| `run-compound-postgres.sh` | CLI launcher for Policies + Things + Connectivity + Gateway + Things-Search, all wired to Postgres / pre-auth / CORS. |

IntelliJ run configs live in `.run/` at the repo root:
`PoliciesService (Postgres)`, `ThingsService (Postgres)`, `GatewayService (Postgres)`, and the
compound `Ditto (Postgres)` (which launches all three together).

---

## How activation works (and the four gotchas that took debugging)

Ditto does **not** honor `-Dconfig.file`. It builds its config via `RawConfigSupplier`:
`FILE_BASED overlay` (highest priority) → `things.conf` → `ditto-service-base.conf` → `reference.conf`.
We inject our overlay as the `FILE_BASED` layer:

```
HOSTING_ENVIRONMENT=filebased
HOSTING_ENVIRONMENT_FILE_LOCATION=<abs path to the overlay>
```

The overlay is layered ON TOP of `things.conf`, so everything in `things.conf` is preserved; we only ADD the
Postgres wiring. The overlays encode four non-obvious fixes:

1. **`include classpath("...")`, not bare `include "..."`.** The FILE_BASED file is parsed with
   `parseFileAnySyntax`, where a bare include is file-relative and silently resolves to nothing (the profile
   lives on the classpath inside the persistence-r2dbc jar). `classpath(...)` forces classpath resolution.
2. **Top-level includes, not inside `ditto { }`.** `ditto-postgres-persistence.conf` ships its own `ditto { }`
   wrapper PLUS top-level Pekko plugin blocks (`ditto-postgres-things-journal { }`, ...). Nesting the include
   inside `ditto { }` would misplace those plugin blocks. (The operator guide's `ditto { include ... }` sketch
   is misleading on this point.)
3. **The persistence-r2dbc jar + r2dbc driver must be on the runtime classpath, FIRST.** The service allinone
   jar does NOT bundle the persistence-r2dbc module (provider/journal classes + profile resources + driver),
   and it bundles an OLD `reactor-core` (3.4.x) lacking `Mono.singleOptional()` that the r2dbc driver calls.
   The CLI scripts prepend the r2dbc module jar and its runtime deps (reactor-core 3.5.x, r2dbc-postgresql,
   r2dbc-pool) BEFORE the allinone jar. In IntelliJ this is supplied by a dedicated local-dev module,
   `ide-postgres-launcher` (artifact `ditto-ide-postgres-launcher`): it compile-depends on the services **and**
   `ditto-internal-utils-persistence-r2dbc`, and the `(Postgres)` run configs set their "Use classpath of module"
   to it (keeping the service's own main class). This is needed because the service modules deliberately don't
   depend on r2dbc (the `enforce-no-r2dbc-in-service` rule keeps it out of their allinones) and an IntelliJ
   Application run uses only a module's *production* classpath — so a test-scope dep on the service would never
   reach the run classpath. The launcher is never shaded or published, so r2dbc stays out of every service image.
   Run *Reload All Maven Projects* after pulling for IntelliJ to pick up the new module. (In IntelliJ the module
   graph also resolves the project's `reactor-core` 3.5.20, so the allinone's old 3.4.x copy is not involved.)
4. **Narrowed auto-start lists.** The PostgreSQL profile's default `pekko.persistence.*.auto-start-*` lists name
   the plugins of all four entities; each service's `*-pg-dev.conf` narrows them to its own entity's plugins,
   because a service defines only its own persistence dispatchers. The operations, streaming and health-check
   actors come from the backend provider, so no MongoDB setting is needed.

One more knob is passed via env (the service configs already expose it):
* `POSTGRES_SSL_MODE=disable` — the default SSL mode is `verify-full`; a local plaintext Postgres needs this or
  the pool refuses to connect.

The snapshot envelope no longer needs a per-service override: `ditto.extensions.snapshot-adapter` keeps its
MongoDB default and now selects only the backend-neutral serializer, and the persistent actors compose it with the
active backend's snapshot codec (Mongo BSON / Postgres JSONB) resolved from the persistence-backend-provider.
Including the Postgres persistence profile therefore switches the snapshot envelope to JSONB automatically.

### Extension JAR placement (two JARs per persistence service — changed)

The Postgres backend is packaged as **layered drop-in JARs**, none of them bundled in the service allinone
JARs. This is a **change from the former single `ditto-internal-utils-persistence-r2dbc-extension` JAR**: the shared
third-party runtime (r2dbc driver/pool, reactor, netty, scram) now lives in a **base** JAR that the thin backend JARs
ride on top of, so a persistence service now mounts **two** JARs instead of one:

| JAR | Contents | Mounted by |
|-----|----------|------------|
| `ditto-postgres-client-extension` (base) | postgres-client infra + ALL third-party (r2dbc-postgresql, r2dbc-pool, reactor, netty incl. resolver-dns, scram) | every Postgres service |
| `ditto-postgres-persistence-extension` (thin) | only the event-sourcing persistence-r2dbc classes | things / policies / connectivity |
| `ditto-postgres-search-extension` (thin) | only the search-r2dbc classes | thing-search (only if search runs on Postgres) |

Deployment matrix:

* **things / policies / connectivity:** mount **base + persistence** (2 JARs).
* **thing-search on Postgres:** mount **base + search** (2 JARs). A search service left on MongoDB mounts **nothing**.
* All mounted Postgres extension JARs **must come from the same Ditto release** — the boot self-check
  (`PostgresExtensionVersions`, a version marker in each JAR) fails fast on a mismatch, and a thin JAR mounted without
  its base fails fast with an actionable "requires base `ditto-postgres-client-extension`" error.

Where they run:

* **Production / Kubernetes:** drop the two JARs for the service (base + the matching thin JAR) into
  `/opt/ditto/extensions/` of each service container. The Helm operator guide has an example of staging these via an
  `initContainer`.
* **Local CLI dev (this directory):** the launch scripts assemble the classpath manually from the raw
  `ditto-internal-utils-persistence-r2dbc` module JAR and its runtime deps (which transitively include the
  postgres-client base + r2dbc stack), prepended before the allinone JAR — equivalent to the `/opt/ditto/extensions/`
  drop-in of the base + persistence JARs. No manual JAR copying is needed when using the scripts or the IntelliJ run
  configs.

---

## Step 1 — Start Postgres

```bash
docker compose -f deployment/postgres-local/docker-compose.postgres.yml up -d
# or: docker run -d --name ditto-postgres-local -e POSTGRES_DB=ditto -e POSTGRES_USER=ditto \
#       -e POSTGRES_PASSWORD=ditto -p 5432:5432 postgres:16
```

## Step 2 — Build (once)

```bash
mvn -pl :ditto-things-service,:ditto-policies-service,:ditto-gateway-service,:ditto-connectivity-service,:ditto-thingsearch-service,:ditto-internal-utils-persistence-r2dbc,:ditto-internal-utils-search-r2dbc \
     -am -DskipTests -Dcheckstyle.skip -Dlicense.skip=true -Dpmd.skip=true install
```

## Step 3 — Run Policies + Things + Gateway: full HTTP write→recover demo

Quick schema sanity check once the services are up (the persistence schema is auto-created on first boot):
```bash
docker exec ditto-postgres-local psql -U ditto -d ditto -c '\dt'
#   -> things_journal, things_journal_seq, things_snaps, policies_*, connections_*, wot_*, connection_remember_*,
#      connections_encryption_migration, schema_version
```

```bash
./deployment/postgres-local/run-compound-postgres.sh
# logs: /tmp/policies-pg.log /tmp/things-pg.log /tmp/gateway-pg.log
```
or in IntelliJ run the compound **Ditto (Postgres)**.

Wait until the gateway is up (`curl -s http://localhost:8080/health` is green), then:

```bash
AUTH='x-ditto-pre-authenticated: nginx:ditto'

# 1. Create a policy (Postgres-backed)
curl -s -X PUT -H "$AUTH" -H 'Content-Type: application/json' \
  http://localhost:8080/api/2/policies/org.eclipse.ditto:demo \
  -d '{"entries":{"DEFAULT":{"subjects":{"nginx:ditto":{"type":"x"}},
       "resources":{"thing:/":{"grant":["READ","WRITE"],"revoke":[]},
                    "policy:/":{"grant":["READ","WRITE"],"revoke":[]}}}}}'

# 2. Create a thing (Postgres-backed)
curl -s -X PUT -H "$AUTH" -H 'Content-Type: application/json' \
  http://localhost:8080/api/2/things/org.eclipse.ditto:demo-thing \
  -d '{"policyId":"org.eclipse.ditto:demo","attributes":{"colour":"blue"}}'

# 3. Confirm the row landed in Postgres
docker exec ditto-postgres-local psql -U ditto -d ditto \
  -c "SELECT pid, sn, manifest FROM things_journal ORDER BY sn;"

# 4. RESTART the Things service, then GET the thing back to prove recovery from Postgres
#    (kill just the things JVM and re-run its launcher, or restart the run config)
curl -s -H "$AUTH" http://localhost:8080/api/2/things/org.eclipse.ditto:demo-thing
#    -> returns the thing with attributes.colour=blue, recovered from things_journal / things_snaps
```

---

## Step 4 — Things-Search on PostgreSQL (full-stack, verified 2026-07-05)

`run-compound-postgres.sh` also starts the Things-Search service on the **opt-in PostgreSQL search backend**
(`search-postgres.conf` = `search-dev` + one top-level `include classpath("ditto-postgres-search")`). The
classpath prepends the `search-r2dbc` module jar + its runtime deps (postgres-client, r2dbc driver stack)
before the thingsearch allinone — the CLI equivalent of mounting the `ditto-postgres-client-extension` +
`ditto-postgres-search-extension` JAR pair in `/opt/ditto/extensions/`.

On first boot the search schema (`search_things`, `search_flat`, `search_sync`, component row
`ditto-postgres-search` in `schema_version`) is bootstrapped by the search service's own schema
manager, and the delete-at reaper starts. Verified end-to-end (Phase G3, fresh DB, zero ERROR lines in all
five service logs):

```bash
AUTH='x-ditto-pre-authenticated: nginx:ditto'
# search by attribute, feature property, like + sort, count — all served from Postgres:
curl -s -H "$AUTH" 'http://localhost:8080/api/2/search/things?filter=eq(attributes/colour,"blue")'
curl -s -H "$AUTH" 'http://localhost:8080/api/2/search/things?filter=gt(features/lamp/properties/brightness,50)'
curl -s -H "$AUTH" 'http://localhost:8080/api/2/search/things?filter=like(attributes/colour,"*e*")&option=sort(-attributes/colour)'
curl -s -H "$AUTH" 'http://localhost:8080/api/2/search/things/count?filter=exists(features/lamp)'
# index follows updates and deletes (eventually consistent, ~1s with the dev write interval):
docker exec ditto-postgres-local psql -U ditto -d ditto -c 'SELECT thing_id, revision FROM search_things;'
# health: persistence prober (Postgres SELECT 1) + background sync, both UP:
curl -s http://localhost:8130/status/health
```

**UI smoke test:** serve the Ditto explorer UI locally (`cd ui && npm install && npm run start` → port 8000),
open `http://localhost:8000/index.html?primaryEnvironmentName=local_ditto_ide`, set the environment's main
auth to *Pre-authenticated* with username `nginx:ditto` (Authorize dialog or `authSettings.main.pre` in the
Environments JSON — the gateway is started with `ENABLE_CORS=true` for exactly this), and type an RQL filter
(e.g. `eq(attributes/colour,"green")`) into the Things search box: the result list and count are answered by
the PostgreSQL search backend.

---

## Known limitations (local demo)

* IntelliJ compound runs start all three services at once; they converge via the seed-node retry loop. The CLI
  `run-compound-postgres.sh` sleeps between services to start the Policies founder first (more deterministic).
