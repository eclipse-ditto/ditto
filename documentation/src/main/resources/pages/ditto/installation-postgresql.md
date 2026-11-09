---
title: Running Ditto on PostgreSQL
tags: [getting_started, installation]
keywords: postgresql, postgres, persistence, search, backend, docker, docker-compose, helm, kubernetes, extension, configuration, provisioning
permalink: installation-postgresql.html
---

By default, Ditto stores Things, Policies and Connectivity data in MongoDB, and the things-search service keeps its
search index in MongoDB as well. Both can run on PostgreSQL instead: the **PostgreSQL persistence backend** for the
policies, things and connectivity services, and the **PostgreSQL search backend** for the things-search service. The
two are independent of each other; each is strictly opt-in, and MongoDB remains the default.

{% include callout.html content="**TL;DR**: Mount two extension JARs (the shared base `ditto-postgres-client-extension` plus the thin `ditto-postgres-persistence-extension` or `ditto-postgres-search-extension`) into `/opt/ditto/extensions/`, add one `include required(classpath(...))` line plus the auto-start narrowing to the service's activation config, and set the `POSTGRES_*` environment variables. The schema is created at the first start." type="primary" %}

This page covers the installation: the JARs, the activation config, provisioning, the environment variables and the
Docker Compose and Helm setups. Everything that matters once the services run (schema lifecycle and upgrades,
self-heal, the connection budget, TLS, PgBouncer, monitoring, vacuum and the differences from MongoDB) is on
[Operating PostgreSQL](operating-postgresql.html).

## Overview

| Backend                        | Services                       | Thin extension JAR                     | Activation include                                          |
|--------------------------------|--------------------------------|----------------------------------------|-------------------------------------------------------------|
| PostgreSQL persistence backend | policies, things, connectivity | `ditto-postgres-persistence-extension` | `include required(classpath("ditto-postgres-persistence"))` |
| PostgreSQL search backend      | things-search                  | `ditto-postgres-search-extension`      | `include required(classpath("ditto-postgres-search"))`      |

Both backends also need the shared base JAR `ditto-postgres-client-extension` and the same `POSTGRES_*` environment
variables. Any combination works: all three persistence services on PostgreSQL with search on MongoDB, search alone on
PostgreSQL, or everything on PostgreSQL. A service left on MongoDB mounts nothing and changes nothing.

**PostgreSQL version:** PostgreSQL 16 or newer (tested: 16, 17, 18). Older versions log a warning at startup, but the
service still starts; technically, persistence needs PostgreSQL ≥ 10 and search PostgreSQL ≥ 13 (from 13 on, `pg_trgm`
is a trusted extension, see [Prerequisites of the search backend](operating-postgresql.html#prerequisites-migration-and-known-limitations)).

**Database encoding and locale (search):** the search database must be UTF8-encoded, with a UTF-8-based `LC_CTYPE`
(e.g. `C.UTF-8` or `en_US.UTF-8`) and the libc collation `C.utf8`. The search service checks this at every start,
before it touches the schema, and refuses to start otherwise; CloudNativePG's `initdb` default `LC_CTYPE` `C` does not
qualify. See [Provisioning the database](#provisioning-the-database).

{% include note.html content="Data migration from MongoDB is the operator's responsibility. No automated migration tooling is provided for the persistence services. The search index is a rebuildable projection of the things data: point the search service at an empty PostgreSQL database and let background sync re-index, see [Migration = re-index only](operating-postgresql.html#prerequisites-migration-and-known-limitations)." %}

## The extension JARs

The extension JARs are **not** included in the default Ditto images. The PostgreSQL backends ship as **layered drop-in
JARs** built from three Maven modules — a shared **base** JAR carrying all third-party runtime (R2DBC driver/pool,
reactor, netty, scram) and **thin** backend JARs carrying only Ditto implementation classes:

| Module / JAR                                  | Contents                                                                                                                  | Mounted by                                        |
|-----------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------|
| `ditto-postgres-client-extension` (base)      | shared `postgres-client` infra + ALL third-party (r2dbc-postgresql, r2dbc-pool, reactor, netty incl. resolver-dns, scram) | every service on PostgreSQL                       |
| `ditto-postgres-persistence-extension` (thin) | only the event-sourcing persistence classes                                                                               | policies / things / connectivity                  |
| `ditto-postgres-search-extension` (thin)      | only the thing-search classes                                                                                             | things-search (only if search runs on PostgreSQL) |

A service therefore mounts **two** JARs: the base plus the matching thin JAR. A search service left on MongoDB mounts
nothing.

The JARs go into `/opt/ditto/extensions/` of the service container. The Ditto images put `/opt/ditto/*` and
`/opt/ditto/extensions/*` on the classpath (see [Adding JARs to the classpath](installation-extending.html#adding-jars-to-the-classpath)).
The release images contain no `extensions` directory and run as the unprivileged `ditto` user, so provide the JARs as
a volume mount (Docker Compose) or stage them with an init container (Helm), as shown below, rather than copying them
into a running container.

### Where the JARs come from

The three JARs are Maven modules of the Ditto build, under `internal/utils/` in the Ditto repository, with the
artifact coordinates `org.eclipse.ditto:ditto-postgres-client-extension`,
`org.eclipse.ditto:ditto-postgres-persistence-extension` and `org.eclipse.ditto:ditto-postgres-search-extension`. Each
is a shaded JAR; the thin ones ride on top of the base. To build them from the sources (from the Ditto repository
root):

```bash
# the base JAR plus both thin JARs; -Drevision sets the version marker (see the same-version rule below)
mvn -pl :ditto-postgres-client-extension,:ditto-postgres-persistence-extension,:ditto-postgres-search-extension \
    -am -DskipTests -DskipITs package -Drevision=<ditto version>

ls internal/utils/postgres-client-extension/target/ditto-postgres-client-extension-<ditto version>.jar \
   internal/utils/postgres-persistence-extension/target/ditto-postgres-persistence-extension-<ditto version>.jar \
   internal/utils/postgres-search-extension/target/ditto-postgres-search-extension-<ditto version>.jar
```

For images built from the sources with `build-images.sh` (snapshot Dockerfiles), `BAKE_POSTGRES_EXTENSIONS=true`
copies the JARs of the same build into the things, policies, connectivity and things-search images under
`/opt/ditto/extensions/`, so nothing has to be mounted:

```bash
mvn install -DskipTests -DskipITs
BAKE_POSTGRES_EXTENSIONS=true ./build-images.sh
```

### The same-version rule

All mounted PostgreSQL extension JARs **must come from the same Ditto release as the service image**. Every extension
JAR carries a version marker (`META-INF/ditto-postgres-extension.properties`, the Maven `revision` it was built with),
and the provider compares the markers of all mounted extension JARs at start: JARs from different versions fail the
start with an `IllegalStateException` naming each component and version (`The mounted Postgres extension jars are from
DIFFERENT Ditto releases: …`). The check compares the extension JARs with each other only, not with the image, and it
passes quietly when fewer than two markers are found. The release images are built from the tagged sources with
`-Drevision=<release tag>`, so build the extension JARs from the same tag with the same `-Drevision`, or take them from
the same release; a build without `-Drevision` produces `0-SNAPSHOT` JARs, which pass the check against each other
whatever image they are mounted into.

### A thin JAR without its base

A thin JAR mounted without the base JAR fails the start of the service with

```
java.lang.NoClassDefFoundError: org/eclipse/ditto/internal/utils/persistence/postgres/client/config/PostgresConfig
```

(or another class of the `…persistence.postgres.client` package): the provider class links against types of the base
JAR before any check of its own can run. The fix is to mount `ditto-postgres-client-extension` from the same release
next to the thin JAR. The reverse (the base JAR alone) is harmless: without a thin JAR the activation include below
fails at config load with `ConfigException$IO: … resource not found on classpath: ditto-postgres-persistence.conf`
(`ditto-postgres-search.conf` for search).

## The activation config

One include activates a backend. Each thin JAR ships a profile — `ditto-postgres-persistence.conf` for the persistence
backend, `ditto-postgres-search.conf` for the search backend — that swaps the provider extension (`ditto.extensions.persistence-backend-provider`
or `ditto.extensions.search-persistence-provider`) to the PostgreSQL implementation, defines the Pekko plugin blocks
(persistence only) and pulls in the shared `ditto.postgresql.*` client, pool and SSL defaults from the base JAR
(`ditto-postgres-client.conf`). Nothing else has to be changed in the service configuration: the provider key is set
inside the profile, and the snapshot encoding switches automatically when the PostgreSQL backend is active, so no
snapshot-adapter override is needed.

Three rules apply to the include:

* **Top level.** The include statement goes **outside any `ditto {}` block**, because `ditto-postgres-persistence.conf`
  ships top-level Pekko plugin blocks (`ditto-postgres-things-journal { }`, …) that Pekko must see at the config root.
  The search profile has no such blocks, but is included at the top level too.
* **`required(classpath(...))`.** A plain `include classpath(...)` of a resource that is missing is silently ignored:
  a service whose image lacks the thin JAR would then start with the MongoDB provider (search) or with auto-start
  lists naming plugins that do not exist (persistence). `include required(classpath("…"))` makes such a service fail
  at config load instead, with `ConfigException$IO: … resource not found on classpath: ditto-postgres-persistence.conf`.
* **Narrow the auto-start lists (persistence services only).** The persistence profile lists the plugins of all four
  entities (things, policies, connections, wot) in Pekko's `auto-start-*` lists. Each Ditto service defines only its own
  entity's persistence dispatchers, and Pekko resolves every auto-started plugin's dispatcher eagerly at
  Persistence-extension init, so an un-narrowed list crashes the first persistent actor on a missing dispatcher for the
  other entities. Every persistence service therefore sets the two lists to its own plugins (table below). The things
  service keeps only the `things` pair: its WoT plugins (`ditto-postgres-wot-*`) start lazily on the same dispatchers
  and must not be auto-started. The search service has no event-sourcing journal and nothing to narrow.

### Per-service names

| Service       | Thin JAR                               | Activation config (Compose)              | Journal plugin to auto-start         | Snapshot store to auto-start           | Read-journal entity (`read-journal.entity`) |
|---------------|----------------------------------------|------------------------------------------|--------------------------------------|----------------------------------------|---------------------------------------------|
| policies      | `ditto-postgres-persistence-extension` | `/opt/ditto/policies-extension.conf`     | `ditto-postgres-policies-journal`    | `ditto-postgres-policies-snapshots`    | `policies`                                  |
| things        | `ditto-postgres-persistence-extension` | `/opt/ditto/things-extension.conf`       | `ditto-postgres-things-journal`      | `ditto-postgres-things-snapshots`      | `things`                                    |
| connectivity  | `ditto-postgres-persistence-extension` | `/opt/ditto/connectivity-extension.conf` | `ditto-postgres-connections-journal` | `ditto-postgres-connections-snapshots` | `connections`                               |
| things-search | `ditto-postgres-search-extension`      | `/opt/ditto/search-extension.conf`       | — (no narrowing)                     | —                                      | —                                           |

The read-journal entity is set by each service's own configuration (`things.conf`, `policies.conf`,
`connectivity.conf`); `POSTGRES_READ_JOURNAL_ENTITY` overrides it, which a standard deployment never needs.

### The four activation configs

The same content serves as the per-service extension config in Docker Compose (mounted at
`/opt/ditto/<service>-extension.conf`, which every service config includes last) and as the file-based overlay with
Helm (see [Kubernetes with Helm](#kubernetes-with-helm)). It contains **no** `include` of the service's own
configuration (`things`, `things-dev`, …): the service configuration is always loaded, and a `*-dev` profile would
bring local-development cluster settings into the deployment.

```hocon
# things-extension.conf
# TOP-LEVEL include, NOT inside ditto {}: the profile ships top-level Pekko plugin blocks.
include required(classpath("ditto-postgres-persistence"))

# Narrow the auto-start lists to this service's own plugins (REQUIRED for every persistence service).
pekko.persistence.journal.auto-start-journals               = [ "ditto-postgres-things-journal" ]
pekko.persistence.snapshot-store.auto-start-snapshot-stores = [ "ditto-postgres-things-snapshots" ]
```

```hocon
# policies-extension.conf
include required(classpath("ditto-postgres-persistence"))

pekko.persistence.journal.auto-start-journals               = [ "ditto-postgres-policies-journal" ]
pekko.persistence.snapshot-store.auto-start-snapshot-stores = [ "ditto-postgres-policies-snapshots" ]
```

```hocon
# connectivity-extension.conf
include required(classpath("ditto-postgres-persistence"))

pekko.persistence.journal.auto-start-journals               = [ "ditto-postgres-connections-journal" ]
pekko.persistence.snapshot-store.auto-start-snapshot-stores = [ "ditto-postgres-connections-snapshots" ]
```

```hocon
# search-extension.conf
# Swaps ditto.extensions.search-persistence-provider to the PostgreSQL provider and pulls in the shared
# ditto.postgresql.* client/pool/SSL defaults. search.conf's own ditto.mongodb defaults legitimately remain
# present (the profile only swaps the one extension key). Nothing to narrow: search runs no event-sourcing journal.
include required(classpath("ditto-postgres-search"))
```

The persistence profile sets the provider as follows; you do not need to repeat it unless you want to override it:

```hocon
ditto.extensions.persistence-backend-provider {
  extension-class = org.eclipse.ditto.internal.utils.persistence.postgres.PostgresPersistenceBackendProvider
}
```

Any other setting of these pages, for example `ditto.postgresql.pool.max-size` or the per-path search indexes, can be
added to the same file instead of being set through an environment variable.

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
   psql -h <pg-host> -U <database-owner> -d ditto \
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
Each has a HOCON key that can be set in the activation config instead.

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
| `POSTGRES_READ_JOURNAL_ENTITY`                                                                                        | `ditto.extensions.persistence-backend-provider.extension-config.read-journal.entity` (not under `ditto.postgresql`) | per service (`things`, `policies`, `connections`) | Which entity's tables the service's read journal reads. Set by each service's configuration; override only in a custom deployment.                                                                                                                                           |
| `POSTGRES_SEARCH_FORCE_CUSTOM_PLAN`                                                                                   | `force-custom-plan` (search)                                                                                        | `true`                                            | Sends `plan_cache_mode=force_custom_plan` on the search pool. See [Operational tunables](operating-postgresql.html#operational-tunables).                                                                                                                                    |
| `POSTGRES_SEARCH_REAPER_INTERVAL`, `POSTGRES_SEARCH_REAPER_BATCH_SIZE`, `POSTGRES_SEARCH_REAPER_MAX_BATCHES_PER_TICK` | `search.reaper.interval`, `.batch-size`, `.max-batches-per-tick`                                                    | `30s`, `1000`, `50`                               | The `delete_at` reaper that replaces MongoDB's TTL deletion. See [Operational tunables](operating-postgresql.html#operational-tunables).                                                                                                                                     |
| `POSTGRES_SEARCH_PATH_INDEXES`                                                                                        | `search.path-indexes.paths`                                                                                         | unset (not managed)                               | Per-path search indexes as a JSON array. See [Per-path search indexes](operating-postgresql.html#per-path-search-indexes-optional).                                                                                                                                          |
| `POSTGRES_SEARCH_PATH_INDEXES_DIRECT_URI`                                                                             | `search.path-indexes.direct-uri`                                                                                    | empty (= `uri`)                                   | Direct (non-PgBouncer) URI for building the search indexes; required with `pooler-mode = transaction`.                                                                                                                                                                       |

The circuit breakers of the PostgreSQL journal and snapshot-store plugins use the MongoDB plugins' environment
variables (`BREAKER_MAXTRIES`, `BREAKER_TIMEOUT`, `BREAKER_RESET`, `SNAPSHOT_BREAKER_MAXTRIES`,
`SNAPSHOT_BREAKER_TIMEOUT`, `SNAPSHOT_BREAKER_RESET`), see
[Circuit breakers and overload](operating-postgresql.html#circuit-breakers-and-overload). With Helm, two more
variables select the file-based activation, see below: `HOSTING_ENVIRONMENT=filebased` and
`HOSTING_ENVIRONMENT_FILE_LOCATION=<path of the overlay inside the container>`.

## Docker Compose

With Docker Compose the activation config is mounted as the service's extension config at
`/opt/ditto/<service>-extension.conf` (every service configuration includes that file last, see
[Adjusting service configuration](installation-extending.html#adjusting-service-configuration)); no hosting-environment
change is needed. The example below is an override file for the standard stack in `deployment/docker/`
(`docker compose -f docker-compose.yml -f docker-compose.postgres.yml up -d`). It adds a PostgreSQL 16 service with the
same database, role and password as the local example in
[`deployment/postgres-local/`](https://github.com/eclipse-ditto/ditto/tree/master/deployment/postgres-local) (database
`ditto`, user `ditto`, password `ditto`; that single role owns the database, so it doubles as the DDL role), and switches
the things service. The four activation configs and the JARs lie next to `docker-compose.yml` in `deployment/docker/` (Compose resolves
relative paths against the directory of the first `-f` file).

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
      - POSTGRES_URI=r2dbc:postgresql://postgres:5432/ditto
      - POSTGRES_USER=ditto
      - POSTGRES_PASSWORD=ditto
      - POSTGRES_DDL_USER=ditto
      - POSTGRES_DDL_PASSWORD=ditto
      - POSTGRES_SSL_MODE=disable   # local plaintext PostgreSQL; in production verify-full with POSTGRES_SSL_ROOT_CERT
    volumes:
      # 1. Drop-in extension JARs (NOT in the default image; base + persistence thin JAR, same release as the image)
      - ./ditto-postgres-client-extension.jar:/opt/ditto/extensions/ditto-postgres-client-extension.jar:ro
      - ./ditto-postgres-persistence-extension.jar:/opt/ditto/extensions/ditto-postgres-persistence-extension.jar:ro
      # 2. Activation config = the service's extension config
      - ./things-extension.conf:/opt/ditto/things-extension.conf:ro

volumes:
  ditto-pg-data:
```

Repeat the `things` block for `policies` (depends on `postgres` only) and `connectivity` (depends on `postgres` and
`policies`), with the respective `<service>-extension.conf` from [The four activation configs](#the-four-activation-configs)
and the same two JARs. The `MONGO_DB_*` variables the stock stack sets stay harmless: the PostgreSQL provider ignores
them. The stock stack's `restart: always` restarts a service whose schema check failed (it stops with exit code 1).

Things-search on PostgreSQL mounts the base plus the **search** thin JAR and the `search-extension.conf`:

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
      - POSTGRES_URI=r2dbc:postgresql://postgres:5432/ditto
      - POSTGRES_USER=ditto
      - POSTGRES_PASSWORD=ditto
      - POSTGRES_DDL_USER=ditto
      - POSTGRES_DDL_PASSWORD=ditto
      - POSTGRES_SSL_MODE=disable
    volumes:
      # base + search thin JAR (both from the same Ditto release as the image)
      - ./ditto-postgres-client-extension.jar:/opt/ditto/extensions/ditto-postgres-client-extension.jar:ro
      - ./ditto-postgres-search-extension.jar:/opt/ditto/extensions/ditto-postgres-search-extension.jar:ro
      - ./search-extension.conf:/opt/ditto/search-extension.conf:ro
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
the PostgreSQL compose file, the overlay configs and a launcher script (see its `README.md`).

## Kubernetes with Helm

The [Helm chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm/ditto) owns
`/opt/ditto/<service>-extension.conf` (it renders the file from a ConfigMap of its own), so the activation config is
injected as a **file-based overlay** instead: with `HOSTING_ENVIRONMENT=filebased`, the service reads the file named by
`HOSTING_ENVIRONMENT_FILE_LOCATION` as a configuration layer above its own configuration (only system properties rank
higher). The content of the overlay is the same as the extension config above. Per service:

1. **Overlay ConfigMap**, mounted via `extraVolumes` / `extraVolumeMounts`, and selected with `extraEnv`:

   ```yaml
   # a ConfigMap with the activation config, created alongside the release
   apiVersion: v1
   kind: ConfigMap
   metadata:
     name: ditto-things-postgres
   data:
     things-postgres.conf: |
       include required(classpath("ditto-postgres-persistence"))
       pekko.persistence.journal.auto-start-journals               = [ "ditto-postgres-things-journal" ]
       pekko.persistence.snapshot-store.auto-start-snapshot-stores = [ "ditto-postgres-things-snapshots" ]
   ```

   ```yaml
   # values.yaml
   things:
     extraEnv:
       - name: HOSTING_ENVIRONMENT
         value: "filebased"
       - name: HOSTING_ENVIRONMENT_FILE_LOCATION
         value: "/opt/ditto/things-postgres.conf"
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
     extraVolumes:
       - name: things-postgres-overlay
         configMap:
           name: ditto-things-postgres
       - name: ditto-extensions
         emptyDir: {}
     extraVolumeMounts:
       - name: things-postgres-overlay
         mountPath: /opt/ditto/things-postgres.conf
         subPath: things-postgres.conf
       - name: ditto-extensions
         mountPath: /opt/ditto/extensions
     extraInitContainers:
       - name: stage-postgres-extension
         image: curlimages/curl:8.6.0
         command:
           - sh
           - -c
           - |
             curl -fL -o /extensions/ditto-postgres-client-extension.jar \
               "https://your-artifact-host/path/to/ditto-postgres-client-extension-<version>.jar"
             curl -fL -o /extensions/ditto-postgres-persistence-extension.jar \
               "https://your-artifact-host/path/to/ditto-postgres-persistence-extension-<version>.jar"
         volumeMounts:
           - name: ditto-extensions
             mountPath: /extensions
   ```

   The chart itself never sets `HOSTING_ENVIRONMENT`; without the overlay the services run in the production
   environment, whose environment layer is empty, so the overlay replaces nothing of the chart's configuration.

2. **Extension JARs** are staged into the shared `ditto-extensions` volume by the init container above (the
   `extraVolumes` / `extraVolumeMounts` / `extraInitContainers` examples in `values.yaml` show the same), from an
   artifact host of yours or from an image that carries them. The policies and connectivity deployments take the same
   two JARs; a search deployment takes `ditto-postgres-client-extension` plus `ditto-postgres-search-extension`.

3. **TLS.** `postgres.tls.enabled: true` with `postgres.tls.caCert` (a PEM, or a multi-CA bundle for a staged CA
   rotation) or `postgres.tls.existingSecret` mounts the CA, and the optional mTLS material, read-only at
   `postgres.tls.mountPath` (`/etc/ditto/pg-tls`) into the policies, things, connectivity and things-search pods. The client reads the
   files only through the `ssl.*` keys, so set `POSTGRES_SSL_ROOT_CERT=/etc/ditto/pg-tls/ca.crt` (and
   `POSTGRES_SSL_CERT` / `POSTGRES_SSL_KEY` for mTLS) via `extraEnv`. Details, CA rotation and the PKCS#8 requirement:
   [TLS](operating-postgresql.html#tls).

4. **Pool size.** `POSTGRES_POOL_MAX_SIZE` via `extraEnv` per service; the sum over the services, with the extra pods
   of a rolling update, must fit `max_connections`, see [Connection budget](operating-postgresql.html#connection-budget).

5. **Probes.** Keep `POSTGRES_BOOTSTRAP_RETRY_MAX_DURATION` (default `60s`) below the liveness budget: with the
   chart's probes (liveness from 60 s, 6 failures 10 s apart) a pod is restarted about 110 s after its container
   started, and the wait for PostgreSQL only begins once the service has joined the cluster.

Things-search on Helm works like the other services: the overlay ConfigMap (`search-postgres.conf` with the single
`include required(classpath("ditto-postgres-search"))`), `thingsSearch.extraEnv` with `HOSTING_ENVIRONMENT=filebased`,
`HOSTING_ENVIRONMENT_FILE_LOCATION=/opt/ditto/search-postgres.conf` and the `POSTGRES_*` variables,
`thingsSearch.extraVolumes` / `extraVolumeMounts` for the overlay and the `ditto-extensions` volume, and
`thingsSearch.extraInitContainers` staging `ditto-postgres-client-extension` plus `ditto-postgres-search-extension`
(`values.yaml` carries the example; chart 4.11.0 or newer). `postgres.tls.enabled` mounts the CA into the
things-search pod as well.

Chart-native `postgres.*` values that render the activation themselves are not part of this version.

## Further reading

* [Operating PostgreSQL](operating-postgresql.html) — schema lifecycle and upgrades, self-heal, connection budget, TLS,
  PgBouncer, persistence ping, search tunables and indexes, differences from MongoDB, vacuum and disk space
* [Extending Ditto](installation-extending.html) — how extension JARs and `<service>-extension.conf` files work
* [Operating - Configuration](operating-configuration.html)
* [`deployment/postgres/README.md`](https://github.com/eclipse-ditto/ditto/blob/master/deployment/postgres/README.md) —
  the operator runbook (provisioning, TLS, CA rotation, connection budget, PgBouncer, schema upgrades)
