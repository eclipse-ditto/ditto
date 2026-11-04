---
title: Extending Ditto
tags: [getting_started, installation]
keywords: running, start, run, docker, docker-compose, extension, custom, configuration, logging
permalink: installation-extending.html
---

You extend Ditto by implementing custom `DittoExtensionPoint` interfaces and loading them into the service via Pekko's classloader.

{% include callout.html content="**TL;DR**: Create a Java class implementing a `DittoExtensionPoint` interface, configure it in a `<service>-extension.conf` file, and add the JAR to `/opt/ditto/extensions` in the Docker container." type="primary" %}

## Overview

Ditto provides extension points throughout its codebase, marked by interfaces that extend `DittoExtensionPoint`. You can replace the default behavior at these points by providing your own implementation.

## How it works

### Creating an extension

Your implementation needs a public constructor that accepts an `ActorSystem` and `Config` parameter, which Pekko's classloader uses for reflection-based instantiation:

```java
public CustomExtension(final ActorSystem actorSystem, final Config config) {}
```

### Configuring an extension

Tell Pekko's classloader which implementation to use by adding the extension's `CONFIG_KEY` to:
* A `<service-name>-extension.conf` file for service-specific extensions
* The `reference.conf` for a global scope

Each extension configuration has two parts:
* `extension-class`: The fully qualified class name of your implementation
* `extension-config`: Custom configuration for the extension (optional)

```hocon
ditto.extensions.signal-enrichment-provider {
  extension-class = org.eclipse.ditto.gateway.service.endpoints.utils.DefaultGatewaySignalEnrichmentProvider
  extension-config = {
    cache {
      enabled = true
      maximum-size = 20000
      expire-after-create = 2m
    }
  }
}
```

If your extension needs no custom configuration, use the shorthand form:

```hocon
ditto.extensions.signal-enrichment-provider = org.eclipse.ditto.gateway.service.endpoints.utils.DefaultGatewaySignalEnrichmentProvider
```

## Configuration

### Adjusting service configuration

For simple configuration changes, use [system properties](operating-configuration.html). For extensive changes, create a [HOCON](https://github.com/lightbend/config/blob/main/HOCON.md) file named `<ditto-service-name>-extension.conf` and place it in the Docker container's working directory:

| Service | Extension config path |
|---------|----------------------|
| Policies | `/opt/ditto/policies-extension.conf` |
| Things | `/opt/ditto/things-extension.conf` |
| Search | `/opt/ditto/search-extension.conf` |
| Connectivity | `/opt/ditto/connectivity-extension.conf` |
| Gateway | `/opt/ditto/gateway-extension.conf` |

These files can contain any configuration from the [service config files](operating-configuration.html).

For example, the [gateway.conf](https://github.com/eclipse-ditto/ditto/blob/master/gateway/service/src/main/resources/gateway.conf)
contains the following health-check configuration:

```hocon
ditto {
  gateway {
    health-check {
      cluster-roles = {
        enabled = true
        enabled = ${?HEALTH_CHECK_ROLES_ENABLED}

        expected = [
          "policies",
          "things",
          "search",
          "gateway",
          "connectivity"
        ]
      }
    }
  }
}
```

To remove the "connectivity" role from the health check (e.g. when not starting `ditto-connectivity`
at all), create a `gateway-extension.conf` with:

```hocon
ditto.gateway.health-check.cluster-roles = {
  expected = [
    "policies",
    "things",
    "search",
    "gateway"
  ]
}
```

Then mount the file into the container at `/opt/ditto/gateway-extension.conf`.

### Adding JARs to the classpath

Ditto Docker images automatically add all JARs from these directories to the classpath:

* `/opt/ditto`
* `/opt/ditto/extensions`

Build your extension as a JAR (including extension classes and config files) and place it in the `extensions` directory.

## Examples

### Custom API routes

1. Create a new implementation of `CustomApiRoutesProvider`, overriding the `unauthorized(*)` and `authorized(*)` functions to return custom HTTP API routes.
2. Build the project into a `gateway-extension.jar`.
3. Add the JAR to the container:
   ```bash
   docker cp gateway-extension.jar container_id:/opt/ditto/extensions/
   ```
4. Create a `gateway-extension.conf`:
   ```hocon
   ditto.extensions.custom-api-routes-provider = org.company.project.gateway.service.endpoints.utils.MyCustomApiRoutesProvider
   ```
5. Add the config to the container:
   ```bash
   docker cp gateway-extension.conf container_id:/opt/ditto/
   ```

Alternatively, mount both files via docker-compose:

```yaml
connectivity:
  image: docker.io/eclipse/ditto-gateway:${DITTO_VERSION:-latest}
  environment:
    - TZ=Europe/Berlin
    - JAVA_TOOL_OPTIONS=-Dlogback.configurationFile=/opt/ditto/logback.xml
  volumes:
    - ./gateway-extension.conf:/opt/ditto/gateway-extension.conf
    - ./logback.xml:/opt/ditto/logback.xml
    - ./gateway-extension.jar:/opt/ditto/extensions/gateway-extension.jar
```

### PostgreSQL persistence backend

By default, Ditto stores Things, Policies, and Connectivity data in MongoDB.
You can switch any service to a PostgreSQL backend by dropping in the extension JARs and mounting a service-specific activation config.
MongoDB remains the default — this is strictly opt-in.

**PostgreSQL version:** PostgreSQL 16 or newer (tested: 16, 17, 18). Older versions log a warning at startup, but the service still starts; technically, persistence needs PostgreSQL ≥ 10 and search PostgreSQL ≥ 13.

**Operations:** for vacuum, table bloat, monitoring and reclaiming disk space after cleanup, see [Operating PostgreSQL](#operating-postgresql).

#### 1. Provide the extension JARs

The extension JARs are **not** included in the default Ditto images.
The PostgreSQL backend ships as **layered drop-in JARs** built from three Maven modules — a shared **base** JAR carrying all third-party runtime (R2DBC driver/pool, reactor, netty, scram) and **thin** backend JARs carrying only Ditto implementation classes:

| Module / JAR | Contents | Mounted by |
|---|---|---|
| `ditto-postgres-client-extension` (base) | shared `postgres-client` infra + ALL third-party (r2dbc-postgresql, r2dbc-pool, reactor, netty incl. resolver-dns, scram) | every Postgres service |
| `ditto-postgres-persistence-extension` (thin) | only the event-sourcing persistence classes | things / policies / connectivity |
| `ditto-postgres-search-extension` (thin) | only the thing-search classes | thing-search (only if search runs on Postgres — see the search section below) |

A persistence service therefore mounts **two** JARs: the base plus the matching thin JAR. Build them (from the ditto repo root) and drop them into the extensions directory:

```bash
# build the base + the persistence thin JAR (each is a shaded JAR; the thin one rides on top of the base)
mvn -pl :ditto-postgres-client-extension,:ditto-postgres-persistence-extension -am -DskipTests package

# copy BOTH into the container (or use a volume mount — see docker-compose example below)
docker cp \
  internal/utils/postgres-client-extension/target/ditto-postgres-client-extension-<version>.jar \
  container_id:/opt/ditto/extensions/
docker cp \
  internal/utils/postgres-persistence-extension/target/ditto-postgres-persistence-extension-<version>.jar \
  container_id:/opt/ditto/extensions/
```

{% include note.html content="All mounted PostgreSQL extension JARs **must come from the same Ditto release**. A boot self-check (a version marker in each JAR) fails fast on a version mismatch, and a thin JAR mounted without its base fails fast with an actionable *requires base `ditto-postgres-client-extension`* error." %}

#### 2. Create a service activation config

Create a HOCON overlay file (e.g. `things-postgres.conf`) for each service you want to migrate.
The top-level `include` statement is **required outside any `ditto {}` block** because the Postgres persistence config ships its own top-level Pekko plugin blocks that Pekko must see at the config root.

```hocon
# things-postgres.conf
# Injected via: HOSTING_ENVIRONMENT=filebased
#               HOSTING_ENVIRONMENT_FILE_LOCATION=/opt/ditto/things-postgres.conf

# 1. Re-include the service base settings (filebased replaces the normal environment layer)
include classpath("things-dev")

# 2. TOP-LEVEL include — NOT inside ditto {} — because ditto-postgres-persistence.conf ships its
#    own top-level Pekko plugin blocks (ditto-postgres-things-journal { }, ...) that Pekko must
#    see at the config root. This single include wires the provider selection, the per-entity
#    Pekko plugin blocks, AND the ditto.postgresql.* client/pool/SSL defaults (the client config
#    used to live in a separate ditto-postgresql.conf; it is now folded into this one file).
include classpath("ditto-postgres-persistence")

# Narrow Pekko's auto-start lists to this service's own entity. ditto-postgres-persistence.conf
# defaults these lists to all 4 entities (things/policies/connections/wot), which only boots where
# every entity's plugin-dispatcher is defined (an all-in-one config). A single-entity service such
# as things only defines the thing-* dispatchers, and Pekko resolves every auto-started plugin's
# dispatcher eagerly at Persistence-extension init — an un-narrowed list crashes the first
# persistent actor on a missing dispatcher for the other entities. This narrowing step is REQUIRED
# for every service overlay, not optional.
pekko.persistence.journal.auto-start-journals               = [ "ditto-postgres-things-journal" ]
pekko.persistence.snapshot-store.auto-start-snapshot-stores = [ "ditto-postgres-things-snapshots" ]
```

This overlay also activates the Postgres persistence backend provider via:

```hocon
ditto.extensions.persistence-backend-provider {
  extension-class = org.eclipse.ditto.internal.utils.persistence.postgres.PostgresPersistenceBackendProvider
}
```

That key is already set inside `ditto-postgres-persistence.conf` (shipped inside the extension JAR); you do not need to repeat it unless you want to override it.

Snapshot encoding switches automatically when the Postgres backend is active — no per-service snapshot adapter override is needed.

#### 3. Set environment variables

| Variable | Purpose | Example value |
|---|---|---|
| `HOSTING_ENVIRONMENT` | Switches Ditto's config loader to file-based mode | `filebased` |
| `HOSTING_ENVIRONMENT_FILE_LOCATION` | Path to the overlay config inside the container | `/opt/ditto/things-postgres.conf` |
| `POSTGRES_URI` | R2DBC connection URI | `r2dbc:postgresql://postgres:5432/ditto` |
| `POSTGRES_USER` | Database user | `ditto` |
| `POSTGRES_PASSWORD` | Database password | `ditto` |
| `POSTGRES_SSL_MODE` | TLS mode (`disable`, `require`, `verify-full`) | `disable` (dev) / `verify-full` (prod) |
| `POSTGRES_POOL_MAX_SIZE` | Connections per service instance (default `25`), see **Connection budget** below | `25` |

Schema tables are created automatically on first boot.

**Schema check at every start.** Each PostgreSQL-backed service checks its schema at start, in one transaction. When the stored schema version and checksum match the code and every table and index of the schema exists, it runs no DDL, so it takes no table lock that writes or index builds conflict with. Otherwise (first start, a schema upgrade, or a missing table or index) it runs the schema DDL, which re-creates what is missing; each statement then waits at most 500 ms for a table lock (`lock_timeout`). A start that meets a conflicting lock — for example a running `CREATE INDEX CONCURRENTLY`, a long transaction or an autovacuum of that table — fails after about half a second without changing anything and is retried at the next start, instead of stalling writes. As a consequence, a manually dropped table or index is re-created at the next start, but a table storage parameter changed by hand (for example `ALTER TABLE … RESET (autovacuum_vacuum_scale_factor)`) is not re-applied until the next schema version upgrade or a [self-heal](#schema-self-heal) that runs the DDL; set it back by hand if needed (see [Autovacuum settings applied by the schema](#autovacuum-settings-applied-by-the-schema)).

**Waiting for PostgreSQL at start.** A PostgreSQL-backed service (policies, things, connectivity, and things-search on PostgreSQL) that cannot reach PostgreSQL when it starts retries its schema check with backoff (1 s doubling to 10 s, ±20 % jitter, one WARN per attempt) for up to `ditto.postgresql.bootstrap.retry.max-duration` (`POSTGRES_BOOTSTRAP_RETRY_MAX_DURATION`, default `60s`; `0s` waits without limit). It retries only when the connection is refused, times out or is reset, the host name does not resolve yet, or the server answers that it is starting up or shutting down (SQLSTATE `57P03`, `57P01`, `57P02`) or has no free connection slot (`53300`). Everything else fails the start at once: schema drift, an incompatible schema version or an unsuitable search database, TLS and certificate errors, wrong credentials or a missing role (`28000`, `28P01`), an unknown database (`3D000`), PgBouncer's own errors (`08P01`), a DDL lock timeout (`55P03`), and an attempt that connects but does not finish within `ditto.postgresql.bootstrap.attempt-timeout` (`POSTGRES_BOOTSTRAP_ATTEMPT_TIMEOUT`, default `60s`): usually another session holds the schema advisory lock, and the error names the `pg_locks` query that shows it; behind PgBouncer a PostgreSQL that is down looks the same. When the start finally fails, the service stops (coordinated shutdown, exit code 1), so that Docker Compose (`restart`) or Kubernetes restarts it instead of leaving it running without its root actor; `ditto.postgresql.bootstrap.exit-on-failure = false` (`POSTGRES_BOOTSTRAP_EXIT_ON_FAILURE`) keeps the JVM running. A shutdown (SIGTERM) during the wait ends it at once. `ditto.postgresql.bootstrap.retry.enabled = false` (`POSTGRES_BOOTSTRAP_RETRY_ENABLED`) makes a single attempt. On Kubernetes, keep `max-duration` below the liveness budget: with the Helm chart's probes (liveness from 60 s, 6 failures 10 s apart) a pod is restarted about 110 s after its container started, and the wait only begins once the service has joined the cluster. The wait covers the start only; an outage while the service runs is handled as before.

**Statement timeout and PgBouncer.** `ditto.postgresql.statement-timeout` (`POSTGRES_STATEMENT_TIMEOUT`, default `60s`) is sent as a connection startup parameter; things-search also sends `plan_cache_mode` (`ditto.postgresql.force-custom-plan`, on by default for search). PgBouncer, in session and transaction mode, refuses the connection unless each such parameter is listed in `track_extra_parameters` (its value then applies) or in `ignore_startup_parameters` (its value is then dropped). **Use `track_extra_parameters`:** PgBouncer then applies Ditto's values to the server connection it hands out, as on a direct connection, while `ignore_startup_parameters` drops them silently. The driver also always sends `extra_float_digits`, which must be listed in `ignore_startup_parameters` in either case. So configure PgBouncer with `ignore_startup_parameters = extra_float_digits` and `track_extra_parameters = statement_timeout,plan_cache_mode`. This was verified with PgBouncer 1.26.0; how PgBouncer tracks a parameter that PostgreSQL does not report to clients, such as `statement_timeout`, may differ between versions. For the case in which the value is dropped (`ignore_startup_parameters = extra_float_digits,statement_timeout`), the provisioning script `deployment/postgres/provisioning/01-roles-and-grants.sql` gives the runtime role `ditto_app` a `statement_timeout` default of `60s`, which PostgreSQL applies to every server session opened as that role; PgBouncer must therefore log in as the client's role (no `user=` in its `[databases]` entry; an `auth_user=` is fine). Because of that default, `statement-timeout = 0` (no startup parameter) means "use the role default", not "no timeout". The DDL role deliberately has no such default, so the schema bootstrap and per-path index builds are not cut off by it; without a separate DDL role (`POSTGRES_DDL_USER`), the bootstrap runs as the runtime role and its default applies. Details, including how to lift the timeout: `deployment/postgres/README.md`.

**Persistence ping.** The persistence ping (policies: `always-alive` policies in ID order; connectivity: all connections in priority order; by default one entity per second) runs no statement, transaction or snapshot on PostgreSQL while it waits, so `statement_timeout` applies to one short statement at a time. The ID order reads windows of `persistence-ping.read-journal-batch-size` entity ids (default 500) and keeps the tagged ones: a window costs the same wherever it lies and however the tagged entities are spread (about 1 ms at 1,000,000 entities with 2 events each), and a walk takes (all ids / batch size) statements and reads every journal row of the entities it examines, once per walk: with many entities, few of them always-alive, or long event histories, size the ping `interval` for that (10,000,000 policies: about 20,000 statements per walk, about 20–160 s of database time at 2 events per entity). The priority order reads the same windows (500 ids each), keeps each tagged connection's id and priority in memory (about 100–120 bytes per connection, about 12 MB for 100,000) and pings them highest priority first once the last window is read. In ID order an entity that starts to qualify after the walk's position (the last window read) is pinged in this walk, otherwise in the next one, and one deleted before the walk reaches it is not pinged; the priority order pings the connections as of the windows that read them. A failed statement is retried from the same position with the MongoDB read journal's back-off (1 s doubling to 128 s, 10 % jitter, 10 retries for the default `interval` of `10m`; unlike MongoDB, slow failures count too, so the retries end) and logged at WARN with its SQLSTATE; when the retries are exhausted, the walk ends with a WARN and the next one starts at the next `interval`, in ID order after the last persistence id the failed walk reached (kept in memory, so after a restart from the beginning); if that continued walk fails before it reaches a new persistence id (a failure that recurs there, or an outage still going on), the position is given up and the next walk starts from the beginning. A persistence id that is no entity id is skipped with a WARN instead of failing the walk. At one entity per second, 1,000,000 always-alive policies take about 11.6 days per walk on either backend; to walk faster, raise `persistence-ping.rate.entities` (e.g. `POLICIES_PERSISTENCE_PING_RATE_ENTITIES`, `RECONNECT_RATE_ENTITIES`). Details: `deployment/postgres/README.md`, section "Persistence ping on PostgreSQL".

**Circuit breakers and overload.** The PostgreSQL journal and snapshot-store plugins use the MongoDB plugins' Pekko circuit-breaker settings and the same environment variables: journal 5 failures, 5 s call timeout, 3 s reset (`BREAKER_MAXTRIES`, `BREAKER_TIMEOUT`, `BREAKER_RESET`); snapshot store 5 failures, 10 s call timeout, 6 s reset (`SNAPSHOT_BREAKER_MAXTRIES`, `SNAPSHOT_BREAKER_TIMEOUT`, `SNAPSHOT_BREAKER_RESET`). The Helm chart always sets these variables (`<service>.config.mongodb.journalCircuitBreaker` / `snapsCircuitBreaker`), so with Helm its values apply to either backend. A borrow that the connection pool rejects (`pool.pending-acquire-max-size` reached) or that times out counts as a breaker failure; every recovery starts with a snapshot load, so under overload the snapshot-store breaker usually opens first, and while it is open every recovery in that service fails at once. On either backend, a request whose entity cannot be recovered or written because of overload is answered as on MongoDB: the persistence actor stops, and the command it could not answer fails with `enforcement.timeout` (HTTP 503) once the supervisor's ask timeout expires (5 s for things and policies, 50 s for connections, longer while the entity recovers); a thing create whose implicit policy could not be created fails with `things:thing.notcreatable` carrying the policy error's status. Retry such requests later.

**Connection budget.** Each instance of a PostgreSQL-backed service holds one connection pool: the journal, snapshot store and read journal of policies, things and connectivity share it, and things-search on PostgreSQL has its own. A pool holds at most `ditto.postgresql.pool.max-size` connections (`POSTGRES_POOL_MAX_SIZE`, default `25`). The pools of different services and replicas do not coordinate, so size them together, counting the extra pods of a rolling update: the sum over policies, things, connectivity and things-search (when on PostgreSQL) of `(replicas + maxSurge) × max-size`, plus reserved connections, must not exceed PostgreSQL's `max_connections`. `maxSurge` is the number of extra pods a Deployment starts while it rolls: the Helm chart rolls these four services with `maxSurge: 1` and `maxUnavailable: 0`, and a `helm upgrade` that changes all four rolls them at the same time; while the old pod hands its entities over, its pool is still open, and the new pod recovers them with a pool of its own. Reserved connections are `superuser_reserved_connections`, monitoring, `psql`, and, per service instance, the connections Ditto opens outside its pool: a DDL connection for the schema bootstrap and the schema self-heal, and in things-search the per-path index maintenance at every start (two connections while an index builds). Example: two Kubernetes nodes with one replica each of the four services and `max_connections = 300`, about 20 of them kept for administration: 4 × 25 = 100 in steady state and 4 × (1 + 1) × 25 = 200 during an upgrade, both ≤ 280; scaling things and policies to 3 replicas and things-search to 2 gives (3 + 3 + 1 + 2) × 25 = 225 in steady state but (4 + 4 + 2 + 3) × 25 = 325 during an upgrade, which does not fit; at `max-size = 20` the same replicas take 180 and 260. A stock PostgreSQL (`max_connections = 100`, 3 of them superuser-reserved) holds the three persistence services at one replica each only in steady state (75): rolling them takes up to 150 at once, and still 100 one at a time. So for a stock server, and for things-search or more replicas, raise `max_connections`, lower `max-size` per service or put PgBouncer in front. Beyond `max_connections`, PostgreSQL refuses connections (SQLSTATE 53300, "too many clients"), and recoveries and writes fail. A pool opens connections on demand, at most `max-size` at a time, so a burst can take its whole share at once; idle connections are closed after 30 minutes. Override the size per service with `POSTGRES_POOL_MAX_SIZE` (the pending-acquire queue follows at 2 × `max-size`), with Helm via the service's `extraEnv`:

```yaml
things:
  extraEnv:
    - name: POSTGRES_POOL_MAX_SIZE
      value: "40"
```

Raise a service's `max-size` only when its pool is the measured bottleneck (`ditto_persistence_pool_pending` above 0 for long, "PostgreSQL pool overload" for journal writes, `PoolAcquirePendingLimitException` for recoveries, reads and search, or acquire-timeout errors while PostgreSQL has headroom), and re-check the sum first. `25` is a starting point; confirm production sizing by load testing. For many replicas, put PgBouncer in transaction mode in front of PostgreSQL. Sizing behind PgBouncer and the PgBouncer settings Ditto needs: `deployment/postgres/README.md`, section "Connection budget".

#### 4. Docker Compose volume mount example

```yaml
things:
  image: docker.io/eclipse/ditto-things:${DITTO_VERSION:-latest}
  environment:
    - HOSTING_ENVIRONMENT=filebased
    - HOSTING_ENVIRONMENT_FILE_LOCATION=/opt/ditto/things-postgres.conf
    - POSTGRES_URI=r2dbc:postgresql://postgres:5432/ditto
    - POSTGRES_USER=ditto
    - POSTGRES_PASSWORD=ditto
    - POSTGRES_SSL_MODE=disable   # or verify-full in production with a CA cert
  volumes:
    # 1. Drop-in extension JARs (NOT in the default image — must be provided by the operator; base + persistence thin JAR)
    - ./ditto-postgres-client-extension.jar:/opt/ditto/extensions/ditto-postgres-client-extension.jar
    - ./ditto-postgres-persistence-extension.jar:/opt/ditto/extensions/ditto-postgres-persistence-extension.jar
    # 2. Activation overlay config
    - ./things-postgres.conf:/opt/ditto/things-postgres.conf
```

Repeat the same pattern for the Policies and Connectivity services, substituting the appropriate plugin IDs, overlay conf name, and collection name overrides for each service.

{% include note.html content="Data migration from MongoDB is the operator's responsibility. No automated migration tooling is provided." %}

#### Known limitations

* **Connection encryption migration is MongoDB-only:** connection secrets are encrypted at rest on PostgreSQL exactly as on MongoDB, and the dual-key configuration (`old-symmetrical-key`) works, but the DevOps commands `migrateEncryption`, `migrateEncryptionStatus` and `migrateEncryptionAbort`, which re-encrypt or decrypt all stored connection data, are refused on PostgreSQL (when field encryption is enabled or an `old-symmetrical-key` is set; otherwise nothing answers them and they time out, as on MongoDB in that configuration). After a key rotation keep `old-symmetrical-key` configured; see [Key rotation on PostgreSQL](installation-operating.html#key-rotation-on-postgresql).
* **Other differences from the MongoDB backend:** see [Differences from the MongoDB backend](#differences-from-the-mongodb-backend) and [Schema self-heal](#schema-self-heal).

### PostgreSQL search backend

Thing-search can **optionally** run its search index on PostgreSQL instead of MongoDB, independently of which backend the persistence services use. MongoDB remains the default search backend — this is strictly opt-in and only affects the `things-search` service.

**PostgreSQL version:** PostgreSQL 16 or newer (tested: 16, 17, 18). Older versions log a warning at startup, but the service still starts; technically, persistence needs PostgreSQL ≥ 10 and search PostgreSQL ≥ 13 (from 13 on, `pg_trgm` is a trusted extension, see [Prerequisites](#prerequisites-migration-and-known-limitations)).

**Database encoding and locale:** the search database must be UTF8-encoded, with a UTF-8-based `LC_CTYPE` (e.g. `C.UTF-8` or `en_US.UTF-8`) and the libc collation `C.utf8`. The search service checks this at every start, before it touches the schema, and refuses to start otherwise; CloudNativePG's `initdb` default `LC_CTYPE` `C` does not qualify. See [Prerequisites](#prerequisites-migration-and-known-limitations).

**Operations:** for vacuum, table bloat and monitoring of the search tables, see [Operating PostgreSQL](#operating-postgresql).

#### 1. Provide the extension JARs

Running search on Postgres mounts **two** JARs: the SAME shared `ditto-postgres-client-extension` base the persistence services use, plus the thin `ditto-postgres-search-extension` (search read/write/aggregation classes only):

```bash
mvn -pl :ditto-postgres-client-extension,:ditto-postgres-search-extension -am -DskipTests package

docker cp \
  internal/utils/postgres-client-extension/target/ditto-postgres-client-extension-<version>.jar \
  container_id:/opt/ditto/extensions/
docker cp \
  internal/utils/postgres-search-extension/target/ditto-postgres-search-extension-<version>.jar \
  container_id:/opt/ditto/extensions/
```

#### 2. Create the search activation config

Create a HOCON overlay (e.g. `search-postgres.conf`) with two top-level includes — the search service defaults, then the opt-in Postgres search profile which swaps the `search-persistence-provider` extension and pulls in the shared `ditto.postgresql.*` client/pool/SSL defaults:

```hocon
# search-postgres.conf
# Injected via: HOSTING_ENVIRONMENT=filebased
#               HOSTING_ENVIRONMENT_FILE_LOCATION=/opt/ditto/search-postgres.conf

# 1. Re-include the search service base settings (Mongo search backend by default)
include classpath("search")

# 2. TOP-LEVEL include — opt-in PostgreSQL search backend + shared client/pool/SSL defaults.
#    Swaps ditto.extensions.search-persistence-provider to the PostgreSQL provider; search.conf's own
#    ditto.mongodb defaults legitimately remain present (the overlay only swaps the one extension key).
include classpath("ditto-postgres-search")
```

Environment variables are the same `POSTGRES_*` set as for the persistence backend (`POSTGRES_URI`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_SSL_MODE`). Compose mount:

```yaml
things-search:
  image: docker.io/eclipse/ditto-things-search:${DITTO_VERSION:-latest}
  environment:
    - HOSTING_ENVIRONMENT=filebased
    - HOSTING_ENVIRONMENT_FILE_LOCATION=/opt/ditto/search-postgres.conf
    - POSTGRES_URI=r2dbc:postgresql://postgres:5432/ditto
    - POSTGRES_USER=ditto
    - POSTGRES_PASSWORD=ditto
    - POSTGRES_SSL_MODE=disable   # or verify-full in production with a CA cert
  volumes:
    # base + search thin JAR (both from the same Ditto release)
    - ./ditto-postgres-client-extension.jar:/opt/ditto/extensions/ditto-postgres-client-extension.jar
    - ./ditto-postgres-search-extension.jar:/opt/ditto/extensions/ditto-postgres-search-extension.jar
    - ./search-postgres.conf:/opt/ditto/search-postgres.conf
```

#### Operational tunables

The PostgreSQL search backend adds a small set of search-specific knobs on top of the shared client block. All have safe defaults; override them only if a deployment needs to.

| Config path | Default | Env override | Purpose |
|---|---|---|---|
| `ditto.postgresql.search.reaper.interval` | `30s` | `POSTGRES_SEARCH_REAPER_INTERVAL` | How often the `delete_at` reaper attempts a tick (Postgres has no TTL index; the reaper replaces Mongo's TTL deletion). |
| `ditto.postgresql.search.reaper.batch-size` | `1000` | `POSTGRES_SEARCH_REAPER_BATCH_SIZE` | Rows deleted per reap batch (`DELETE … FOR UPDATE SKIP LOCKED` LIMIT), bounding one statement's lock footprint. |
| `ditto.postgresql.search.reaper.max-batches-per-tick` | `50` | `POSTGRES_SEARCH_REAPER_MAX_BATCHES_PER_TICK` | Safety valve: at most this many batches per tick, so one tick's transaction is never held open against an arbitrarily large backlog. |
| `ditto.postgresql.force-custom-plan` | `true` (for search) | `POSTGRES_SEARCH_FORCE_CUSTOM_PLAN` | Binds `plan_cache_mode=force_custom_plan` on the search JVM's pool so `wpath`-parameterized read statements re-plan against their actual bound path instead of latching onto a generic plan. Also required for the per-path indexes below to be used. Behind PgBouncer, list `plan_cache_mode` in `track_extra_parameters` (see **Statement timeout and PgBouncer** in the persistence section). |
| `ditto.postgresql.search.path-indexes.paths` | unset (not managed) | `POSTGRES_SEARCH_PATH_INDEXES` (a JSON array) | Per-path search indexes, see [Per-path search indexes](#per-path-search-indexes-optional). Unset: none is built or dropped; `[]`: all are dropped. |
| `ditto.postgresql.search.path-indexes.direct-uri` | `""` (use `ditto.postgresql.uri`) | `POSTGRES_SEARCH_PATH_INDEXES_DIRECT_URI` | A direct (non-PgBouncer) r2dbc URI of the same database for building the per-path indexes. Required with `pooler-mode = transaction`. |

Pool sizing, SSL and credentials are **not** search-specific — they come from the shared `ditto.postgresql.*` client block (the same one the persistence backend uses), tuned via `POSTGRES_URI` / `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DDL_*`, `POSTGRES_SSL_MODE` and `POSTGRES_POOL_MAX_SIZE`. The search service's pool counts toward the same connection budget as the persistence services' (see **Connection budget** in the persistence section above).

#### Per-path search indexes (optional)

`like`/`ilike` with a pattern of at least three letters or digits is served by one table-wide trigram index, `sf_trgm`, over the text values of **all** paths. On a path that many things carry, typically a property of every feature (`features/*/properties/…`), such a query first collects the matching values of all paths and then discards those on other paths, and the planner may additionally intersect them with a scan of every row of that path. For such hot paths you can configure a per-path trigram index: a partial index restricted to one path, which the planner uses instead of `sf_trgm` for `like`/`ilike` on exactly that path. It is the PostgreSQL counterpart of the MongoDB custom search indexes.

```hocon
ditto.postgresql.search.path-indexes {
  paths = [
    { path = "features/*/properties/serialNumber", kind = "trigram" }
    { path = "attributes/location/city", kind = "trigram" }
  ]
}
```

The same list as an environment variable, or as system properties:

```shell
POSTGRES_SEARCH_PATH_INDEXES='[{"path":"features/*/properties/serialNumber","kind":"trigram"}]'

-Dditto.postgresql.search.path-indexes.paths.0.path=features/*/properties/serialNumber
-Dditto.postgresql.search.path-indexes.paths.0.kind=trigram
```

* `path`: the search field exactly as you write it in an RQL filter: `attributes/…`, `features/<featureId>/properties/…`, `features/*/properties/…`, `…/desiredProperties/…`, `features/<featureId or *>/definition`, or a field mapped to one of these. `features/*/properties/x` and `features/env/properties/x` are different paths: index the form your queries use. `thingId` and `namespace` are columns of `search_things`, not paths, and are rejected.
* `kind`: `trigram`, the only kind so far.
* An invalid entry (an unknown field, a missing or unknown `kind`, a duplicate) fails the start of the search service.
* **Unset versus empty.** While `paths` is not set (the default; a blank `POSTGRES_SEARCH_PATH_INDEXES` counts as not set), the service manages no per-path index: it builds none and drops none, and logs (INFO) which `sf_path_*` indexes exist. Only a set list is applied, and an explicitly empty list (`paths = []`, or `POSTGRES_SEARCH_PATH_INDEXES='[]'`) drops every `sf_path_*` index (the system index `sf_multi` is maintained independently of this list and never dropped by it).

For each entry the search service builds:

```sql
CREATE INDEX CONCURRENTLY IF NOT EXISTS sf_path_trgm_<hash> ON search_flat
    USING gin ((val_text COLLATE "C.utf8") gin_trgm_ops)
    WHERE wpath = '<the path as stored in search_flat>' AND val_text IS NOT NULL;
```

* **Name:** `sf_path_trgm_` followed by 16 hex digits of the SHA-256 of the index definition. It is the same on every instance and at every start; the service log names the path of every index it builds.
* **Semantics:** the indexed expression is the one of `sf_trgm`, so the prerequisites and the matching are the same (the `pg_trgm` extension and the `C.utf8` collation, see the `like`/`ilike` notes below). The index changes which index serves a query, never which things match.
* **Planning:** the planner can only use a per-path index when it sees the queried path as a value, i.e. with custom plans. Keep `ditto.postgresql.force-custom-plan` at its search default `true`; under a generic plan the per-path indexes are never used.

**How and when the indexes are built.** They are not part of the versioned (checksummed) search schema.

* After every successful schema bootstrap, the search service compares the configured list with the `sf_path_*` indexes on `search_flat`, in the background: the service starts serving right away, and until a new index is valid, queries on its path keep using `sf_trgm`.
* If nothing has to change, that costs one catalog query: no lock, no DDL.
* Otherwise one instance takes a session advisory lock (the others skip), sets `statement_timeout = 0` and `lock_timeout = 0` for its own session, and builds one index at a time with `CREATE INDEX CONCURRENTLY`. The build by itself does not block reads or writes of the running instances. The build phase and progress are logged every 30 seconds.
* Nothing is changed while the stored search schema is newer than the running code (during a rolling upgrade the newer code manages the indexes), and nothing at all when the schema bootstrap refused to start.
* **Repair:** a `CREATE INDEX CONCURRENTLY` that fails or is cancelled, or whose database backend is killed, leaves an INVALID index behind, which `IF NOT EXISTS` would skip forever and which still costs write overhead. The service drops it right away if it can, and otherwise drops and rebuilds it at the next start of any instance.
* If the service stops during a build, PostgreSQL keeps building until the build is done; meanwhile no other instance starts a second one.

**Starting instances during a build or drop is safe.**

* A start whose stored search schema matches the code, and in which every built-in table and index of the search schema exists, runs no schema DDL: it neither waits for a running build or drop nor stalls the writes of the other instances.
* A start that has to run the schema DDL (a schema upgrade, or a missing built-in index such as a dropped `sf_trgm`) waits at most 500 ms for the lock a running build or drop holds on `search_flat`, then fails without changing anything and is retried at the next start (for example when Kubernetes restarts the pod), until the build or drop is done. Writes of the running instances wait at most that half second per attempt; the build is not disturbed.

Still add or remove paths at a quiet time: a build reads the whole table twice. Later restarts with an unchanged list build and drop nothing, except that a start repairs an INVALID index left by a failed build that could not be dropped right away (a full build again).

To see whether a build or drop is running:

* in the log of the instance that builds: `Building search path index sf_path_… (i/n)` and a progress line every 30 seconds, until `Built search path index …` and `Search path index changes applied`;
* in PostgreSQL: `SELECT pid, phase, blocks_done, blocks_total FROM pg_stat_progress_create_index WHERE relid = 'search_flat'::regclass;` returns a row while a build runs, and `SELECT pid, query FROM pg_stat_activity WHERE query LIKE 'DROP INDEX CONCURRENTLY IF EXISTS sf_path_%';` while a drop runs. An `sf_path_*` index with `pg_index.indisvalid = false` is being built, or is the leftover of a failed build.

**A direct database connection is required.** The build runs on a dedicated, non-pooled connection (never one of the search pool) as the DDL role (`ddl-credentials`, else the runtime role), which must own `search_flat`. The advisory lock, the session settings and the build have to run in **one** server session. Transaction-mode PgBouncer cannot carry that: it may run every statement on a different server connection, so the lock could stay behind on a pooled server connection, `statement_timeout = 0` could leak into other clients' transactions, and the build would run under the server's timeout.

* With a direct connection or session-mode pooling, leave `direct-uri` empty.
* With `ditto.postgresql.pooler-mode = transaction`, set `ditto.postgresql.search.path-indexes.direct-uri` (`POSTGRES_SEARCH_PATH_INDEXES_DIRECT_URI`) to a direct r2dbc URI of the same database (the credentials and SSL settings of `ditto.postgresql` apply to it). Without it, the service logs an error and changes no index.
* A `statement_timeout` or `lock_timeout` role default on the DDL role is overridden for the build session only.

**Removing a path drops its index.** At the next start the service drops (`DROP INDEX CONCURRENTLY`) every `sf_path_*` index whose path is no longer in the (set) list, as Ditto drops a MongoDB custom search index that is removed from `custom-indexes`. A per-path index costs maintenance on every write to its path, so a forgotten one would cost write throughput without serving any query.

* Only per-path indexes are dropped. This rests on the names: the service only drops indexes of `search_flat` whose name starts with `sf_path_`, a prefix no other index of the search schema uses, and the hash in the name keeps different paths and definitions apart (the system index `sf_multi` is maintained independently of this list and never dropped by it). Do not give your own indexes that prefix.
* **All search instances that share the database must use the same list** (one configuration source, changed in one rollout). With different lists, each instance drops at its start what its list lacks and builds what it adds, so the indexes are dropped and rebuilt again at every start, and each rebuild reads the whole table. During a rolling change this happens once: an instance with the old list does not rebuild a dropped index before its own next start.
* An instance that must not manage the indexes (for example a blue/green twin on the same database) leaves `paths` unset; it then only logs at INFO which `sf_path_*` indexes it found and leaves them alone.

**Build time and cost.** On the 1M-thing search benchmark corpus (68.5 million `search_flat` rows, 4.5 million of them on `/features/*/properties/prop0`; PostgreSQL 16 defaults, `maintenance_work_mem` 64 MB, a laptop VM), building the index for `features/*/properties/prop0` took **44 seconds**, with much of the table in the page cache, and the index is **51 MB** (the table-wide `sf_trgm`: 304 MB). Infix `ilike` on that path then took **111.2 / 111.1 ms p95** in two runs (96.6 / 95.7 ms p50), instead of **305.8 / 325.5 ms p95** (197.6 / 187.8 ms p50) in two runs without it. Measurements and plans: `internal/utils/search-r2dbc/src/test/resources/bench/results/production-config/path-index/README.md`.

* A build reads the whole `search_flat` heap twice (build and validation scan), however few rows the path has, so its duration grows with the whole table.
* A larger `maintenance_work_mem` for the DDL role speeds up GIN builds, e.g. `ALTER ROLE <ddl role> SET maintenance_work_mem = '512MB'`.
* Every write to a configured path also updates its index.

#### Range filters and the system index sf_multi

A filter with a two-sided numeric range on one field (`ge` or `gt` together with `le` or `lt` on the same field), for example `and(eq(attributes/location/city,"Stuttgart"),ge(features/env/properties/temperature,-15),le(features/env/properties/temperature,-14.01))`, may start from the range instead of the equality:

* With an equality and a range in the same filter, the search counts their index entries in the same statement (the equality up to 50,000 entries, the range up to one more than the equality) and starts from the one with fewer entries; a tie keeps the equality. A two-sided range alone keeps the previous plan. Measured on the 1M-thing benchmark corpus on a host that did not swap, starting from it was at best as fast as that plan (a range matching ~14,000 things) and slower for wider ranges: ~40,000 things 152 → 366 ms, ~620,000 things 112 ms → 5.9 s (all-rounds warm p95 of a find with authorization).
* When the search starts from the equality or the range, it hands each candidate thing to the rest of the filter once. That keeps the database's estimate of the candidates realistic, so it checks the remaining conditions per candidate instead of reading every row of the other condition. Measured on the same corpus: `and(eq(city), range)` with ~14,000 things in the range and ~90,000 in the city, 582 → 162 ms (all-rounds warm p95).
* Results never change: the start only narrows the candidates, and the whole filter, including every authorization check, is still applied.
* One-sided ranges, string ranges, `features/*` fields and ranges under `or`/`not` keep the previous plan.

**Arrays.** Two range conditions may be met by different elements of an array, as on MongoDB: `[91621, 91214, 93186]` matches `and(ge(attributes/region,91700),le(attributes/region,93000))`. A range start therefore also includes every thing with more than one value at that field. These things are found with the index `sf_multi ON search_flat (wpath, thing_id) WHERE ord = 1`, which belongs to search schema version 3.

**How the index is built.**

* After the start of the first version-3 instance, in the background, with `CREATE INDEX CONCURRENTLY`, by the same maintenance as the per-path indexes: one instance builds it under the session advisory lock and logs `Building system search index sf_multi (i/n)` with progress lines, and an INVALID index left by a failed or interrupted build is repaired at the next start.
* It is built whether `path-indexes.paths` is set or not; that list never governs it.
* It needs the same session-capable connection as the per-path indexes: with `ditto.postgresql.pooler-mode = transaction`, set `ditto.postgresql.search.path-indexes.direct-uri`. Without one the service logs a WARN, builds nothing, and range filters keep the previous plan.
* Until an instance sees the index valid (it checks at its start, after its own maintenance run and every minute), its range filters keep the previous plan.

**Cost.** On the 1M-thing benchmark corpus (68.5 million `search_flat` rows), the concurrent build took 72 seconds, and the index is 213 MB with 2.3 million entries. It holds an entry only for a field that has more than one value in a thing. Like every `CREATE INDEX CONCURRENTLY`, the build reads the whole `search_flat` heap twice.

**Do not drop it.** A dropped `sf_multi` is rebuilt at the next start; until then range filters keep the previous plan.

**Version-2 images** (during a rolling upgrade or after a rollback) run against a version-3 schema unchanged and ignore the index.

#### Prerequisites, migration and known limitations

* **`pg_trgm` extension:** the search schema requires the `pg_trgm` extension (used for `like`/`ilike` trigram indexes). It is a *trusted* extension on PostgreSQL ≥ 13, so `CREATE EXTENSION IF NOT EXISTS pg_trgm` on the target database succeeds for the DDL role without superuser; the schema bootstrap fails with an actionable error if the role is not permitted to create it.
* **UTF8 database with a UTF-8 `LC_CTYPE`:** the PostgreSQL search backend needs a UTF8-encoded database with a UTF-8-based `LC_CTYPE` (e.g. `C.UTF-8` or `en_US.UTF-8`; the official `postgres` image's default, `en_US.utf8`, qualifies), the libc collation `C.utf8` (present on glibc-based images, absent on non-UTF8 databases and on musl/Alpine images), and the `pg_trgm` extension.
  * Why the `LC_CTYPE` matters: `pg_trgm` lower-cases and splits words with the database's `LC_CTYPE`, whatever the index collation, while `ilike` compares under `C.utf8`. On a `C`/`POSIX` `LC_CTYPE` every non-ASCII character separates words for `pg_trgm`. An `ilike` that the planner serves from the trigram index then silently misses things whose value differs from the pattern by a character that lower-cases to an ASCII letter (`İ` U+0130 → `i`, the Kelvin sign `K` U+212A → `k`), and patterns made only of non-ASCII letters read the whole index.
  * At every start, before any schema change, the search service reads the database's encoding, its `LC_CTYPE` (judged by name: its codeset must be UTF-8) and whether `C.utf8` exists. It refuses to start with an error that names every unmet prerequisite and the remedy.

  Check your database (connected to the search database):

  ```sql
  SELECT pg_encoding_to_char(d.encoding) AS encoding,        -- must be UTF8
         d.datctype                       AS ctype,           -- must be UTF-8 based (e.g. C.UTF-8, en_US.UTF-8), not C/POSIX
         EXISTS (SELECT 1 FROM pg_collation c
                 WHERE c.collname = 'C.utf8' AND c.collencoding IN (-1, d.encoding)) AS has_c_utf8,   -- must be true
         EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'pg_trgm')      AS pg_trgm_available  -- must be true
  FROM pg_database d WHERE d.datname = current_database();
  ```

  To fix it, create the search database with UTF8 encoding and a UTF-8 `LC_CTYPE`. This also works inside a cluster initialised with `C`:

  ```sql
  CREATE DATABASE ditto_search TEMPLATE template0 ENCODING 'UTF8' LC_COLLATE 'C.UTF-8' LC_CTYPE 'C.UTF-8';
  ```

  On CloudNativePG, whose `initdb` default `LC_CTYPE` is `C`, set `spec.bootstrap.initdb.localeCType: C.UTF-8` (the default encoding is already `UTF8`). An existing search database that does not qualify cannot be changed in place: point the search service at a new, qualifying database and let it re-index (next bullet).
* **Migration = re-index only:** the search index is a rebuildable projection of the things data, so the cutover to Postgres is simply to point search at an empty PostgreSQL database and let the background-sync stream regenerate the index. There is no copy tooling and none is needed. Search results are incomplete until the initial re-index (background sync) finishes — plan this re-index window into the cutover.
* **Speeding up very large re-indexes (optional):** every row written during the initial re-index pays incremental maintenance on the `sf_trgm` trigram GIN index, and a bulk GIN build after the data is loaded is much faster than the same work done insert-by-insert. For very large populations an operator may `DROP INDEX sf_trgm` before starting the backfill and recreate it once the re-index has caught up:

  ```sql
  CREATE INDEX CONCURRENTLY IF NOT EXISTS sf_trgm ON search_flat
      USING gin ((val_text COLLATE "C.utf8") gin_trgm_ops) WHERE val_text IS NOT NULL;
  ```

  While the index is absent, `like`/`ilike` queries fall back to scans — everything else is unaffected. Recreate it **before** the next search-service restart: a start that finds a built-in index missing runs the schema DDL, which would rebuild `sf_trgm` as a plain (blocking, non-concurrent) build against the by-then-populated table. This is purely an optimisation — by default GIN's `fastupdate` pending list amortises per-insert cost, and the re-index works fine with the index in place.
* **`like`/`ilike` performance caveat:** sub-3-character `like`/`ilike` patterns cannot be served by the trigram index and degrade to a scan — mirroring MongoDB's own unanchored-regex degradation. The slow-query log catches abuse. `ilike` on a path that many things carry is slow with the table-wide `sf_trgm` alone: on the 1M-thing benchmark corpus, infix `ilike` on `features/*/properties/prop0` took **305.8–391.5 ms p95** in three runs (most patterns about 200 ms, some 300–420 ms when the planner adds a scan of all rows of the path). Configure a [per-path search index](#per-path-search-indexes-optional) for such paths.
* **`ilike` case folding:** `ilike` lower-cases value and pattern character by character under `C.utf8`; it does not apply full Unicode case folding. So `ß` does not match `ss`/`SS` (the capital `ẞ` does match `ß`), the dotless `ı` matches neither `i` nor `I`, and the final sigma `ς` does not match `σ`/`Σ`, while the dotted `İ` (U+0130) matches `i` and the Kelvin sign `K` (U+212A) matches `k`. MongoDB's case-insensitive regex folds some of these characters differently.
* **Sort performance caveat:** sorting by any field other than `thingId` or `namespace` (an attribute, a feature property such as `sort(+features/env/properties/temperature)`, `_modified`, …) cannot be served by an index. To return any page, the search looks up the sort value of **every** thing that matches the filter and is visible to the caller, then orders them. The cost grows with the number of matching visible things, not with the page size, and neither `limit(offset,size)` nor cursor paging reduces it.
  * On the 1M-thing search benchmark (PostgreSQL 16 defaults: 128 MB `shared_buffers`, `jit = on`; no filter; about 29,400 things visible to the caller), one sorted page took about **350 ms p95** for page 1 and about **360–390 ms p95** for page 100 via `limit`. Page 100 via the cursor took about **680 ms p95**. That is above the 200 ms target the other read shapes are measured against.
  * The cursor page costs more because its resume filter is evaluated for every visible thing.
  * For fast sorted searches, sort by `thingId` (the default) or `namespace`. Both are index-served: on the same corpus, page 1 took 0.3 ms p95 (warm). Deep pages via `limit` still cost in proportion to the skipped things (about 130 ms p95 at page 100). Otherwise, narrow the matching set with a selective filter.
  * About 200 ms of the cursor page is JIT compilation (`jit = on` is the PostgreSQL default). With `jit = off`, that page took about 460 instead of 670 ms p95 on the same corpus.
  * Measurements, SQL and plans: `internal/utils/search-r2dbc/src/test/resources/bench/results/production-config/sort-path-bound/` (`README.md` and `psql-checks.md`).
* **Mongo-only search knobs:** search settings specific to MongoDB (e.g. per-metric MongoDB index hints, custom MongoDB search indexes) are ignored (logged with a WARN) when the PostgreSQL search backend is active. Per-path PostgreSQL indexes are configured separately, see [Per-path search indexes](#per-path-search-indexes-optional).
* **Other differences from the MongoDB backend:** see [Differences from the MongoDB backend](#differences-from-the-mongodb-backend); in particular, the search service does not re-create a missing table while it runs ([Schema self-heal](#schema-self-heal)).

{% include note.html content="A `search-update-mapper` custom extension implementation must be re-typed to the backend-neutral write model to work with this release — see the release notes for the `SearchUpdateMapper` breaking change and migration guide. Custom `search-update-observer` implementations need no change." %}

#### Background-sync and updater-stream settings — identical on PostgreSQL

The `ditto.search.updater` settings listed below are consumed by the backend-neutral service layer
(`SearchUpdaterRootActor`, `BackgroundSyncActor`, `ThingUpdater`, `PolicyModificationForwarder`, `EnforcementFlow`)
and therefore behave exactly the same whether the index lives in MongoDB or PostgreSQL. The `ditto-postgres-search`
profile does not define or override any `ditto.search.*` key, so the usual environment overrides keep working
unchanged. (`stream.persistence.with-acks-writeConcern` is a MongoDB write concern and has no effect on PostgreSQL.)

| Setting (`ditto.search.updater.…`) | Env override | On PostgreSQL |
|---|---|---|
| `event-processing-active` | `EVENT_PROCESSING_ACTIVE` | identical (service-level flag, not backend-specific) |
| `background-sync.enabled` | `BACKGROUND_SYNC_ENABLED` | identical — gates the background-sync actor's wake-up |
| `background-sync.quiet-period` | `BACKGROUND_SYNC_QUIET_PERIOD` | identical — start-up delay and bookmark interval; the bookmark is the single row of the `search_sync` table (the MongoDB capped-collection equivalent) |
| `background-sync.tolerance-window` | `BACKGROUND_SYNC_TOLERANCE_WINDOW` | identical — compares against the thing's `_modified` timestamp, which the PostgreSQL backend stores in `search_things.t_modified` (`timestamptz`, microsecond precision) |
| `stream.write-interval` | `THINGS_SEARCH_UPDATER_STREAM_WRITE_INTERVAL` | identical — the per-thing write tick lives in the updater actor; both backends receive one write per tick |
| `stream.policy-cache.retry-delay`, `stream.thing-cache.retry-delay` | `THINGS_SEARCH_UPDATER_STREAM_POLICY_CACHE_RETRY_DELAY`, `THINGS_SEARCH_UPDATER_STREAM_THING_CACHE_RETRY_DELAY` | identical — enforcer-cache retry back-off in the shared enforcement flow (the policy-cache delay is parsed but currently not consumed; only the thing-cache delay drives the retry) |

The only PostgreSQL-specific search tunables are the ones listed under *Operational tunables* above (including the per-path indexes); there is no
Postgres-side override of any background-sync or stream setting.

### Operating PostgreSQL

Runtime behaviour of the PostgreSQL backends that matters when you operate them. Provisioning, TLS, the connection budget, PgBouncer, schema upgrades and the persistence ping are described in the runbook `deployment/postgres/README.md`. Vacuum, table bloat and disk space are covered from [Dead rows and autovacuum](#dead-rows-and-autovacuum) on.

#### Schema self-heal

Policies, things and connectivity on PostgreSQL re-create a missing table while they run, as MongoDB creates a missing collection on first use. When a statement fails because a table does not exist (SQLSTATE `42P01`), the service checks the persistence schema again, runs its DDL where needed and then retries the statement once. The DDL is idempotent (`CREATE … IF NOT EXISTS` plus the schema's table storage parameters), so it re-creates whatever table or index of the persistence schema is missing, for all entity types, and leaves the rest alone. This applies to every persistence statement of these services: journal writes, snapshots, recoveries, the read-journal streams (persistence ping, background cleanup, snapshot streaming) and purges. Self-heal is on by default: `ditto.postgresql.schema.self-heal` (`POSTGRES_SCHEMA_SELF_HEAL`).

{% include note.html content="Self-heal **masks data loss**. If the database is wiped, re-created or restored empty under running services, they carry on with empty tables: entities whose data is gone no longer exist, and new writes succeed. MongoDB behaves the same when its collections are dropped. To have the services fail instead, disable self-heal (see below), and in any case alert on the self-heal metric." %}

How a heal works:

* It runs in one transaction under the same advisory lock as the schema check at start, so heals and starts of all instances are serialised, and concurrent misses in one instance share one heal.
* It follows the start's rules for skipping the DDL: it runs no DDL while the stored `schema_version` row has the code's version and checksum and every table and index exists, and never against a newer stored schema version (during a rolling upgrade: with a compatible newer schema it changes nothing and the statement is retried once; with an incompatible one it fails). Unlike a start, it does not refuse a stored row with the code's version but a different checksum; it runs the DDL. Its DDL waits at most 500 ms for each table lock (`lock_timeout`).
* It does not verify the catalog and never writes the `schema_version` row; after a wiped database, the next start writes the row again.
* It connects like the schema bootstrap: as the DDL role (`POSTGRES_DDL_USER`, `POSTGRES_DDL_PASSWORD`) if one is configured, otherwise as the runtime role, on one short-lived connection outside the pool. A role that may not create tables in the schema, such as `ditto_app` from `deployment/postgres/provisioning/01-roles-and-grants.sql`, makes every heal fail with SQLSTATE `42501`.
* When a heal fails (missing privileges, a lock timeout, an incompatible newer schema), the statement fails with the original `42P01` error. A heal is not retried; the next statement that meets the missing table starts a new one.

**Things-search does not self-heal.** While a search table is missing, its statements fail. The schema check at the next start of the search service re-creates the tables, and background sync then indexes the things again, as on a new database.

**Disabling it.** With `ditto.postgresql.schema.self-heal = false` (`POSTGRES_SCHEMA_SELF_HEAL=false`), a statement on a missing table fails with the `42P01` error: the affected persistence actors fail and their requests get errors, instead of the services continuing on empty tables. Disable it in production setups that prefer to fail loud, for example where a missing table can only mean an accidental `DROP`, a wrong database or a failed restore. The schema check at start re-creates missing tables whatever this setting says, so a restarted service starts on empty tables either way: check the database before you restart the services.

**Monitoring.** Each heal that ran DDL increments the counter `ditto_persistence_schema_self_heal` (tag `engine="postgres"`; on the Prometheus endpoint `ditto_persistence_schema_self_heal_total`) by one. A heal that ran no DDL and a heal that failed are not counted. Any increase means that a table disappeared under a running service; alert on it, for example with `increase(ditto_persistence_schema_self_heal_total[15m]) > 0`. After a wiped database the `schema_version` row is gone too, so until a restart writes it again every heal runs the DDL: expect the WARN and an increase from each instance whose statements met a missing table, even when another instance has already re-created the tables.

**Recognising a heal in the logs:**

* WARN `Missing PostgreSQL table detected; recreated the schema of component 'ditto-postgres-persistence' (self-heal). This masks a wiped/absent database — set ditto.postgresql.schema.self-heal=false to fail loud instead.` (logger `…postgres.client.PostgresSchemaHealer`): the heal re-created what was missing.
* WARN `PostgreSQL schema self-heal failed; the original missing-table error will surface unchanged.` with the cause, for example `permission denied for schema …` (`42501`) or a lock timeout (`55P03`).
* INFO `PostgreSQL schema self-heal for component 'ditto-postgres-persistence': every table and index of the schema exists; running no DDL.` (logger `…postgres.client.schema.PostgresSchemaManager`): the tables were already back, for example because another instance healed first. This needs a current `schema_version` row, so it appears when single tables were dropped, not after a wiped database. An INFO line that names a newer, compatible stored schema version means the heal ran no DDL during a rolling upgrade.

#### Differences from the MongoDB backend

The PostgreSQL backends behave like the MongoDB ones except for the following points.

* **Stored JSON is normalised.** Events and snapshots are stored as `jsonb`, which does not keep the order of object keys and rewrites numbers in exponent notation (`1E10` is stored as `10000000000`; `21.0` and integers beyond 2^53 are kept as written). Values never change, but once an entity is loaded from the database again (after a restart, after passivation, or when historical revisions or events are read), the keys of its objects, for example attributes and feature properties, can come back in a different order than they were written. MongoDB keeps the order. JSON objects are unordered: do not depend on key order.
* **U+0000 cannot be stored.** `jsonb` cannot hold the character U+0000; MongoDB can store it in string values (not in keys). For the payload of thing and policy commands this makes no difference: Ditto's API rejects a payload that contains it with HTTP 400 on both backends, before anything is stored. Connection commands are not checked, and neither are the `put-metadata` header values of thing commands (they become event metadata). MongoDB stores such a write. On PostgreSQL the journal rejects the event: nothing is stored, a WARN `Rejecting PostgreSQL journal write for pid <…> due to a SQLSTATE class-22 data exception …` is logged, the entity keeps running, and the request gets no successful answer (it fails when its ask timeout expires: 5 s for things, 50 s for connections). The same happens to any other event with U+0000 that reaches the PostgreSQL journal, for example from a custom extension.
* **Live journal queries end.** `eventsByPersistenceId`, `eventsByTag` and `persistenceIds` of the PostgreSQL read journal return the current state and complete, like their `current…` variants; they do not wait for new events. With Ditto's configuration MongoDB does the same (its realtime collection is disabled, `realtime-enable-persistence = false`), and no Ditto feature uses these queries, so this matters only for custom extensions.
* **Snapshot streaming reads whole snapshots.** When the things service runs on PostgreSQL, the snapshot stream that search background sync reads returns each thing's complete snapshot, although background sync asks only for `_revision`, `policyId` and `_modified`; MongoDB reads only the requested fields. The results are the same, but each background-sync pass reads and transfers the full snapshots. Background cleanup and the connectivity service's retrieval of all connection ids request no fields and read no snapshot payload: their pages select only the persistence id, sequence number, lifecycle and write time of each entity's newest snapshot.
* **Streaming is bound to the service's own tables.** The read journal of each service reads the tables of one entity type (`read-journal.entity`, `POSTGRES_READ_JOURNAL_ENTITY`: `things`, `policies`, `connections` or `wot`), and creating a snapshot stream of another entity type fails with a configuration error; MongoDB uses the service's configured collections. In Ditto only the things service streams snapshots, so a standard deployment is not affected.
* **Persistence ping.** On PostgreSQL the ping reads windows of entity ids in both orders (the priority order sorts the ids it collected in memory before the first ping), and every failed attempt counts towards the retry limit, while MongoDB keeps retrying an attempt that fails slowly. See **Persistence ping** in [PostgreSQL persistence backend](#postgresql-persistence-backend) and `deployment/postgres/README.md`, section "Persistence ping on PostgreSQL".
* **Pool overload fails fast.** A borrow from the connection pool fails after 4 s (`pool.max-acquire-time`, no acquire retry, a 2 s connect timeout), and borrowers beyond 2 × `max-size` waiting fail at once (a journal write reports "PostgreSQL pool overload"); for journal and snapshot writes the MongoDB persistence plugin's pool waits up to 2 minutes for a connection (`waitqueuetimeout`; Ditto's own MongoDB client, used by search and the read journal, waits up to 30 s, `maxWaitTime`). The circuit breakers use the MongoDB values and environment variables, and the failed request is answered exactly as on MongoDB; see **Circuit breakers and overload** in [PostgreSQL persistence backend](#postgresql-persistence-backend).
* **Late commits under overload remain possible on both backends.** A write can still commit after its caller's deadline (5 s for things and policies) expired and the caller was told it failed: on PostgreSQL when its statement is still running at the deadline (the wait for a connection ends after 4 s), on MongoDB also when it waited for a connection beyond the deadline. Under overload, a failed thing creation can also leave the policy it created behind (a retried policy creation that commits late, or a rollback whose delete is itself rejected). These paths are in Ditto's request handling, not in the backend; on PostgreSQL the pool's fast rejections and acquire timeouts trigger them.
* **Connection encryption migration** is MongoDB-only, see [Known limitations](#known-limitations).
* **Search: MongoDB-only settings** (index hints, custom MongoDB search indexes) are ignored with a WARN, see [Prerequisites, migration and known limitations](#prerequisites-migration-and-known-limitations).
* **Search: purged namespaces stay findable for a while**, as on MongoDB. Search queries do not filter the things a namespace purge has marked for deletion; they disappear when they are removed: on PostgreSQL by a reaper every 30 s (`ditto.postgresql.search.reaper.interval`), at most `batch-size` × `max-batches-per-tick` (50,000 by default) per run; on MongoDB by its TTL index. A deleted thing disappears at once on both backends.
* **Search: no runtime self-heal**, see [Schema self-heal](#schema-self-heal).

#### Dead rows and autovacuum

PostgreSQL does not remove a deleted row at once. The row stays in the table and in its indexes as a dead row until a `VACUUM` (normally autovacuum) processes the table; the space then becomes free for new rows of the same table, but the files keep their size. Dead rows that pile up ("bloat") make tables, indexes and backups larger and reads of the affected entities slower. This and the following sections describe how Ditto's cleanup creates dead rows, which autovacuum settings the schemas apply, the server settings that decide whether autovacuum keeps up, what to monitor, and how to get disk space back after a large prune.

The persistence services use the tables `<entity>_journal` (events), `<entity>_snaps` (snapshots) and `<entity>_journal_seq` (highest sequence number per entity) for the entities `things`, `policies`, `connections` and `wot`; every persistence service creates all twelve. The search service uses `search_things`, `search_flat` and `search_sync`. With the provisioning script `deployment/postgres/provisioning/01-roles-and-grants.sql` the tables are in the schema `ditto`, which is on the `search_path` of both Ditto roles. Run the statements below on the primary, in the Ditto database.

#### How cleanup creates dead rows

* **What is deleted.** The background cleanup of each persistence service deletes, entity by entity, the events and snapshots that are older than `history-retention-duration` and no longer needed to recover the entity (it keeps the newest snapshot older than that, and everything after it). It deletes with plain `DELETE` statements, so every deleted event or snapshot becomes a dead row.
* **Pace.** Every `interval`, each service instance gets `credits-per-batch` credits; one credit is one `DELETE` of at most `writes-per-credit` events or snapshots of one entity. An instance therefore deletes at most `credits-per-batch × writes-per-credit / interval` rows per second:

  | Service | Configuration defaults (`history-retention-duration`, `interval`, `credits-per-batch`, `writes-per-credit`) | Rows/s per instance | Helm chart values | Rows/s per instance |
  |---|---|---|---|---|
  | things | `3d`, `3s`, `3`, `100` | ≤ 100 | `30d`, `1s`, `5`, `100` | ≤ 500 |
  | policies | `30d`, `10s`, `3`, `100` | ≤ 30 | `30d`, `1s`, `5`, `100` | ≤ 500 |
  | connectivity | `30d`, `60s`, `1`, `100` | ≤ 1.7 | `30d`, `10s`, `1`, `100` | ≤ 10 |

  The instances of a service share the entities between them, so the cluster deletes up to the sum over its instances. The pace is a ceiling: a credit spent on snapshots, or on the last events of an entity, deletes fewer rows. The credits are the only throttle: the pace does not adapt to the database load (the `timer-threshold` setting does not currently lower it). The environment variables are `CLEANUP_HISTORY_RETENTION_DURATION`, `CLEANUP_INTERVAL`, `CLEANUP_CREDITS_PER_BATCH` and `CLEANUP_WRITES_PER_CREDIT`; the Helm chart sets the values under `<service>.config.cleanup`. The counters `cleanup_delete_events` and `cleanup_delete_snapshots` show what cleanup actually deletes.
* **At the paced rate, autovacuum keeps up.** With the schema's scale factor of 1 % (see [Autovacuum settings applied by the schema](#autovacuum-settings-applied-by-the-schema)), autovacuum starts on a journal of 35 million rows after about 350,000 dead rows: at 100 rows/s, about once an hour. In the persistence benchmark, two hours of cleanup at the things default pace deleted 56 rows/s on average; autovacuum started on `things_journal` after about 1 hour 45 minutes, and one short run brought its dead rows back down.
* **Search tables.** Every thing change replaces the thing's `search_things` row and its changed `search_flat` rows, so the search tables collect dead rows from normal updates, without any cleanup. The same monitoring applies.
* **A mass prune is an operator-induced worst case.** The following delete far faster than the paced cleanup:
  * raising the pace, in the configuration or at runtime with the DevOps command that [modifies the background cleanup configuration](installation-operating.html#modify-background-cleanup-coordinator-configuration) (it applies until the next restart);
  * shortening `history-retention-duration`, or enabling cleanup for the first time, on a database with a large backlog;
  * the [cleanup command for single entities](installation-operating.html#cleanup-events-and-snapshots-of-an-entity) sent for many entities, and [erasing the data of a namespace](installation-operating.html#erase-all-data-in-a-namespace-from-the-persistence), which deletes all of its rows at once.

  In the persistence benchmark (250,000 things with 35 million events, PostgreSQL 16 with default vacuum settings, limited to 4 CPUs and 8 GiB of memory), an unthrottled sweep deleted 22 million events and 36,000 snapshots in about 14 minutes (about 26,000 rows/s; at the things default pace it would take more than four days). With the schema's settings, autovacuum ran on `things_journal` again and again during the sweep and had finished within 2 minutes after it; with the scale factor alone and PostgreSQL's default vacuum speed, it took about 53 minutes. Neither made the files smaller: `things_journal` kept its size of about 38 GB for the 13 million events left. Evidence: `internal/utils/persistence-bench/benchmark/README.md`, section 5. What to do after a mass prune: [After a mass prune](#after-a-mass-prune).

#### Autovacuum settings applied by the schema

The schema bootstrap sets these storage parameters on the tables Ditto deletes from or updates heavily:

| Tables | `autovacuum_vacuum_scale_factor` | `autovacuum_vacuum_cost_limit` |
|---|---|---|
| `<entity>_journal`, `<entity>_snaps` for `things`, `policies`, `connections`, `wot` | `0.01` | `2000` |
| `<entity>_journal_seq` for the same entities | `0.01` | not set |
| `search_things` (its TOAST table, which holds the thing documents, inherits the value), `search_flat` | `0.01` | not set |
| `search_sync` | not set | not set |
| Not set means the server's setting; PostgreSQL's defaults: | `0.2` | `-1`, which means `vacuum_cost_limit` (`200`) |

* **Scale factor.** Autovacuum processes a table once its dead rows exceed `autovacuum_vacuum_threshold` (default 50) plus the scale factor times its rows, so after 1 % instead of 20 %. (PostgreSQL 18 also caps that number at `autovacuum_vacuum_max_threshold`, 100 million by default.)
* **Cost limit.** Autovacuum sleeps for `autovacuum_vacuum_cost_delay` (default `2ms`) each time it has used up its cost limit; reading a page from disk costs 2 and dirtying a page 20. With `2000` instead of the default `200`, a vacuum of a journal or snapshot table may read and write ten times as much per second, up to about 390 MiB of dirtied pages per second instead of about 39 MiB, so it finishes these large tables soon after a prune (see the benchmark numbers above). A table with its own cost limit is left out of the sharing of the server's budget between autovacuum workers: every worker on such a table uses the whole `2000`, so with the default three workers up to three times that at once. While such a vacuum runs, it competes with Ditto's statements for I/O. In the benchmark above, an event append took 4.75 ms and a snapshot write 23.45 ms (95th percentile) while a vacuum with `2000` ran on `things_journal`, against 2.39 and 2.34 ms with no vacuum running, and 3.27 and 2.80 ms while a vacuum at the default speed ran. Watch the storage latency and the duration of Ditto's PostgreSQL statements (`ditto_persistence_command_duration`) while these tables are vacuumed, and slow the vacuums down if the storage cannot take it (see "Changing them" below).
* Every other autovacuum setting, including the memory, comes from the server, see [Server settings for vacuum](#server-settings-for-vacuum).
* To see the parameters a table has:

  ```sql
  SELECT c.relname AS table_name, c.reloptions
  FROM pg_class c
  WHERE c.relkind = 'r'
    AND (c.relname ~ '^(things|policies|connections|wot)_(journal|journal_seq|snaps)$'
         OR c.relname IN ('search_things', 'search_flat', 'search_sync'))
  ORDER BY c.relname;
  ```

* **Changing them.** No Ditto configuration overrides these values: they are part of the schema DDL, whose checksum each service compares at start (see **Schema check at every start** in [PostgreSQL persistence backend](#postgresql-persistence-backend)). The checksum covers the DDL text, not the tables' current parameters, so a change made with `ALTER TABLE` is neither detected nor a reason to refuse a start. What happens to such a change:
  * A start that runs no DDL (the normal case: same schema version, every table and index present) keeps it.
  * A start that runs the schema DDL (a schema upgrade, a missing table or index), and a persistence [self-heal](#schema-self-heal) that runs it, set every parameter of the table above back to the schema's value. Parameters the schema does not set, such as `autovacuum_vacuum_cost_delay` or `autovacuum_vacuum_threshold`, stay: `ALTER TABLE … SET` only changes the parameters it names.

  So prefer the server settings. To change a single table, use a parameter the schema does not set: for example, to slow down the vacuums of a journal, raise its `autovacuum_vacuum_cost_delay` (`4` milliseconds about halves its budget) rather than lowering its cost limit, which the next DDL run would set back to `2000`. Run the statement as the owner of the table (the DDL role, e.g. `ditto_ddl`), and check the parameters again after each Ditto upgrade. Changing an autovacuum parameter does not rewrite the table and does not block reads or writes. It waits for a running manual `VACUUM` of that table, while a running autovacuum of it is interrupted (unless it runs to prevent transaction ID wraparound):

  ```sql
  ALTER TABLE things_journal SET (autovacuum_vacuum_cost_delay = 4);
  ALTER TABLE things_journal RESET (autovacuum_vacuum_cost_delay);
  ```

#### Server settings for vacuum

Server settings apply to all tables of the PostgreSQL server. Set them in `postgresql.conf`, with `ALTER SYSTEM`, or, on a managed service such as Amazon RDS, Azure Database for PostgreSQL or Google Cloud SQL, in its server parameters.

* **Memory for dead rows: `autovacuum_work_mem` and `maintenance_work_mem`.** A vacuum collects the ids of a table's dead rows in memory; each time that memory is full, and once at the end, it cleans every index of the table. On PostgreSQL 16 one dead row takes 6 bytes, so the default (`autovacuum_work_mem = -1`, which means `maintenance_work_mem`, `64MB`) holds about 11.2 million dead rows, and a table with more makes the vacuum read all of its indexes more than once. In the benchmark sweep above, the vacuum with the scale factor alone met about 15 million dead rows in `things_journal` and cleaned its indexes twice. Set `autovacuum_work_mem` to at least the largest number of dead rows you expect in one table times 6 bytes, with room to spare: `256MB` holds about 44 million, twice the sweep above. PostgreSQL 16 uses at most `1GB` (about 179 million dead rows) per vacuum, whatever the setting. Every autovacuum worker may use that much at the same time (three workers with `256MB`: 768 MB). PostgreSQL 17 and later store dead row ids more compactly and have no 1GB limit. `maintenance_work_mem` is also used by manual `VACUUM` and by index builds such as the per-path search indexes, so set `autovacuum_work_mem` on its own rather than raising `maintenance_work_mem` for everything.
* **Workers: `autovacuum_max_workers`** (default 3). A worker vacuums one table at a time; after a mass prune, the vacuum of a large journal keeps one worker busy for its whole duration while the others go on with the remaining tables. Keep the default unless the monitoring below shows tables waiting: several tables above their `autovacuum_starts_at` while every worker is busy. Each further worker adds memory (`autovacuum_work_mem`) and, on the journal and snapshot tables, I/O (their own `2000`). Changing it needs a server restart on PostgreSQL 16 and 17; PostgreSQL 18 applies it on reload, up to `autovacuum_worker_slots`.
* **Speed of the other tables: `autovacuum_vacuum_cost_limit` and `autovacuum_vacuum_cost_delay`.** Tables without their own cost limit (the `<entity>_journal_seq` tables, the search tables and every other table of the server) share the server's budget: `autovacuum_vacuum_cost_limit` (default `-1`, which means `vacuum_cost_limit`, `200`) per `autovacuum_vacuum_cost_delay` (default `2ms`). That limits their workers together to about 50,000 pages read from disk (about 390 MiB) or 5,000 pages dirtied (about 39 MiB) per second; more workers do not vacuum them faster. If autovacuum falls behind on such a table and its worker mostly waits in `VacuumDelay` (see [Monitoring vacuum and table growth](#monitoring-vacuum-and-table-growth)) while the storage has headroom, raise `autovacuum_vacuum_cost_limit` in steps and watch the storage latency and Ditto's statement durations. If it mostly waits for I/O instead, a higher limit does not help. The journal and snapshot tables keep their `2000` whatever the server's cost limit, but they use the server's `autovacuum_vacuum_cost_delay`.

  ```sql
  ALTER SYSTEM SET autovacuum_work_mem = '256MB';
  SELECT pg_reload_conf();
  SELECT name, setting, unit, context, pending_restart
  FROM pg_settings
  WHERE name IN ('autovacuum_work_mem', 'maintenance_work_mem', 'autovacuum_max_workers',
                 'autovacuum_vacuum_cost_limit', 'autovacuum_vacuum_cost_delay', 'vacuum_cost_limit');
  ```

  The value is an example. A setting whose `context` is `postmaster` takes effect only after a restart (`pending_restart` shows a change that waits for one).

#### Monitoring vacuum and table growth

Ditto does not export per-table database metrics. Read them from PostgreSQL's statistics views, with your PostgreSQL monitoring (for example the Prometheus `postgres_exporter`) or with the queries below. Run them on the primary as a role with the privileges of `pg_monitor`, or as a superuser: other roles do not see the details of other users' sessions.

**Per table:** dead rows, the number of dead rows at which autovacuum starts, the last vacuums, and the sizes:

```sql
SELECT s.relname                                          AS table_name,
       s.n_live_tup,
       s.n_dead_tup,
       round(100.0 * s.n_dead_tup / nullif(s.n_live_tup + s.n_dead_tup, 0), 1) AS dead_pct,
       ceil(coalesce(o.threshold, current_setting('autovacuum_vacuum_threshold')::float8)
            + coalesce(o.scale_factor, current_setting('autovacuum_vacuum_scale_factor')::float8)
              * greatest(c.reltuples, 0))                 AS autovacuum_starts_at,
       s.last_autovacuum,
       s.last_vacuum,
       s.autovacuum_count,
       pg_size_pretty(pg_table_size(s.relid))             AS table_size,
       pg_size_pretty(pg_indexes_size(s.relid))           AS index_size
FROM pg_stat_user_tables s
JOIN pg_class c ON c.oid = s.relid
CROSS JOIN LATERAL (
    SELECT max(option_value) FILTER (WHERE option_name = 'autovacuum_vacuum_scale_factor')::float8 AS scale_factor,
           max(option_value) FILTER (WHERE option_name = 'autovacuum_vacuum_threshold')::float8    AS threshold
    FROM pg_options_to_table(c.reloptions)) o
WHERE s.relname ~ '^(things|policies|connections|wot)_(journal|journal_seq|snaps)$'
   OR s.relname IN ('search_things', 'search_flat', 'search_sync')
ORDER BY s.n_dead_tup DESC;
```

* `n_dead_tup` well above `autovacuum_starts_at` for a long time means that autovacuum does not keep up, or cannot remove the dead rows (see below).
* The counters are estimates. They start again from zero after a crash or a statistics reset.

**Running vacuums**, with their progress and what they wait for:

```sql
SELECT p.pid,
       p.relid::regclass    AS table_name,
       a.backend_type,
       p.phase,
       p.heap_blks_scanned,
       p.heap_blks_total,
       p.index_vacuum_count,
       now() - a.xact_start AS running_for,
       a.wait_event_type,
       a.wait_event
FROM pg_stat_progress_vacuum p
JOIN pg_stat_activity a USING (pid);
```

* `wait_event` `VacuumDelay`: the vacuum sleeps because of its cost limit; the cost settings limit its speed.
* I/O wait events such as `DataFileRead`: the storage limits its speed.
* `index_vacuum_count` above 1 on PostgreSQL 16: the dead row ids did not fit into `autovacuum_work_mem` (`maintenance_work_mem` for a manual `VACUUM`), and every further pass reads all indexes of the table again.
* A poll of this view misses short runs. To have the server log every autovacuum run with its duration, index passes and removed rows, lower `log_autovacuum_min_duration` (PostgreSQL 15 and later log only runs longer than 10 minutes by default):

  ```sql
  ALTER SYSTEM SET log_autovacuum_min_duration = '10s';
  SELECT pg_reload_conf();
  ```

**Dead rows that vacuum cannot remove.** A vacuum removes only rows that were deleted before the oldest snapshot still in use. A long-running or forgotten transaction, a replication slot that nobody reads any more (or a standby with `hot_standby_feedback`), or an abandoned prepared transaction keeps every newer dead row of the database in place; `VACUUM VERBOSE` then reports them as "dead but not yet removable". To find the cause:

```sql
SELECT pid, usename, backend_type, state,
       now() - xact_start AS transaction_age,
       age(backend_xmin)  AS xmin_age,
       left(query, 80)    AS query
FROM pg_stat_activity
WHERE backend_xmin IS NOT NULL
ORDER BY age(backend_xmin) DESC
LIMIT 5;

SELECT slot_name, active, xmin, catalog_xmin FROM pg_replication_slots;

SELECT gid, prepared, owner FROM pg_prepared_xacts;
```

**Example alert thresholds** (starting points; tune them to your load):

| Condition | Example threshold | What it indicates |
|---|---|---|
| `n_dead_tup` above 5 × `autovacuum_starts_at` | for more than 1 hour | autovacuum does not keep up, or cannot remove the rows |
| `dead_pct` of a journal or snapshot table above 20 | for more than 1 hour | bloat is building up |
| `last_autovacuum` older than 24 hours while `n_dead_tup` is above `autovacuum_starts_at` | — | autovacuum does not get to the table (workers busy, lock conflicts) |
| a row in `pg_stat_progress_vacuum` | running for more than 6 hours, or `index_vacuum_count` of 2 or more on PostgreSQL 16 | vacuum too slow, or too little memory |
| oldest `transaction_age` | more than 1 hour | a transaction holds back the removal of dead rows in the whole database |
| table plus index size | grows by more than 20 % a week while `n_live_tup` does not | bloat; plan [reclaiming disk space](#reclaiming-disk-space) |
| free disk | less than the size of the largest table plus its indexes | no room left to rewrite a table |

#### After a mass prune

After a mass prune, vacuum and analyze the pruned tables by hand rather than waiting for autovacuum. If autovacuum has already caught up (with the schema's settings it usually does soon, in the benchmark above within 2 minutes), this takes little time; otherwise it finishes the work at once. It also brings the planner statistics up to date, and its output shows whether something holds back the removal of dead rows. For example, after shortening the retention of things:

```sql
SET maintenance_work_mem = '1GB';
VACUUM (VERBOSE, ANALYZE) things_journal;
VACUUM (VERBOSE, ANALYZE) things_snaps;
```

* Run it as the owner of the tables (the DDL role, e.g. `ditto_ddl`) or as a superuser; on PostgreSQL 17 and later also as a role with the privileges of `pg_maintain`. The runtime role (`ditto_app`) may not vacuum the tables: PostgreSQL skips them with a warning. `VACUUM` cannot run inside a transaction, and the role should have no `statement_timeout` (the provisioning script sets none for the DDL role).
* It does not block reads and writes. For as long as it runs, it blocks another vacuum of the same table, a Ditto start that has to run schema DDL (that start fails after 500 ms and is retried at the next start, so do not vacuum by hand during an upgrade that changes the schema), and `CREATE INDEX CONCURRENTLY` on that table, for example a per-path search index build on `search_flat`.
* A manual `VACUUM` is not throttled by default (`vacuum_cost_delay = 0`). On a busy database, throttle it with `SET vacuum_cost_delay = '2ms';` (it then uses `vacuum_cost_limit`, `200` by default, per 2 ms).
* In the `VERBOSE` output, `index scans:` above 1 on PostgreSQL 16 means that `maintenance_work_mem` was too small, and "N are dead but not yet removable" with N above 0 means that something holds back the removal (see the queries above).
* Do not use `VACUUM FULL` for this: it locks the table exclusively, see the next section. A plain `VACUUM` makes the space reusable for new rows; the files keep their size, except for empty pages at their end.

#### Reclaiming disk space

Usually there is no need: once cleanup has caught up and the tables are vacuumed regularly, new rows reuse the freed space and the tables stop growing. Reclaim space when you need the disk back, for example after a one-time reduction of the retention. In the benchmark, `things_journal` (table and indexes) still took 38,319 MB after the sweep; rewriting it brought it down to 14,362 MB (its indexes from 2,047 to 761 MB), and `things_snaps` from 294 to 59 MB.

Two ways to rewrite a table:

* **`pg_repack` (online).** The [pg_repack](https://reorg.github.io/pg_repack/) extension rebuilds a table and its indexes into a new copy while Ditto keeps reading and writing; it takes an exclusive lock only briefly, at the start and at the end. It needs the extension in the database (`CREATE EXTENSION pg_repack;`), the `pg_repack` client of the same version, run as a superuser (managed services that offer pg_repack describe how to run it with their admin role), a primary key on the table (every Ditto table has one), and free disk for a full copy of the table and its indexes plus the changes made meanwhile. For example (with your connection options):

  ```shell
  pg_repack --dbname=ditto --table=ditto.things_journal
  ```

* **`VACUUM FULL` (offline).** Built into PostgreSQL. It rewrites the table and its indexes under an exclusive lock: every read and write of the table waits until it is done, so writes and recoveries of the entities in that table stall and time out meanwhile (for `things_journal`: every change of a thing). Plan a maintenance window. It needs the same free disk as `pg_repack`.

  ```sql
  VACUUM (FULL, VERBOSE, ANALYZE) things_journal;
  ```

Both keep the table's storage parameters. Do not run either during a Ditto upgrade that changes the schema. The search index can also be rebuilt from scratch instead: point the search service at a new, empty database and let it re-index, see [Prerequisites, migration and known limitations](#prerequisites-migration-and-known-limitations).

## Further reading

* [Operating - Configuration](operating-configuration.html)
* [Architecture Overview](architecture-overview.html)
