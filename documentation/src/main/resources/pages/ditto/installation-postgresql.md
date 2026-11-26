---
title: Running Ditto on PostgreSQL
tags: [getting_started, installation]
keywords: postgresql, postgres, persistence, search, backend, docker, docker-compose, helm, kubernetes, extension, configuration, provisioning
permalink: installation-postgresql.html
---

By default, Ditto stores Things, Policies and Connectivity data in MongoDB, and the things-search service keeps its
search index in MongoDB as well. Both can run on PostgreSQL instead: the **PostgreSQL persistence backend** for the
policies, things and connectivity services, and the **PostgreSQL search backend** for the things-search service. The
two are independent of each other; each service selects its backend, and MongoDB remains the default.

{% include callout.html content="**TL;DR**: Set `DITTO_PERSISTENCE_BACKEND=postgresql` on the service (Helm: `global.persistenceBackend: postgresql`) and set the `POSTGRES_*` environment variables. The extension JARs ship in the Ditto images. The schema is created at the first start." type="primary" %}

This page covers the installation: the JARs, selecting the backend, provisioning, the environment variables and the
Docker Compose and Helm setups. Everything that matters once the services run (schema lifecycle and upgrades,
self-heal, the connection budget, TLS, PgBouncer, monitoring, vacuum and the differences from MongoDB) is on
[Operating PostgreSQL](operating-postgresql.html).

## Overview

| Backend                        | Services                       | Thin extension JAR                     | Folder in the image               |
|--------------------------------|--------------------------------|----------------------------------------|-----------------------------------|
| PostgreSQL persistence backend | policies, things, connectivity | `ditto-postgres-persistence-extension` | `/opt/ditto/backends/postgresql/` |
| PostgreSQL search backend      | things-search                  | `ditto-postgres-search-extension`      | `/opt/ditto/backends/postgresql/` |

Both backends also need the shared base JAR `ditto-postgres-client-extension` and the same `POSTGRES_*` environment
variables. Any combination works: all three persistence services on PostgreSQL with search on MongoDB, search alone on
PostgreSQL, or everything on PostgreSQL. `DITTO_PERSISTENCE_BACKEND` selects the backend per service; a service left
on MongoDB keeps the default and changes nothing.

**PostgreSQL version:** PostgreSQL 16 or newer (tested: 16, 17, 18). Older versions log a warning at startup, but the
service still starts; technically, persistence needs PostgreSQL ≥ 10 and search PostgreSQL ≥ 13 (from 13 on, `pg_trgm`
is a trusted extension, see [Prerequisites of the search backend](operating-postgresql.html#prerequisites-migration-and-known-limitations)).

**Database encoding and locale (search):** the search database must be UTF8-encoded, with a UTF-8-based `LC_CTYPE`
(e.g. `C.UTF-8` or `en_US.UTF-8`) and the libc collation `C.utf8`. The search service checks this at every start,
before it touches the schema, and refuses to start otherwise; CloudNativePG's `initdb` default `LC_CTYPE` `C` does not
qualify. See [Provisioning the database](#provisioning-the-database).

{% include note.html content="Data migration from MongoDB is the operator's responsibility. No automated migration tooling is provided for the persistence services. The search index is a rebuildable projection of the things data: point the search service at an empty PostgreSQL database and fill the index, namespace by namespace, with the force-update command, see [Cutover to the PostgreSQL search index](operating-postgresql.html#cutover-to-the-postgresql-search-index)." %}

## The extension JARs

The Ditto images ship the extension JARs in `/opt/ditto/backends/postgresql/`. The PostgreSQL backends are
**layered JARs** built from three Maven modules — a shared **base** JAR carrying the third-party runtime the service images lack
(R2DBC driver/pool, reactor, reactor-netty, netty, scram) and **thin** backend JARs carrying only Ditto implementation classes:

| Module / JAR                                  | Contents                                                                                                                                      | Used by                                           |
|-----------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------|
| `ditto-postgres-client-extension` (base)      | shared `postgres-client` infra + the third-party runtime the images lack (r2dbc-postgresql, r2dbc-pool, reactor, reactor-netty, netty, scram) | every service on PostgreSQL                       |
| `ditto-postgres-persistence-extension` (thin) | only the event-sourcing persistence classes                                                                                                   | policies / things / connectivity                  |
| `ditto-postgres-search-extension` (thin)      | only the thing-search classes                                                                                                                 | things-search (only if search runs on PostgreSQL) |

A service on PostgreSQL therefore gets **two** JARs: the base plus the matching thin JAR. The images put
`/opt/ditto/*`, `/opt/ditto/backends/${DITTO_PERSISTENCE_BACKEND}/*` and `/opt/ditto/extensions/*` on the classpath
(see [Adding JARs to the classpath](installation-extending.html#adding-jars-to-the-classpath)), so with
`DITTO_PERSISTENCE_BACKEND=postgresql` the images' own `postgresql` folder provides them and nothing is mounted. Do
**not** also put the JARs into `/opt/ditto/extensions/`: next to the default `mongodb` folder the service finds two
backends on its classpath and refuses to start, and next to the `postgresql` folder they are a second copy of the same
classes (see [Persistence backends](installation-extending.html#persistence-backends)).

### Where the JARs come from

The images take the JARs from the same build as the service. For a custom image, the three JARs are Maven modules of
the Ditto build, under `internal/utils/` in the Ditto repository, with the artifact coordinates
`org.eclipse.ditto:ditto-postgres-client-extension`, `org.eclipse.ditto:ditto-postgres-persistence-extension` and
`org.eclipse.ditto:ditto-postgres-search-extension` (also on Maven Central with each release). Each is a shaded JAR;
the thin ones ride on top of the base. `stage-backends.sh <service> <version> <dest>` in the repository root copies
the built JARs of one service into `<dest>/mongodb/` and `<dest>/postgresql/`, the layout of `/opt/ditto/backends/`;
it is what `build-images.sh` and the release build use. To build the JARs from the sources (from the Ditto repository
root):

```bash
# all five backend extension JARs; -Drevision sets the version marker (see the same-version rule below)
mvn -pl "$(./stage-backends.sh --maven-modules)" -am -DskipTests -DskipITs clean install -Drevision=<ditto version>

./stage-backends.sh things <ditto version> target/backends-things
#   -> target/backends-things/postgresql/ditto-postgres-client-extension-<ditto version>.jar
#      target/backends-things/postgresql/ditto-postgres-persistence-extension-<ditto version>.jar
```

### The same-version rule

This rule matters for custom-built images: all PostgreSQL extension JARs **must come from the same Ditto release as
the service**. Every extension
JAR carries a version marker (`META-INF/ditto-postgres-extension.properties`, the Maven `revision` it was built with),
and the provider compares the markers of all extension JARs on the classpath at start: JARs from different versions fail the
start with an `IllegalStateException` naming each component and version (`The mounted Postgres extension jars are from
DIFFERENT Ditto releases: …`). The check compares the extension JARs with each other only, not with the image, and it
passes quietly when fewer than two markers are found. The release images are built from the tagged sources with
`-Drevision=<release tag>`, so build the extension JARs from the same tag with the same `-Drevision`, or take them from
the same release; a build without `-Drevision` produces `0-SNAPSHOT` JARs, which pass the check against each other
whatever image they are put into. Build them from a clean tree (`mvn clean install -DskipTests -DskipITs`, as above):
the extension poms force the thin JAR to be rebuilt, and `build-images.sh` refuses a shaded extension JAR whose
classes differ from its module JAR, so an incremental build can no longer ship the previous build's classes unnoticed.

### A thin JAR without its base

A thin JAR on the classpath without the base JAR fails the start of the service with

```
java.lang.NoClassDefFoundError: org/eclipse/ditto/internal/utils/persistence/postgres/client/config/PostgresConfig
```

(or another class of the `…persistence.postgres.client` package): the provider class links against types of the base
JAR before any check of its own can run. The fix is to put `ditto-postgres-client-extension` from the same release
next to the thin JAR. The reverse (the base JAR alone) provides no backend, so the service refuses to start with the
"No persistence backend" message (see [Selecting the backend](#selecting-the-backend)).

## Selecting the backend

`DITTO_PERSISTENCE_BACKEND=postgresql` is all a service needs to run on PostgreSQL. Each backend JAR ships a fixed-name
profile that the service configuration always includes: `ditto-persistence-backend.conf` (persistence services) and
`ditto-search-backend.conf` (things-search). In the PostgreSQL JARs they pull in `ditto-postgres-persistence.conf`
or `ditto-postgres-search.conf`, which swap the provider extension (`ditto.extensions.persistence-backend-provider`
or `ditto.extensions.search-persistence-provider`) to the PostgreSQL implementation, define the Pekko plugin blocks
(persistence only) and pull in the shared `ditto.postgresql.*` client, pool and SSL defaults from the base JAR
(`ditto-postgres-client.conf`). The snapshot encoding follows the provider, so no snapshot-adapter override is needed.
No `include`, no `required(...)` and no auto-start narrowing has to be written by the operator.

The persistence profile also lists each entity's plugins under `ditto.persistence-backend.auto-start.<entity>`
(`things`, `policies`, `connections`, each with `journals` and `snapshots`). Each service configuration assigns its own
entity's lists to Pekko's `auto-start-*` lists, so a service starts only the plugins whose dispatchers it defines. The
things service's WoT plugins (`ditto-postgres-wot-*`) start lazily and are not in the lists. The search service has no
event-sourcing journal.

A service that finds no backend, or several, on its classpath refuses to start before any actor starts. The message
names `DITTO_PERSISTENCE_BACKEND` and the fix, for example `No persistence backend is on the classpath.
DITTO_PERSISTENCE_BACKEND is unset. …` or `Several persistence backends are on the classpath: [mongodb, postgresql]. …`.

### Per-service names

| Service       | Thin JAR                               | Journal plugin                       | Snapshot store                         | Read-journal entity (`read-journal.entity`) |
|---------------|----------------------------------------|--------------------------------------|----------------------------------------|---------------------------------------------|
| policies      | `ditto-postgres-persistence-extension` | `ditto-postgres-policies-journal`    | `ditto-postgres-policies-snapshots`    | `policies`                                  |
| things        | `ditto-postgres-persistence-extension` | `ditto-postgres-things-journal`      | `ditto-postgres-things-snapshots`      | `things`                                    |
| connectivity  | `ditto-postgres-persistence-extension` | `ditto-postgres-connections-journal` | `ditto-postgres-connections-snapshots` | `connections`                               |
| things-search | `ditto-postgres-search-extension`      | —                                    | —                                      | —                                           |

The read-journal entity binds the service's shared read journal and is set by each service's own configuration
(`things.conf`, `policies.conf`, `connectivity.conf`); `POSTGRES_READ_JOURNAL_ENTITY` overrides it, which a standard
deployment never needs. The things service reads the history of WoT validation configs through a separate read
journal over the `wot` tables, so its value stays `things`.

Any other setting of these pages, for example `ditto.postgresql.pool.max-size` or the per-path search indexes, can be
set in the service's `<service>-extension.conf` (see
[Adjusting service configuration](installation-extending.html#adjusting-service-configuration)) instead of through an
environment variable.

## Provisioning the database

1. **Create the database.** Any name works (`ditto` below). The persistence services have no requirements on its
   encoding or locale. The search backend requires a UTF8-encoded database with a UTF-8-based `LC_CTYPE` and the
   libc collation `C.utf8` (the official `postgres` image's default, `en_US.utf8`, qualifies). Inside a cluster
   initialised with `C`, create it explicitly:

   ```sql
   CREATE DATABASE ditto TEMPLATE template0 ENCODING 'UTF8' LC_COLLATE 'C.UTF-8' LC_CTYPE 'C.UTF-8';
   ```

   On CloudNativePG, whose `initdb` default `LC_CTYPE` is `C`, set `spec.bootstrap.initdb.localeCType: C.UTF-8`
   (the default encoding is already `UTF8`). The query that checks an existing database is in
   [Prerequisites, migration and known limitations](operating-postgresql.html#prerequisites-migration-and-known-limitations).
   The persistence services and the search service can share one database or use separate ones; the connection
   budget and the schema rows are per database (see [Schema upgrades](operating-postgresql.html#schema-upgrades)).

2. **Create the roles, as the owner of the database.** Ditto runs with two roles: a **DDL role** that owns the schema
   and creates the tables (used by the schema check at every start, the schema self-heal and the per-path search index
   builds) and a **runtime role** with `SELECT`, `INSERT`, `UPDATE` and `DELETE` only. The script
   [`deployment/postgres/provisioning/01-roles-and-grants.sql`](https://github.com/eclipse-ditto/ditto/blob/master/deployment/postgres/provisioning/01-roles-and-grants.sql)
   creates them as `ditto_ddl` and `ditto_app`:

   ```bash
   psql -h <pg-host> -U <database-owner> -d ditto -v ON_ERROR_STOP=1 \
        -v ddl_pw="$DITTO_DDL_PASSWORD" \
        -v app_pw="$DITTO_APP_PASSWORD" \
        -f deployment/postgres/provisioning/01-roles-and-grants.sql
   ```

   Run it as the owner of the database: a superuser, or on a managed service (Amazon RDS, Azure Database for
   PostgreSQL, Cloud SQL) the master or admin user, who is no superuser. It creates the schema `ditto` owned by
   `ditto_ddl`, grants `ditto_ddl` `CREATE` on the database (needed to create the trusted `pg_trgm` extension at the
   search service's first start), sets the default privileges so that every table `ditto_ddl` creates is born with the
   DML grant to `ditto_app`, puts `ditto` on both roles' `search_path`, and gives `ditto_app` a `statement_timeout`
   default of `60s`. **The order matters:** the default privileges apply only to tables created after the statement
   runs, so the script must run before the first service starts. The full runbook, including re-provisioning an existing
   schema and the role model, is
   [`deployment/postgres/README.md`](https://github.com/eclipse-ditto/ditto/blob/master/deployment/postgres/README.md).

   Without a separate DDL role (`POSTGRES_DDL_USER` unset), the runtime role runs the schema check, the self-heal and
   the index builds itself and must then be allowed to create tables in the schema; the local example below uses the
   single `ditto` superuser of the `postgres` image that way. With the two-role script, `POSTGRES_DDL_USER` is
   mandatory: as `ditto_app`, the schema check fails with SQLSTATE `42501` and the service stops.

3. **Start the services.** The schema tables are created automatically at the first start, by the DDL role, in one
   transaction; later starts check the schema and run no DDL when it is current. A service that cannot reach PostgreSQL
   at start waits for it (by default up to 60 s) and then stops, so that Docker Compose (`restart`) or Kubernetes
   restarts it; wrong credentials, an unknown database, a TLS error or an unsuitable search database stop it at once.
   Details: [Start-up and schema lifecycle](operating-postgresql.html#start-up-and-schema-lifecycle).

## Environment variables

All `POSTGRES_*` variables are read by the shared client configuration (`ditto.postgresql.*`, shipped in the base JAR)
and apply to every service on PostgreSQL alike; the `POSTGRES_SEARCH_*` variables apply to the things-search service only.
Each has a HOCON key that can be set in `<service>-extension.conf` instead.

| Variable                                                                                                              | HOCON key (`ditto.postgresql.…`)                                                                                    | Default                                           | Purpose                                                                                                                                                                                                                                                                      |
|-----------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|---------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `POSTGRES_URI`                                                                                                        | `uri`                                                                                                               | `r2dbc:postgresql://localhost:5432/ditto`         | R2DBC connection URI.                                                                                                                                                                                                                                                        |
| `POSTGRES_USER`, `POSTGRES_PASSWORD`                                                                                  | `username`, `password`                                                                                              | empty                                             | Runtime role (`ditto_app` with the provisioning script). May instead be embedded in the URI.                                                                                                                                                                                 |
| `POSTGRES_DDL_USER`, `POSTGRES_DDL_PASSWORD`                                                                          | `pool.ddl-credentials.username`, `.password`                                                                        | empty (= the runtime role)                        | DDL role (`ditto_ddl` with the provisioning script): schema check at start, self-heal, per-path index builds. Mandatory with the two-role provisioning.                                                                                                                      |
| `POSTGRES_SSL_MODE`                                                                                                   | `ssl.mode`                                                                                                          | `verify-full`                                     | `disable`, `allow`, `prefer`, `require`, `verify-ca` or `verify-full` (validates the certificate chain **and** the host name). `disable` for a local plaintext PostgreSQL.                                                                                                   |
| `POSTGRES_SSL_ROOT_CERT`                                                                                              | `ssl.root-cert`                                                                                                     | empty                                             | PEM CA bundle the server certificate must chain to. **Required** with `verify-ca` and `verify-full`: the service refuses to start when it is unset (`ssl.allow-system-truststore` is `false` and has no environment variable). See [TLS](operating-postgresql.html#tls).     |
| `POSTGRES_SSL_CERT`, `POSTGRES_SSL_KEY`, `POSTGRES_SSL_KEY_PASSWORD`                                                  | `ssl.cert`, `ssl.key`, `ssl.key-password`                                                                           | empty                                             | Optional mutual TLS: PEM client certificate and **PKCS#8** private key.                                                                                                                                                                                                      |
| `POSTGRES_POOL_MAX_SIZE`                                                                                              | `pool.max-size`                                                                                                     | `25`                                              | Connections per service instance. Size all pools together against `max_connections`, see [Connection budget](operating-postgresql.html#connection-budget).                                                                                                                   |
| `POSTGRES_POOL_PENDING_ACQUIRE_MAX_SIZE`                                                                              | `pool.pending-acquire-max-size`                                                                                     | unset (= 2 × `max-size`)                          | Borrowers that may wait for a connection; beyond it a borrow fails at once.                                                                                                                                                                                                  |
| `POSTGRES_POOL_MAX_ACQUIRE_TIME`                                                                                      | `pool.max-acquire-time`                                                                                             | `4s`                                              | How long a borrow may wait for a connection. Keep it below the callers' 5 s deadline, see [Circuit breakers and overload](operating-postgresql.html#circuit-breakers-and-overload).                                                                                          |
| `POSTGRES_POOL_ACQUIRE_RETRY`                                                                                         | `pool.acquire-retry`                                                                                                | `0`                                               | Re-subscriptions of a failed borrow, each with a fresh `max-acquire-time`. Keep `0`.                                                                                                                                                                                         |
| `POSTGRES_CONNECT_TIMEOUT`                                                                                            | `connect-timeout`                                                                                                   | `2s`                                              | TCP connect timeout of a physical connection; keep it below `max-acquire-time`.                                                                                                                                                                                              |
| `POSTGRES_STATEMENT_TIMEOUT`                                                                                          | `statement-timeout`                                                                                                 | `60s`                                             | Server-side `statement_timeout`, sent as a connection startup parameter. `0` sends none, which means the role default applies, not "no timeout". See [Statement timeout and PgBouncer](operating-postgresql.html#statement-timeout-and-pgbouncer).                           |
| `POSTGRES_POOLER_MODE`                                                                                                | `pooler-mode`                                                                                                       | `direct`                                          | `direct`, `session` or `transaction`: how connections reach PostgreSQL. With `transaction`, the search service needs `POSTGRES_SEARCH_PATH_INDEXES_DIRECT_URI` to build its indexes. See [Scaling out with PgBouncer](operating-postgresql.html#scaling-out-with-pgbouncer). |
| `POSTGRES_SCHEMA_SELF_HEAL`                                                                                           | `schema.self-heal`                                                                                                  | `true`                                            | Re-create a missing table while the persistence services run. See [Schema self-heal](operating-postgresql.html#schema-self-heal).                                                                                                                                            |
| `POSTGRES_BOOTSTRAP_RETRY_ENABLED`                                                                                    | `bootstrap.retry.enabled`                                                                                           | `true`                                            | Wait for PostgreSQL at start instead of making a single attempt.                                                                                                                                                                                                             |
| `POSTGRES_BOOTSTRAP_RETRY_MAX_DURATION`                                                                               | `bootstrap.retry.max-duration`                                                                                      | `60s`                                             | Total wait for PostgreSQL at start; `0s` waits without limit. On Kubernetes keep it below the liveness budget.                                                                                                                                                               |
| `POSTGRES_BOOTSTRAP_RETRY_MIN_BACKOFF`, `POSTGRES_BOOTSTRAP_RETRY_MAX_BACKOFF`                                        | `bootstrap.retry.min-backoff`, `.max-backoff`                                                                       | `1s`, `10s`                                       | Backoff between the attempts (doubling, ±20 % jitter).                                                                                                                                                                                                                       |
| `POSTGRES_BOOTSTRAP_ATTEMPT_TIMEOUT`                                                                                  | `bootstrap.attempt-timeout`                                                                                         | `60s`                                             | Maximum duration of one schema-check attempt that did connect; running out of it is not retried.                                                                                                                                                                             |
| `POSTGRES_BOOTSTRAP_EXIT_ON_FAILURE`                                                                                  | `bootstrap.exit-on-failure`                                                                                         | `true`                                            | Stop the service (exit code 1) when the schema check finally fails, so that the orchestrator restarts it; `false` keeps the JVM running without its root actor.                                                                                                              |
| `POSTGRES_READ_JOURNAL_ENTITY`                                                                                        | `ditto.extensions.persistence-backend-provider.extension-config.read-journal.entity` (not under `ditto.postgresql`) | per service (`things`, `policies`, `connections`) | Which entity's tables the service's shared read journal reads (persistence ping, background cleanup, snapshot streaming). Set by each service's configuration; override only in a custom deployment. WoT validation config history is read from the `wot` tables regardless. |
| `POSTGRES_SEARCH_FORCE_CUSTOM_PLAN`                                                                                   | `force-custom-plan` (search)                                                                                        | `true`                                            | Sends `plan_cache_mode=force_custom_plan` on the search pool. See [Operational tunables](operating-postgresql.html#operational-tunables).                                                                                                                                    |
| `POSTGRES_SEARCH_REAPER_INTERVAL`, `POSTGRES_SEARCH_REAPER_BATCH_SIZE`, `POSTGRES_SEARCH_REAPER_MAX_BATCHES_PER_TICK` | `search.reaper.interval`, `.batch-size`, `.max-batches-per-tick`                                                    | `30s`, `1000`, `50`                               | The `delete_at` reaper that replaces MongoDB's TTL deletion. See [Operational tunables](operating-postgresql.html#operational-tunables).                                                                                                                                     |
| `POSTGRES_SEARCH_PATH_INDEXES`                                                                                        | `search.path-indexes.paths`                                                                                         | unset (not managed)                               | Per-path search indexes as a JSON array. See [Per-path search indexes](operating-postgresql.html#per-path-search-indexes-optional).                                                                                                                                          |
| `POSTGRES_SEARCH_PATH_INDEXES_DIRECT_URI`                                                                             | `search.path-indexes.direct-uri`                                                                                    | empty (= `uri`)                                   | Direct (non-PgBouncer) URI for building the search indexes; required with `pooler-mode = transaction`.                                                                                                                                                                       |

The circuit breakers of the PostgreSQL journal and snapshot-store plugins use the MongoDB plugins' environment
variables (`BREAKER_MAXTRIES`, `BREAKER_TIMEOUT`, `BREAKER_RESET`, `SNAPSHOT_BREAKER_MAXTRIES`,
`SNAPSHOT_BREAKER_TIMEOUT`, `SNAPSHOT_BREAKER_RESET`), see
[Circuit breakers and overload](operating-postgresql.html#circuit-breakers-and-overload).

## Docker Compose

With Docker Compose a service switches to PostgreSQL with `DITTO_PERSISTENCE_BACKEND=postgresql` and the `POSTGRES_*`
variables; nothing is mounted. The example below is an override file for the standard stack in `deployment/docker/`
(`docker compose -f docker-compose.yml -f docker-compose.postgres.yml up -d`). It adds a PostgreSQL 16 service with the
same database, role and password as the local example in
[`deployment/postgres-local/`](https://github.com/eclipse-ditto/ditto/tree/master/deployment/postgres-local) (database
`ditto`, user `ditto`, password `ditto`; that single role owns the database, so it doubles as the DDL role), and switches
the things service.

```yaml
# docker-compose.postgres.yml — layered on deployment/docker/docker-compose.yml
services:
  postgres:
    image: docker.io/postgres:16
    environment:
      - POSTGRES_DB=ditto
      - POSTGRES_USER=ditto
      - POSTGRES_PASSWORD=ditto
    volumes:
      - ditto-pg-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ditto -d ditto"]
      interval: 5s
      timeout: 5s
      retries: 10

  things:
    image: docker.io/eclipse/ditto-things:${DITTO_VERSION:-latest}
    # !override REPLACES the stock dependency list (compose merges lists across files otherwise), so MongoDB is
    # not started for this service.
    depends_on: !override
      postgres:
        condition: service_healthy
      policies:
        condition: service_started
    environment:
      - DITTO_PERSISTENCE_BACKEND=postgresql   # the image's /opt/ditto/backends/postgresql/ folder
      - POSTGRES_URI=r2dbc:postgresql://postgres:5432/ditto
      - POSTGRES_USER=ditto
      - POSTGRES_PASSWORD=ditto
      - POSTGRES_DDL_USER=ditto
      - POSTGRES_DDL_PASSWORD=ditto
      - POSTGRES_SSL_MODE=disable   # local plaintext PostgreSQL; in production verify-full with POSTGRES_SSL_ROOT_CERT

volumes:
  ditto-pg-data:
```

Repeat the `things` block for `policies` (depends on `postgres` only) and `connectivity` (depends on `postgres` and
`policies`). The `MONGO_DB_*` variables the stock stack sets stay harmless: the PostgreSQL provider ignores
them. The stock stack's `restart: always` restarts a service whose schema check failed (it stops with exit code 1).

Things-search on PostgreSQL sets the same variables; its `postgresql` folder holds the base plus the **search** thin
JAR:

```yaml
  things-search:
    image: docker.io/eclipse/ditto-things-search:${DITTO_VERSION:-latest}
    depends_on: !override
      postgres:
        condition: service_healthy
      policies:
        condition: service_started
      things:
        condition: service_started
    environment:
      - DITTO_PERSISTENCE_BACKEND=postgresql
      - POSTGRES_URI=r2dbc:postgresql://postgres:5432/ditto
      - POSTGRES_USER=ditto
      - POSTGRES_PASSWORD=ditto
      - POSTGRES_DDL_USER=ditto
      - POSTGRES_DDL_PASSWORD=ditto
      - POSTGRES_SSL_MODE=disable
```

If the search service stays on MongoDB, leave its definition untouched; the `mongodb` service of the stock stack is then
started for it. If no service uses MongoDB any more, remove `mongodb` from the services that are started
(`docker compose up -d` starts every defined service unless the ones you need are named).

To check that the services came up on PostgreSQL:

```bash
docker compose -f docker-compose.yml -f docker-compose.postgres.yml exec postgres psql -U ditto -d ditto -c '\dt'
#   -> things_journal, things_journal_seq, things_snaps, policies_*, connections_*, wot_*, connection_remember_*,
#      connections_encryption_migration, schema_version; with search: search_things, search_flat, search_sync
```

For running the services from the IDE or the command line without containers, `deployment/postgres-local/` provides
the PostgreSQL compose file, the dev configs and a launcher script (see its `README.md`).

## Kubernetes with Helm

With the [Helm chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm/ditto) (4.12.0 or newer),
`global.persistenceBackend: postgresql` renders `DITTO_PERSISTENCE_BACKEND=postgresql` into the policies, things,
connectivity and things-search deployments. No init container, no extra volume and no configuration overlay is needed:
the images ship the JARs. Per service:

1. **Backend.** Set the global value:

   ```yaml
   # values.yaml
   global:
     persistenceBackend: postgresql
   ```

   A mixed layout overrides the variable per service through `<service>.extraEnv`. The chart renders the global entry
   first and `extraEnv` after it, and for a duplicate name Kubernetes uses the later entry (the API server warns about
   the duplicate). For example, to keep things-search on MongoDB:

   ```yaml
   thingsSearch:
     extraEnv:
       - name: DITTO_PERSISTENCE_BACKEND
         value: "mongodb"
   ```

2. **Connection.** The `POSTGRES_*` variables via `extraEnv`:

   ```yaml
   # values.yaml
   things:
     extraEnv:
       - name: POSTGRES_URI
         value: "r2dbc:postgresql://postgres.database.svc:5432/ditto"
       - name: POSTGRES_USER
         valueFrom:
           secretKeyRef: { name: ditto-postgres, key: app-user }
       - name: POSTGRES_PASSWORD
         valueFrom:
           secretKeyRef: { name: ditto-postgres, key: app-password }
       - name: POSTGRES_DDL_USER
         valueFrom:
           secretKeyRef: { name: ditto-postgres, key: ddl-user }
       - name: POSTGRES_DDL_PASSWORD
         valueFrom:
           secretKeyRef: { name: ditto-postgres, key: ddl-password }
       - name: POSTGRES_SSL_ROOT_CERT
         value: "/etc/ditto/pg-tls/ca.crt"      # with postgres.tls.enabled (below); default mode verify-full
   ```

   The policies, connectivity and things-search deployments take the same variables in `policies.extraEnv`,
   `connectivity.extraEnv` and `thingsSearch.extraEnv`.

3. **TLS.** `postgres.tls.enabled: true` with `postgres.tls.caCert` (a PEM, or a multi-CA bundle for a staged CA
   rotation; the chart renders it into the Secret `<fullname>-postgres-tls`) or `postgres.tls.existingSecret` (keys
   `ca.crt`, plus `tls.crt`/`tls.key` for mTLS) mounts the CA, and the optional mTLS material, read-only at
   `postgres.tls.mountPath` (`/etc/ditto/pg-tls`) into the policies, things, connectivity and things-search pods. The client reads the
   files only through the `ssl.*` keys, so set `POSTGRES_SSL_ROOT_CERT=/etc/ditto/pg-tls/ca.crt` (and
   `POSTGRES_SSL_CERT` / `POSTGRES_SSL_KEY` for mTLS) via `extraEnv`. Details, CA rotation and the PKCS#8 requirement:
   [TLS](operating-postgresql.html#tls).

4. **Pool size.** `POSTGRES_POOL_MAX_SIZE` via `extraEnv` per service; the sum over the services, with the extra pods
   of a rolling update, must fit `max_connections`, see [Connection budget](operating-postgresql.html#connection-budget).

5. **Probes.** Keep `POSTGRES_BOOTSTRAP_RETRY_MAX_DURATION` (default `60s`) below the liveness budget: with the
   chart's probes (liveness from 60 s, 6 failures 10 s apart) a pod is restarted about 110 s after its container
   started, and the wait for PostgreSQL only begins once the service has joined the cluster.

Things-search on Helm works like the other services: `global.persistenceBackend` (or `thingsSearch.extraEnv`) selects
the backend, and `thingsSearch.extraEnv` carries the `POSTGRES_*` variables. `postgres.tls.enabled` mounts the CA into
the things-search pod as well.

A deployment that staged the JARs into `/opt/ditto/extensions/` with an earlier chart must remove that staging, see
[Upgrading a PostgreSQL deployment that staged JARs](installation-extending.html#upgrading-a-postgresql-deployment-that-staged-jars).

## Further reading

* [Operating PostgreSQL](operating-postgresql.html) — schema lifecycle and upgrades, self-heal, connection budget, TLS,
  PgBouncer, persistence ping, search tunables and indexes, differences from MongoDB, vacuum and disk space
* [Extending Ditto](installation-extending.html) — how extension JARs, the backend folders and `<service>-extension.conf`
  files work
* [Operating - Configuration](operating-configuration.html)
* [`deployment/postgres/README.md`](https://github.com/eclipse-ditto/ditto/blob/master/deployment/postgres/README.md) —
  the operator runbook (provisioning, TLS, CA rotation, connection budget, PgBouncer, schema upgrades)
