# Running Eclipse Ditto on PostgreSQL locally

This directory contains everything needed to boot Ditto microservices against the **PostgreSQL persistence
backend** locally and prove it works — without editing any shipped `*.conf`. Each service gets the PostgreSQL
backend JARs on its classpath and a dev overlay (in this directory) injected via environment variables.

All files here are additive; no committed service config is modified.

---

## What's here

| File                          | Purpose                                                                                                                   |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `docker-compose.postgres.yml` | A standalone Postgres 16 (db `ditto`, user/pw `ditto`). Does NOT touch `deployment/docker/docker-compose.yml`.            |
| `things-postgres.conf`        | Dev overlay for the Things service on Postgres (joins the Policies-founded cluster at 2552).                              |
| `policies-postgres.conf`      | Dev overlay for the Policies service on Postgres (cluster founder).                                                       |
| `search-postgres.conf`        | Dev overlay for the Things-Search service on the PostgreSQL search backend (joins the cluster at 2552; artery port 2557). |
| `connectivity-postgres.conf`  | Dev overlay for the Connectivity service on Postgres (joins the cluster at 2552).                                         |
| `run-compound-postgres.sh`    | CLI launcher for Policies + Things + Connectivity + Gateway + Things-Search, all wired to Postgres / pre-auth / CORS.     |

IntelliJ run configurations live in `.run/` at the repo root: `PoliciesService (Postgres)`, `ThingsService (Postgres)`,
`ConnectivityService (Postgres)`, `SearchService (Postgres)`, `GatewayService (Postgres)` and the compound
`Ditto (Postgres)`, which launches all five together. Their "Use classpath of module" is `ditto-ide-postgres-launcher`;
the plain run configs (`ThingsService`, `Ditto`, …) use `ditto-ide-mongo-launcher` and run on MongoDB.

---

## How activation works (and four gotchas)

Ditto does **not** honor `-Dconfig.file`. It builds its config via `RawConfigSupplier`:
`FILE_BASED overlay` (highest priority) → `things.conf` → `ditto-service-base.conf` → `reference.conf`.
We inject our overlay as the `FILE_BASED` layer:

```
HOSTING_ENVIRONMENT=filebased
HOSTING_ENVIRONMENT_FILE_LOCATION=<abs path to the overlay>
```

The overlay is layered ON TOP of `things.conf`, so everything in `things.conf` is preserved; it only adds the dev
settings, and the backend comes from the JARs on the classpath. Four things to know:

1. **`include classpath("...")`, not bare `include "..."`.** The FILE_BASED file is parsed with
   `parseFileAnySyntax`, where a bare include is file-relative and silently resolves to nothing (the dev profile
   lives on the classpath inside the service jar). `classpath(...)` forces classpath resolution.
2. **Top-level includes, not inside `ditto { }`.** If a custom overlay includes a backend profile by its unique name
   (e.g. `ditto-postgres-persistence.conf`), the include has to be top-level: the profile ships its own `ditto { }`
   wrapper PLUS top-level Pekko plugin blocks (`ditto-postgres-things-journal { }`, ...), which an include inside
   `ditto { }` would misplace.
3. **The PostgreSQL backend JARs must be on the runtime classpath.** The service allinone JARs carry no
   persistence backend at all, so a service without backend JARs refuses to start. The CLI script stages the five
   shaded extension JARs of this build per service with `stage-backends.sh` (the `postgresql/` folder of the image
   layout) and puts that folder before the allinone JAR. In IntelliJ the backend is supplied by a dedicated
   local-dev module, `ide-postgres-launcher` (artifact `ditto-ide-postgres-launcher`): it compile-depends on the
   services **and** `ditto-internal-utils-persistence-r2dbc` / `ditto-internal-utils-search-r2dbc`, and the
   `(Postgres)` run configs set their "Use classpath of module" to it (keeping the service's own main class). This
   is needed because the service modules deliberately don't depend on r2dbc (the `enforce-no-r2dbc-in-service`
   rule keeps it out of their allinones) and an IntelliJ Application run uses only a module's *production*
   classpath — so a test-scope dep on the service would never reach the run classpath. The launcher is never shaded
   or published, so r2dbc stays out of every service image. Run *Reload All Maven Projects* after pulling for
   IntelliJ to pick up the module. `ide-mongo-launcher` does the same for MongoDB.
4. **No auto-start narrowing.** The PostgreSQL profile declares per-entity auto-start keys, and each service conf
   maps its own entity's keys onto Pekko's auto-start lists, so no overlay narrows anything.

One more knob is passed via env (the service configs already expose it):
* `POSTGRES_SSL_MODE=disable` — the default SSL mode is `verify-full`; a local plaintext Postgres needs this or
  the pool refuses to connect.

The snapshot envelope needs no per-service override: `ditto.extensions.snapshot-adapter` keeps its
MongoDB default and selects only the backend-neutral serializer, and the persistent actors compose it with the
active backend's snapshot codec (Mongo BSON / Postgres JSONB) resolved from the persistence-backend-provider.
Including the Postgres persistence profile therefore switches the snapshot envelope to JSONB automatically.

### Extension JAR placement (two JARs per PostgreSQL service)

The Postgres backend is packaged as **layered JARs**, none of them bundled in the service allinone JARs: a **base**
JAR with the shared third-party runtime (r2dbc driver/pool, reactor, netty, scram) and thin backend JARs that ride on top of
it. The Docker images ship them, together with the MongoDB backend, in `/opt/ditto/backends/{mongodb,postgresql}/`;
`stage-backends.sh` holds the service → JAR mapping.

| JAR                                           | Contents                                                                                                             | Used by                                        |
|-----------------------------------------------|----------------------------------------------------------------------------------------------------------------------|------------------------------------------------|
| `ditto-postgres-client-extension` (base)      | postgres-client infra + the third-party runtime (r2dbc-postgresql, r2dbc-pool, reactor, reactor-netty, netty, scram) | every Postgres service                         |
| `ditto-postgres-persistence-extension` (thin) | only the event-sourcing persistence-r2dbc classes                                                                    | things / policies / connectivity               |
| `ditto-postgres-search-extension` (thin)      | only the search-r2dbc classes                                                                                        | thing-search (only if search runs on Postgres) |

Deployment matrix:

* **things / policies / connectivity:** **base + persistence** (2 JARs).
* **thing-search on Postgres:** **base + search** (2 JARs).
* All Postgres extension JARs **must come from the same Ditto release** — the boot self-check
  (`PostgresExtensionVersions`, a version marker in each JAR) fails fast on a mismatch, and a thin JAR without
  its base fails at boot with `NoClassDefFoundError … PostgresConfig` (see `installation-postgresql.md`, "A thin JAR
  without its base").

Where they run:

* **Docker / Kubernetes:** set `DITTO_PERSISTENCE_BACKEND=postgresql` on each Postgres service (Helm:
  `global.persistenceBackend: postgresql`); the image's `/opt/ditto/backends/postgresql/` folder goes on the
  classpath. Nothing is mounted into `/opt/ditto/extensions/`: next to the default `mongodb` folder that would put two
  backends on the classpath, and the service refuses to start.
* **Local CLI dev (this directory):** `run-compound-postgres.sh` calls `./stage-backends.sh <service> 0-SNAPSHOT
  <cache>/<service>` for policies, things, connectivity and thingsearch and puts `<cache>/<service>/postgresql/*`
  before the allinone JAR (the images put it after `/opt/ditto/*`). A missing JAR stops the script before any JVM
  starts, with a message naming the Maven command. No manual JAR copying is needed when using the script or the
  IntelliJ run configs.

---

## Step 1 — Start Postgres

```bash
docker compose -f deployment/postgres-local/docker-compose.postgres.yml up -d
# or: docker run -d --name ditto-postgres-local -e POSTGRES_DB=ditto -e POSTGRES_USER=ditto \
#       -e POSTGRES_PASSWORD=ditto -p 5432:5432 postgres:16
```

## Step 2 — Build (once)

The launcher needs the service allinone JARs and the five shaded backend extension JARs of the same build:

```bash
mvn -T4 install -DskipTests -DskipITs \
    -pl "$(./stage-backends.sh --maven-modules)",things/service,policies/service,connectivity/service,thingsearch/service,gateway/service -am
```

## Step 3 — Run Policies + Things + Connectivity + Gateway + Things-Search: full HTTP write→recover demo

Quick schema sanity check once the services are up (the persistence schema is auto-created on first boot):
```bash
docker exec ditto-postgres-local psql -U ditto -d ditto -c '\dt'
#   -> things_journal, things_journal_seq, things_snaps, policies_*, connections_*, wot_*, connection_remember_*,
#      connections_encryption_migration, schema_version
```

```bash
./deployment/postgres-local/run-compound-postgres.sh
# logs: /tmp/policies-pg.log /tmp/things-pg.log /tmp/connectivity-pg.log /tmp/gateway-pg.log /tmp/search-pg.log
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

## Step 4 — Things-Search on PostgreSQL (full stack)

`run-compound-postgres.sh` also starts the Things-Search service on the **PostgreSQL search backend**
(`search-postgres.conf` = `search-dev` only; `search.conf`'s `ditto-search-backend` include picks the
PostgreSQL search profile from the staged `ditto-postgres-search-extension` JAR). Its staged `postgresql/` folder
holds `ditto-postgres-client-extension` + `ditto-postgres-search-extension`, the same pair the image ships in
`/opt/ditto/backends/postgresql/` of the things-search image.

On first boot the search schema (`search_things`, `search_flat`, `search_sync`, component row
`ditto-postgres-search` in `schema_version`) is bootstrapped by the search service's own schema
manager, and the delete-at reaper starts. To check it end-to-end:

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

* IntelliJ compound runs start all five services at once; they converge via the seed-node retry loop. The CLI
  `run-compound-postgres.sh` does the same.
