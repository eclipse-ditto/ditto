---
title: Operating - PostgreSQL
tags: [installation]
keywords: operating, postgresql, postgres, database, schema, upgrade, self-heal, vacuum, autovacuum, pgbouncer, tls, monitoring, tuning, indexes
permalink: operating-postgresql.html
---

Runtime behaviour of the PostgreSQL backends that matters when you operate them: what happens at every start, how the
schema is upgraded, the schema self-heal, the connection budget and PgBouncer, TLS, the persistence ping, the search
backend's tunables and indexes, the differences from MongoDB, and vacuum, table bloat and disk space. How to install
and activate the backends is described in [Running Ditto on PostgreSQL](installation-postgresql.html).

{% include callout.html content="**TL;DR**: The schema is checked at every start and upgraded in place; a missing table is re-created by the persistence services while they run (self-heal, which masks a wiped database). Size all connection pools together against `max_connections`, counting the extra pods of a rolling update, and use `track_extra_parameters` behind PgBouncer. Deleted rows need vacuum: the schema sets aggressive autovacuum parameters on the journal and snapshot tables, and a mass prune calls for a manual `VACUUM` afterwards." type="primary" %}

## Start-up and schema lifecycle

### Schema check at every start

Each PostgreSQL-backed service checks its schema at start, in one transaction. When the stored schema version and checksum match the code and every table and index of the schema exists, it runs no DDL, so it takes no table lock that writes or index builds conflict with. Otherwise (first start, a schema upgrade, or a missing table or index) it runs the schema DDL, which re-creates what is missing; each statement then waits at most 500 ms for a table lock (`lock_timeout`). A start that meets a conflicting lock — for example a running `CREATE INDEX CONCURRENTLY`, a long transaction or an autovacuum of that table — fails after about half a second without changing anything and is retried at the next start, instead of stalling writes. As a consequence, a manually dropped table or index is re-created at the next start, but a table storage parameter changed by hand (for example `ALTER TABLE … RESET (autovacuum_vacuum_scale_factor)`) is not re-applied until the next schema version upgrade or a [self-heal](#schema-self-heal) that runs the DDL; set it back by hand if needed (see [Autovacuum settings applied by the schema](#autovacuum-settings-applied-by-the-schema)).

### Waiting for PostgreSQL at start

A PostgreSQL-backed service (policies, things, connectivity, and things-search on PostgreSQL) that cannot reach PostgreSQL when it starts retries its schema check with backoff (1 s doubling to 10 s, ±20 % jitter, one WARN per attempt) for up to `ditto.postgresql.bootstrap.retry.max-duration` (`POSTGRES_BOOTSTRAP_RETRY_MAX_DURATION`, default `60s`; `0s` waits without limit). It retries only when the connection is refused, times out or is reset, the host name does not resolve yet, or the server answers that it is starting up or shutting down (SQLSTATE `57P03`, `57P01`, `57P02`) or has no free connection slot (`53300`). Everything else fails the start at once: schema drift, an incompatible schema version or an unsuitable search database, TLS and certificate errors, wrong credentials or a missing role (`28000`, `28P01`), an unknown database (`3D000`), PgBouncer's own errors (`08P01`), a DDL lock timeout (`55P03`), and an attempt that connects but does not finish within `ditto.postgresql.bootstrap.attempt-timeout` (`POSTGRES_BOOTSTRAP_ATTEMPT_TIMEOUT`, default `60s`): usually another session holds the schema advisory lock, and the error names the `pg_locks` query that shows it; behind PgBouncer a PostgreSQL that is down looks the same. When the start finally fails, the service stops (coordinated shutdown, exit code 1), so that Docker Compose (`restart`) or Kubernetes restarts it instead of leaving it running without its root actor; `ditto.postgresql.bootstrap.exit-on-failure = false` (`POSTGRES_BOOTSTRAP_EXIT_ON_FAILURE`) keeps the JVM running. A shutdown (SIGTERM) during the wait ends it at once. `ditto.postgresql.bootstrap.retry.enabled = false` (`POSTGRES_BOOTSTRAP_RETRY_ENABLED`) makes a single attempt. On Kubernetes, keep `max-duration` below the liveness budget: with the Helm chart's probes (liveness from 60 s, 6 failures 10 s apart) a pod is restarted about 110 s after its container started, and the wait only begins once the service has joined the cluster. The wait covers the start only; an outage while the service runs is handled as before.

### Plugin wiring check at start

Before the schema check, a PostgreSQL-backed persistence service (policies, things, connectivity) verifies that every journal and snapshot-store plugin its configuration auto-starts is a PostgreSQL plugin (the provider's own plugin IDs and the effective `pekko.persistence.*.auto-start-*` lists). An overlay that still wires the MongoDB plugins is logged as `Persistence backend mismatch ...`, naming the plugin, its class and the expected classes, and the service stops under the same `exit-on-failure` rule as a failed schema check (exit code 1), so that it is restarted until the configuration is fixed. On success one `INFO` line names the verified plugin IDs. The MongoDB backend runs no such check.

### Schema upgrades

Every PostgreSQL-backed component keeps one row in `schema_version`: things, policies and connectivity share the row
`ditto-postgres-persistence`; the optional search backend has its own row `ditto-postgres-search`. The row holds the
schema `version`, the `checksum` of the DDL, and `min_compatible_version`, the oldest code version that can still run
against that schema.

The schema check at start runs in one transaction under a cluster-wide advisory lock. It reads the row **before** it
runs any DDL and decides:

| Stored row                                       | What the starting instance does                                                                                                                                                                                                                                                                                     |
|--------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| none (first start)                               | creates the schema, verifies the catalog, inserts the row                                                                                                                                                                                                                                                           |
| `version` older than the code                    | upgrade: runs its idempotent DDL, verifies the catalog, advances the row (including its own `min_compatible_version`)                                                                                                                                                                                               |
| `version` equal to the code                      | a checksum mismatch refuses to start (drift). Checksum matches: if every table and index its DDL creates exists, runs **no** DDL (no lock on the component's tables), verifies the catalog and rewrites the row unchanged; otherwise runs its idempotent DDL (re-creating what is missing) and verifies the catalog |
| `version` newer, `min_compatible_version` ≤ code | **compatible newer** (rolling upgrade in progress): runs **no** DDL and does **not** write the row; only verifies the catalog facts it needs (primary keys, column types, `COLLATE "C"`; extra columns or extra/dropped indexes are fine); logs an INFO line naming both versions                                   |
| `version` newer, `min_compatible_version` > code | refuses to start; the message names `min_compatible_version`                                                                                                                                                                                                                                                        |

Every start (and self-heal) that runs DDL first sets `SET LOCAL lock_timeout = '500ms'` in its transaction, after the
advisory lock: each DDL statement waits at most 500 ms for a table lock, below PostgreSQL's default `deadlock_timeout`
(1 s). A start that meets a conflicting lock — a running `CREATE INDEX CONCURRENTLY` or `DROP INDEX CONCURRENTLY`
(per-path search indexes), a long transaction, or an autovacuum of that table (a lock wait shorter than
`deadlock_timeout` does not make PostgreSQL cancel the autovacuum) — fails with SQLSTATE `55P03`, changes nothing, logs
a WARN naming the component, and is retried at the next start (the failed start stops the service, see
`bootstrap.exit-on-failure`, and its orchestrator restarts it). On large, busy tables a schema upgrade may therefore
need several start attempts; cancelling the blocking autovacuum worker (`pg_cancel_backend`) lets the next one through.

A row written before the `min_compatible_version` column existed has it `NULL`, which counts as
`min_compatible_version = version`. The first start of an image with this check adds the column and fills it in: the
schema check inspects the catalog first and issues `ALTER TABLE schema_version ADD COLUMN IF NOT EXISTS
min_compatible_version INT` only while the column is missing. That statement is not part of the checksummed DDL, so no
stored checksum changes. This one-time `ALTER TABLE` needs **ownership** of `schema_version`. With the default
provisioning the DDL role created the table and owns it. If a component's DDL role does not own `schema_version`, start
a component whose role owns it first, or add the column once as the owner. Once the column exists, starts and heals need
`CREATE` on the schema (every start and heal issues `CREATE TABLE IF NOT EXISTS schema_version`, and PostgreSQL checks
the schema privilege even when the table exists) and `SELECT`, `INSERT`, `UPDATE` on `schema_version`, no ownership.

**Rolling upgrades.** As soon as the first upgraded instance advances the row, the not-yet-upgraded instances run
against the newer schema. Instances that keep running are unaffected either way. A not-yet-upgraded instance that
**restarts** mid-roll keeps starting if the new version declares it compatible (`min_compatible_version` ≤ its version),
and refuses to start if it does not. Then complete the roll across all services that share the row promptly, and do not
pin one service to an older image across such a bump. Images built before `min_compatible_version` existed do not read
it: they treat any newer stored version as a downgrade, and their self-heal still runs their own DDL. So during the
first schema bump after this change, an instance still on such an older image refuses to start if it restarts mid-roll.
Roll every instance to an image with this check first (that roll needs no schema bump); later bumps then follow the
table above.

**Connectivity's remember-entities store.** Connectivity's cluster sharding remembers the ids of the running connections
in an event-sourced store: on PostgreSQL in the `connection_remember_*` tables (schema version 4), as on MongoDB in its
`connection_remember_*` collections. A connectivity cluster set up with a pre-release version of the PostgreSQL profile
kept that state in Pekko's distributed-data (`ddata`) store instead. The two stores know nothing of each other, so
switch with a **full restart of all connectivity instances**, not a rolling one: the first start on the event-sourced
store remembers nothing yet and reopens the connections from their journal (connectivity's reconnect at start), while a
rolling update would leave shards on new instances without the ids the old instances still hold in `ddata`.

**Recovery from same-version drift** (manual DDL edits): reconcile the DDL, then
`UPDATE schema_version SET checksum = '<expected-from-the-start-error>' WHERE component = 'ditto-postgres-persistence';`
(use `ditto-postgres-search` for the search row). With the provisioning script's roles the table is in the schema
`ditto`, which is on the Ditto roles' `search_path` but not on a superuser's: qualify it as `ditto.schema_version` when
you run the statement as someone else.

Indexes whose plain build would block writes on a populated table are not part of the DDL: the search service builds
them `CONCURRENTLY` after the schema check (the per-path indexes and the system index `sf_multi`), see
[Per-path search indexes](#per-path-search-indexes-optional).

## Schema self-heal

Policies, things and connectivity on PostgreSQL re-create a missing table while they run, as MongoDB creates a missing collection on first use. When a statement fails because a table does not exist (SQLSTATE `42P01`), the service checks the persistence schema again, runs its DDL where needed and then retries the statement once. The DDL is idempotent (`CREATE … IF NOT EXISTS` plus the schema's table storage parameters), so it re-creates whatever table or index of the persistence schema is missing, for all entity types, and re-applies the schema's storage parameters to every table of the schema (setting back manual changes, see [Autovacuum settings applied by the schema](#autovacuum-settings-applied-by-the-schema)); existing data is not touched. This applies to every persistence statement of these services: journal writes, snapshots, recoveries, the read-journal streams (persistence ping, background cleanup, snapshot streaming) and purges. Self-heal is on by default: `ditto.postgresql.schema.self-heal` (`POSTGRES_SCHEMA_SELF_HEAL`).

{% include note.html content="Self-heal **masks data loss**. If the database is wiped, re-created or restored empty under running services, they carry on with empty tables: entities whose data is gone no longer exist, and new writes succeed. MongoDB behaves the same when its collections are dropped. To have the services fail instead, disable self-heal (see below), and in any case alert on the self-heal metric." %}

How a heal works:

* It runs in one transaction under the same advisory lock as the schema check at start, so heals and starts of all instances are serialised, and concurrent misses in one instance share one heal.
* It follows the start's rules for skipping the DDL: it runs no DDL while the stored `schema_version` row has the code's version and checksum and every table and index exists, and never against a newer stored schema version (during a rolling upgrade: with a compatible newer schema it changes nothing and the statement is retried once; with an incompatible one it fails). Unlike a start, it does not refuse a stored row with the code's version but a different checksum; it runs the DDL. Its DDL waits at most 500 ms for each table lock (`lock_timeout`).
* It does not verify the catalog and never writes the `schema_version` row; after a wiped database, the next start writes the row again.
* It connects like the schema check at start: as the DDL role (`POSTGRES_DDL_USER`, `POSTGRES_DDL_PASSWORD`) if one is configured, otherwise as the runtime role, on one short-lived connection outside the pool. A role that may not create tables in the schema, such as `ditto_app` from [`deployment/postgres/provisioning/01-roles-and-grants.sql`](https://github.com/eclipse-ditto/ditto/blob/master/deployment/postgres/provisioning/01-roles-and-grants.sql), makes every heal fail with SQLSTATE `42501`.
* When a heal fails (missing privileges, a lock timeout, an incompatible newer schema), the statement fails with the original `42P01` error. A heal is not retried; the next statement that meets the missing table starts a new one.

**Things-search does not self-heal.** While a search table is missing, its statements fail. The schema check at the next start of the search service re-creates the tables, and background sync then indexes the things again, as on a new database.

**Disabling it.** With `ditto.postgresql.schema.self-heal = false` (`POSTGRES_SCHEMA_SELF_HEAL=false`), a statement on a missing table fails with the `42P01` error: the affected persistence actors fail and their requests get errors, instead of the services continuing on empty tables. Disable it in production setups that prefer to fail loud, for example where a missing table can only mean an accidental `DROP`, a wrong database or a failed restore. The schema check at start re-creates missing tables whatever this setting says, so a restarted service starts on empty tables either way: check the database before you restart the services.

**Monitoring.** Each heal that ran DDL increments the counter `ditto_persistence_schema_self_heal` (tag `engine="postgres"`; on the Prometheus endpoint `ditto_persistence_schema_self_heal_total`) by one. A heal that ran no DDL and a heal that failed are not counted. Any increase means that a table disappeared under a running service; alert on it, for example with `increase(ditto_persistence_schema_self_heal_total[15m]) > 0`. After a wiped database the `schema_version` row is gone too, so until a restart writes it again every heal runs the DDL: expect the WARN and an increase from each instance whose statements met a missing table, even when another instance has already re-created the tables.

**Recognising a heal in the logs:**

* WARN `Missing PostgreSQL table detected; recreated the schema of component 'ditto-postgres-persistence' (self-heal). This masks a wiped/absent database — set ditto.postgresql.schema.self-heal=false to fail loud instead.` (logger `…postgres.client.PostgresSchemaHealer`): the heal re-created what was missing.
* WARN `PostgreSQL schema self-heal failed; the original missing-table error will surface unchanged.` with the cause, for example `permission denied for schema …` (`42501`) or a lock timeout (`55P03`).
* WARN `PostgreSQL schema self-heal for component 'ditto-postgres-persistence' gave up after waiting 500ms for a table lock (lock_timeout); nothing was changed. Another session holds a conflicting lock on one of the component's tables, e.g. a running CREATE INDEX CONCURRENTLY or DROP INDEX CONCURRENTLY, a long transaction or an autovacuum.` (logger `…postgres.client.schema.PostgresSchemaManager`), followed by the "self-heal failed" WARN above: the heal met a lock and changed nothing.
* INFO `PostgreSQL schema self-heal for component 'ditto-postgres-persistence': missing [things_journal]; running the component DDL.` (logger `…postgres.client.schema.PostgresSchemaManager`): single tables or indexes were missing while the `schema_version` row was current; the heal re-created them (the WARN of the first line follows).
* INFO `PostgreSQL schema self-heal for component 'ditto-postgres-persistence': every table and index of the schema exists; running no DDL.` (logger `…postgres.client.schema.PostgresSchemaManager`): the tables were already back, for example because another instance healed first. This needs a current `schema_version` row, so it appears when single tables were dropped, not after a wiped database. An INFO line that names a newer, compatible stored schema version means the heal ran no DDL during a rolling upgrade.

## Connection pool

### Connection budget

Each instance of a PostgreSQL-backed service holds one connection pool: the journal, snapshot store and read journal of policies, things and connectivity share it, and things-search on PostgreSQL has its own. A pool holds at most `ditto.postgresql.pool.max-size` connections (`POSTGRES_POOL_MAX_SIZE`, default `25`). The pools of different services and replicas do not coordinate, so size them together, counting the extra pods of a rolling update: the sum over policies, things, connectivity and things-search (when on PostgreSQL) of `(replicas + maxSurge) × max-size`, plus reserved connections, must not exceed PostgreSQL's `max_connections`. `maxSurge` is the number of extra pods a Deployment starts while it rolls: the Helm chart rolls these four services with `maxSurge: 1` and `maxUnavailable: 0`, and a `helm upgrade` that changes all four rolls them at the same time; while the old pod hands its entities over, its pool is still open, and the new pod recovers them with a pool of its own. Reserved connections are `superuser_reserved_connections`, monitoring, `psql`, and, per service instance, the connections Ditto opens outside its pool: a DDL connection for the schema check at start and the schema self-heal, and in things-search the per-path index maintenance at every start (two connections while an index builds). Example: two Kubernetes nodes with one replica each of the four services and `max_connections = 300`, about 20 of them kept for administration: 4 × 25 = 100 in steady state and 4 × (1 + 1) × 25 = 200 during an upgrade, both ≤ 280; scaling things and policies to 3 replicas and things-search to 2 gives (3 + 3 + 1 + 2) × 25 = 225 in steady state but (4 + 4 + 2 + 3) × 25 = 325 during an upgrade, which does not fit; at `max-size = 20` the same replicas take 180 and 260. A stock PostgreSQL (`max_connections = 100`, 3 of them superuser-reserved) holds the three persistence services at one replica each only in steady state (75): rolling them takes up to 150 at once, and still 100 one at a time. So for a stock server, and for things-search or more replicas, raise `max_connections`, lower `max-size` per service or put PgBouncer in front. Beyond `max_connections`, PostgreSQL refuses connections (SQLSTATE 53300, "too many clients"), and recoveries and writes fail. A pool opens connections on demand, at most `max-size` at a time, so a burst can take its whole share at once; idle connections are closed after 30 minutes. Override the size per service with `POSTGRES_POOL_MAX_SIZE` (the pending-acquire queue follows at 2 × `max-size`), with Helm via the service's `extraEnv`:

```yaml
things:
  extraEnv:
    - name: POSTGRES_POOL_MAX_SIZE
      value: "40"
```

Raise a service's `max-size` only when its pool is the measured bottleneck (`ditto_persistence_pool_pending` above 0 for long, "PostgreSQL pool overload" for journal writes, `PoolAcquirePendingLimitException` for recoveries, reads and search, or acquire-timeout errors while PostgreSQL has headroom), and re-check the sum first. `25` is a starting point; confirm production sizing by load testing. For many replicas, put PgBouncer in transaction mode in front of PostgreSQL, see [Scaling out with PgBouncer](#scaling-out-with-pgbouncer).

### Circuit breakers and overload

The PostgreSQL journal and snapshot-store plugins use the MongoDB plugins' Pekko circuit-breaker settings and the same environment variables: journal 5 failures, 5 s call timeout, 3 s reset (`BREAKER_MAXTRIES`, `BREAKER_TIMEOUT`, `BREAKER_RESET`); snapshot store 5 failures, 10 s call timeout, 6 s reset (`SNAPSHOT_BREAKER_MAXTRIES`, `SNAPSHOT_BREAKER_TIMEOUT`, `SNAPSHOT_BREAKER_RESET`). The Helm chart always sets these variables (`<service>.config.mongodb.journalCircuitBreaker` / `snapsCircuitBreaker`), so with Helm its values apply to either backend; the chart's defaults are journal 10 failures, 10 s, 5 s and snapshot store 10 failures, 20 s, 8 s. A borrow that the connection pool rejects (`pool.pending-acquire-max-size` reached) or that times out counts as a breaker failure; every recovery starts with a snapshot load, so under overload the snapshot-store breaker usually opens first, and while it is open every recovery in that service fails at once. On either backend, a request whose entity cannot be recovered or written because of overload is answered as on MongoDB: the persistence actor stops, and the command it could not answer fails with `enforcement.timeout` (HTTP 503) once the supervisor's ask timeout expires (5 s for things and policies, 50 s for connections, longer while the entity recovers); a thing create whose implicit policy could not be created fails with `things:thing.notcreatable` carrying the policy error's status. Retry such requests later.

### Scaling out with PgBouncer

When the sum of the pools no longer fits, put PgBouncer in **transaction** mode between Ditto and PostgreSQL: an idle
Ditto connection then holds no server connection, so many replicas share a smaller server pool. Ditto's defaults are
safe under transaction pooling (`pool.fetch-size = 0`: no server-side cursors; `pool.prepared-statement-cache-queries = 0`:
unnamed statements). Set `ditto.postgresql.pooler-mode = transaction` (`POSTGRES_POOLER_MODE`) for the startup
cross-check, list Ditto's startup parameters in `track_extra_parameters` as recommended in
[Statement timeout and PgBouncer](#statement-timeout-and-pgbouncer), and give the search index builds a direct URI
(`ditto.postgresql.search.path-indexes.direct-uri`, see [Per-path search indexes](#per-path-search-indexes-optional)).
Size behind PgBouncer as:

    Σ over the services of ((replicas + maxSurge) × max-size)  ≤  PgBouncer max_client_conn
    PgBouncer server pool + reserved connections  ≤  max_connections

where the server pool is PgBouncer's `default_pool_size` (plus `reserve_pool_size`) per database and role. A
transaction that finds no free server connection waits inside PgBouncer for up to `query_wait_timeout`. Ditto's 4 s
acquire budget does not see that wait; when it outlasts the callers' 5 s deadline, a write can commit after its caller
was told it failed. Either keep the sum within the server pool (PgBouncer then never queues), or lower
`query_wait_timeout` below 5 s.

## Statement timeout and PgBouncer

`ditto.postgresql.statement-timeout` (`POSTGRES_STATEMENT_TIMEOUT`, default `60s`) is sent as a connection startup parameter; things-search also sends `plan_cache_mode` (`ditto.postgresql.force-custom-plan`, on by default for search). PgBouncer, in session and transaction mode, refuses the connection unless each such parameter is listed in `track_extra_parameters` (its value then applies) or in `ignore_startup_parameters` (its value is then dropped). **Use `track_extra_parameters`:** PgBouncer then applies Ditto's values to the server connection it hands out, as on a direct connection, while `ignore_startup_parameters` drops them silently. The driver also always sends `extra_float_digits`, which must be listed in `ignore_startup_parameters` in either case. So configure PgBouncer with `ignore_startup_parameters = extra_float_digits` and `track_extra_parameters = statement_timeout,plan_cache_mode`. This was verified with PgBouncer 1.26.0; how PgBouncer tracks a parameter that PostgreSQL does not report to clients, such as `statement_timeout`, may differ between versions. For the case in which the value is dropped (`ignore_startup_parameters = extra_float_digits,statement_timeout`), the provisioning script [`deployment/postgres/provisioning/01-roles-and-grants.sql`](https://github.com/eclipse-ditto/ditto/blob/master/deployment/postgres/provisioning/01-roles-and-grants.sql) gives the runtime role `ditto_app` a `statement_timeout` default of `60s`, which PostgreSQL applies to every server session opened as that role; PgBouncer must therefore log in as the client's role (no `user=` in its `[databases]` entry; an `auth_user=` is fine). Because of that default, `statement-timeout = 0` (no startup parameter) means "use the role default", not "no timeout". The DDL role deliberately has no such default, so the schema check at start and per-path index builds are not cut off by it; without a separate DDL role (`POSTGRES_DDL_USER`), the schema check runs as the runtime role and its default applies. Details, including how to lift the timeout: the runbook [`deployment/postgres/README.md`, section "Statement timeout: role default and PgBouncer"](https://github.com/eclipse-ditto/ditto/blob/master/deployment/postgres/README.md#statement-timeout-role-default-and-pgbouncer).

Which of the two a statement runs under depends on the path to PostgreSQL:

| Path                                                                                                                           | Timeout in effect                                                                                  |
|--------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| Direct connection                                                                                                              | the startup parameter; it overrides the role default                                               |
| PgBouncer with `track_extra_parameters = statement_timeout` and `ignore_startup_parameters = extra_float_digits` (recommended) | the startup parameter: PgBouncer applies each client's value to the server connection it hands out |
| PgBouncer with `ignore_startup_parameters = extra_float_digits,statement_timeout`                                              | the role default only: PgBouncer drops the startup parameter                                       |
| PgBouncer with `statement_timeout` in neither                                                                                  | n/a: PgBouncer refuses the connection (`unsupported startup parameter: statement_timeout`)         |

A changed role default applies to new server sessions only: after `ALTER ROLE ditto_app SET statement_timeout = …`,
open sessions keep the old value until they reconnect; behind PgBouncer, run `RECONNECT` on its admin console. Statements
that run longer than the timeout are cancelled with SQLSTATE `57014`; that includes streaming reads that are consumed
slowly, because the timeout counts across the whole stream, not per fetch. The persistence ping therefore reads its
entity ids in short statements that are read completely before the ping uses them (see [Persistence ping](#persistence-ping)).

## TLS

The r2dbc-postgresql client runs `sslMode=verify-full` by default (`ditto.postgresql.ssl.mode`, `POSTGRES_SSL_MODE`): it
validates the server certificate chain **and** the host name. The CA bundle the server certificate must chain to is
`ditto.postgresql.ssl.root-cert` (`POSTGRES_SSL_ROOT_CERT`, a PEM file). With `verify-ca` or `verify-full` and an unset
`root-cert` the service refuses to start (`ssl.allow-system-truststore` is `false` by default), instead of silently
falling back to the JVM truststore and failing at the first connection. `POSTGRES_SSL_MODE=disable` is for a local
plaintext PostgreSQL. Optional mutual TLS takes the client certificate and key through `ssl.cert` and `ssl.key`
(`POSTGRES_SSL_CERT`, `POSTGRES_SSL_KEY`, `POSTGRES_SSL_KEY_PASSWORD`); the key **must be PKCS#8**, which
r2dbc-postgresql's `sslKey` requires (convert with `openssl pkcs8 -topk8 -nocrypt -in client-pkcs1.key -out tls.key`).
If PgBouncer terminates TLS, the CA to mount is PgBouncer's: the `verify-full` checks are against whichever endpoint the
r2dbc URI hits.

With the Helm chart, `postgres.tls.*` mounts the material read-only into the policies, things, connectivity and things-search pods;
the file names line up with the `ssl.*` keys:

| Mounted file (`postgres.tls.mountPath`) | HOCON key       | r2dbc option  |
|-----------------------------------------|-----------------|---------------|
| `/etc/ditto/pg-tls/ca.crt`              | `ssl.root-cert` | `sslRootCert` |
| `/etc/ditto/pg-tls/tls.crt` (mTLS)      | `ssl.cert`      | `sslCert`     |
| `/etc/ditto/pg-tls/tls.key` (PKCS#8)    | `ssl.key`       | `sslKey`      |

```yaml
postgres:
  tls:
    enabled: true
    mountPath: /etc/ditto/pg-tls
    caCert: |
      -----BEGIN CERTIFICATE-----
      ...PostgreSQL / PgBouncer CA...
      -----END CERTIFICATE-----
    # or reference a pre-created Secret (keys: ca.crt[, tls.crt, tls.key]):
    # existingSecret: my-pg-ca
    mTLS:
      enabled: false        # set true + supply PKCS#8 clientKey for mutual TLS
      clientCert: ""
      clientKey: ""          # MUST be PKCS#8 (not PKCS#1)
```

The chart mounts the files but the client reads them only through the keys, so set
`POSTGRES_SSL_ROOT_CERT=/etc/ditto/pg-tls/ca.crt` (and the cert/key variables for mTLS) via the service's `extraEnv`,
see [Kubernetes with Helm](installation-postgresql.html#kubernetes-with-helm).

**CA rotation.** r2dbc reads the certificate **once**, when the connection factory is built, so a changed CA only takes
effect after a **restart** of the service. Rotate without downtime using a staged multi-CA bundle:

1. **Stage:** build a PEM bundle containing **both** the current and the new CA (concatenate them into one file) and put
   it in `postgres.tls.caCert` (or the `existingSecret`'s `ca.crt`).
2. **Roll the persistence pods** (`kubectl rollout restart deployment/<name>` for the policies, things and connectivity
   deployments, and things-search if it runs on PostgreSQL). After this, every pod trusts both CAs. The managed Secret's
   checksum is not wired into a pod annotation, so the restart must be explicit.
3. **Cut over the server:** install the server certificate signed by the **new** CA on PostgreSQL / PgBouncer. Clients
   already trust it (bundle from step 1).
4. **Prune:** once all servers present new-CA certificates, drop the old CA from the bundle and roll the pods again so
   they no longer trust it.

Never remove the old CA from the client bundle before every server has cut over, or pods that still present the old
certificate fail `verify-full`.

## Persistence ping

The persistence ping (policies: `always-alive` policies in ID order; connectivity: all connections in priority order; by default one entity per second) runs no statement, transaction or snapshot on PostgreSQL while it waits, so `statement_timeout` applies to one short statement at a time. The ID order reads windows of `persistence-ping.read-journal-batch-size` entity ids (default 500) and keeps the tagged ones: a window costs the same wherever it lies and however the tagged entities are spread, and a walk takes (all ids / batch size) statements and reads every journal row of the entities it examines, once per walk: with many entities, few of them always-alive, or long event histories, size the ping `interval` for that (10,000,000 policies: about 20,000 statements per walk). The priority order reads the same windows (500 ids each), keeps each tagged connection's id and priority in memory (estimated at about 100–120 bytes per connection, about 12 MB for 100,000) and pings them highest priority first once the last window is read. In ID order an entity that starts to qualify after the walk's position (the last window read) is pinged in this walk, otherwise in the next one, and one deleted before the walk reaches it is not pinged; the priority order pings the connections as of the windows that read them. A failed statement is retried from the same position with the MongoDB read journal's back-off (1 s doubling to 128 s, 10 % jitter, 10 retries for the default `interval` of `10m`; unlike MongoDB, slow failures count too, so the retries end) and logged at WARN with its SQLSTATE; when the retries are exhausted, the walk ends with a WARN and the next one starts at the next `interval`, in ID order after the last persistence id the failed walk reached (kept in memory, so after a restart from the beginning); if that continued walk fails before it reaches a new persistence id (a failure that recurs there, or an outage still going on), the position is given up and the next walk starts from the beginning. A persistence id that is no entity id is skipped with a WARN instead of failing the walk. At one entity per second, 1,000,000 always-alive policies take about 11.6 days per walk on either backend; to walk faster, raise `persistence-ping.rate.entities` (e.g. `POLICIES_PERSISTENCE_PING_RATE_ENTITIES`, `RECONNECT_RATE_ENTITIES`). Details: the runbook [`deployment/postgres/README.md`, section "Persistence ping on PostgreSQL"](https://github.com/eclipse-ditto/ditto/blob/master/deployment/postgres/README.md#persistence-ping-on-postgresql).

## Connection-secret encryption

The encryption of sensitive connection fields, the key rotation and the migration commands
(`connectivity.commands:migrateEncryption`, `migrateEncryptionStatus`, `migrateEncryptionAbort`) work on PostgreSQL as
described in [Encrypt sensitive data in Connections](installation-operating.html#encrypt-sensitive-data-in-connections):
the migration rewrites the connections' journal events and snapshots through the active persistence backend, keeps its
progress in the single-row table `connections_encryption_migration` (so it resumes after an abort or a restart) and is
paced by the same `ditto.connectivity.connection.encryption.migration.*` settings. Disabling encryption follows the same
procedure as on MongoDB ([Disabling encryption](installation-operating.html#disabling-encryption)): `encryption-enabled
= false`, no current key, the last used key as `old-symmetrical-key`, then the migration decrypts the stored data.

## Monitoring

Ditto exports no per-table database metrics; combine its own metrics and logs with PostgreSQL's statistics views:

* **Pool:** `ditto_persistence_pool_pending` above 0 for long means the pool is the bottleneck; journal writes then report
  "PostgreSQL pool overload", recoveries, reads and search `PoolAcquirePendingLimitException`. See
  [Connection budget](#connection-budget) before raising `max-size`.
* **Statement duration:** `ditto_persistence_command_duration` for Ditto's PostgreSQL statements; watch it together
  with the storage latency while large tables are vacuumed, see [Autovacuum settings applied by the schema](#autovacuum-settings-applied-by-the-schema).
* **Self-heal:** `ditto_persistence_schema_self_heal` (`_total` on the Prometheus endpoint); any increase means a table
  disappeared under a running service, see [Schema self-heal](#schema-self-heal).
* **Start-up:** a service that stops with exit code 1 right after the start failed its schema check or the plugin
  wiring check; the WARN lines name the cause, see [Start-up and schema lifecycle](#start-up-and-schema-lifecycle).
* **Search index builds:** `Building search path index …` with a progress line every 30 seconds, and the
  `pg_stat_progress_create_index` query in [Per-path search indexes](#per-path-search-indexes-optional).
* **Dead rows, vacuum and table growth:** the `pg_stat_user_tables` and `pg_stat_progress_vacuum` queries and the
  example alert thresholds in [Monitoring vacuum and table growth](#monitoring-vacuum-and-table-growth).

## Search backend

### Operational tunables

The PostgreSQL search backend adds a small set of search-specific knobs on top of the shared client block. All have safe defaults; override them only if a deployment needs to.

| Config path                                           | Default                           | Env override                                  | Purpose                                                                                                                                                                                                                                                                                                                                                                                               |
|-------------------------------------------------------|-----------------------------------|-----------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ditto.postgresql.search.reaper.interval`             | `30s`                             | `POSTGRES_SEARCH_REAPER_INTERVAL`             | How often the `delete_at` reaper attempts a tick (Postgres has no TTL index; the reaper replaces Mongo's TTL deletion).                                                                                                                                                                                                                                                                               |
| `ditto.postgresql.search.reaper.batch-size`           | `1000`                            | `POSTGRES_SEARCH_REAPER_BATCH_SIZE`           | Rows deleted per reap batch (`DELETE … FOR UPDATE SKIP LOCKED` LIMIT), bounding one statement's lock footprint.                                                                                                                                                                                                                                                                                       |
| `ditto.postgresql.search.reaper.max-batches-per-tick` | `50`                              | `POSTGRES_SEARCH_REAPER_MAX_BATCHES_PER_TICK` | Safety valve: at most this many batches per tick, so one tick's transaction is never held open against an arbitrarily large backlog.                                                                                                                                                                                                                                                                  |
| `ditto.postgresql.force-custom-plan`                  | `true` (for search)               | `POSTGRES_SEARCH_FORCE_CUSTOM_PLAN`           | Binds `plan_cache_mode=force_custom_plan` on the search JVM's pool so `wpath`-parameterized read statements re-plan against their actual bound path instead of latching onto a generic plan. Also required for the per-path indexes below to be used. Behind PgBouncer, list `plan_cache_mode` in `track_extra_parameters` (see [Statement timeout and PgBouncer](#statement-timeout-and-pgbouncer)). |
| `ditto.postgresql.search.path-indexes.paths`          | unset (not managed)               | `POSTGRES_SEARCH_PATH_INDEXES` (a JSON array) | Per-path search indexes, see [Per-path search indexes](#per-path-search-indexes-optional). Unset: none is built or dropped; `[]`: all are dropped.                                                                                                                                                                                                                                                    |
| `ditto.postgresql.search.path-indexes.direct-uri`     | `""` (use `ditto.postgresql.uri`) | `POSTGRES_SEARCH_PATH_INDEXES_DIRECT_URI`     | A direct (non-PgBouncer) r2dbc URI of the same database for building the per-path indexes. Required with `pooler-mode = transaction`.                                                                                                                                                                                                                                                                 |
| `ditto.postgresql.pooler-mode`                        | `direct`                          | `POSTGRES_POOLER_MODE`                        | Shared with the persistence services. Set it to `transaction` behind a transaction-mode PgBouncer: the per-path and system index maintenance then needs `direct-uri` and skips its work without one (see [Scaling out with PgBouncer](#scaling-out-with-pgbouncer)).                                                                                                                                  |

Pool sizing, SSL and credentials are **not** search-specific — they come from the shared `ditto.postgresql.*` client block (the same one the persistence backend uses), tuned via `POSTGRES_URI` / `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DDL_*`, `POSTGRES_SSL_MODE` and `POSTGRES_POOL_MAX_SIZE`. The search service's pool counts toward the same connection budget as the persistence services' (see [Connection budget](#connection-budget)).

### Per-path search indexes (optional)

Infix `like` patterns and all `ilike` patterns that contain a run of at least three consecutive letters or digits are served by one table-wide trigram index, `sf_trgm`, over the text values of **all** paths (a prefix-only `like` such as `abc*` is served by the B-tree index `sf_text` instead). On a path that many things carry, typically a property of every feature (`features/*/properties/…`), such a query first collects the matching values of all paths and then discards those on other paths, and the planner may additionally intersect them with a scan of every row of that path. For such hot paths you can configure a per-path trigram index: a partial index restricted to one path, which the planner uses instead of `sf_trgm` for `like`/`ilike` on exactly that path. It is the PostgreSQL counterpart of the MongoDB custom search indexes.

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

* After every successful schema check at start, the search service compares the configured list with the `sf_path_*` indexes on `search_flat`, in the background: the service starts serving right away, and until a new index is valid, queries on its path keep using `sf_trgm`.
* If nothing has to change, that costs one catalog query: no lock, no DDL.
* Otherwise one instance takes a session advisory lock (the others skip), sets `statement_timeout = 0` and `lock_timeout = 0` for its own session, and builds one index at a time with `CREATE INDEX CONCURRENTLY`. The build by itself does not block reads or writes of the running instances. The build phase and progress are logged every 30 seconds.
* Nothing is changed while the stored search schema is newer than the running code (during a rolling upgrade the newer code manages the indexes), and nothing at all when the schema check refused to start.
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

**Build time and cost.** A per-path index is much smaller than the table-wide `sf_trgm`, and `ilike` on its path is served from it instead of from the table-wide index.

* A build reads the whole `search_flat` heap twice (build and validation scan), however few rows the path has, so its duration grows with the whole table.
* A larger `maintenance_work_mem` for the DDL role speeds up GIN builds, e.g. `ALTER ROLE <ddl role> SET maintenance_work_mem = '512MB'`.
* Every write to a configured path also updates its index.

### Range filters and the system index sf_multi

A filter with a two-sided numeric range on one field (`ge` or `gt` together with `le` or `lt` on the same field), for example `and(eq(attributes/location/city,"Stuttgart"),ge(features/env/properties/temperature,-15),le(features/env/properties/temperature,-14.01))`, may start from the range instead of the equality:

* With an equality and a range in the same filter, the search counts their index entries in the same statement (the equality up to 50,000 entries, the range up to one more than the equality) and starts from the one with fewer entries; a tie keeps the equality. A two-sided range alone is planned without a range start, like every other filter: starting from a range alone would be at best as fast for a narrow range and slower for a wide one.
* When the search starts from the equality or the range, it hands each candidate thing to the rest of the filter once. That keeps the database's estimate of the candidates realistic, so it checks the remaining conditions per candidate instead of reading every row of the other condition.
* Results never change: the start only narrows the candidates, and the whole filter, including every authorization check, is still applied.
* One-sided ranges, string ranges, `features/*` fields and ranges under `or`/`not` are planned without a range start.

**Arrays.** Two range conditions may be met by different elements of an array, as on MongoDB: `[91621, 91214, 93186]` matches `and(ge(attributes/region,91700),le(attributes/region,93000))`. A range start therefore also includes every thing with more than one value at that field. These things are found with the index `sf_multi ON search_flat (wpath, thing_id) WHERE ord = 1`, which belongs to search schema version 3.

**How the index is built.**

* After a start of the search service, in the background, with `CREATE INDEX CONCURRENTLY`, by the same maintenance as the per-path indexes: one instance builds it under the session advisory lock and logs `Building system search index sf_multi (i/n)` with progress lines, and an INVALID index left by a failed or interrupted build is repaired at the next start.
* It is built whether `path-indexes.paths` is set or not; that list never governs it.
* It needs the same session-capable connection as the per-path indexes: with `ditto.postgresql.pooler-mode = transaction`, set `ditto.postgresql.search.path-indexes.direct-uri`. Without one the service logs a WARN, builds nothing, and range filters are planned without a range start.
* Until an instance sees the index valid (it checks at its start, after its own maintenance run and every minute), its range filters are planned without a range start.

**Cost.** The index holds an entry only for a field that has more than one value in a thing. Like every `CREATE INDEX CONCURRENTLY`, the build reads the whole `search_flat` heap twice.

**Do not drop it.** A dropped `sf_multi` is rebuilt at the next start; until then range filters are planned without a range start.

### Prerequisites, migration and known limitations

* **`pg_trgm` extension:** the search schema requires the `pg_trgm` extension (used for `like`/`ilike` trigram indexes). It is a *trusted* extension on PostgreSQL ≥ 13, so the DDL role creates it itself at the first start, without superuser rights, as long as it has `CREATE` on the target database (the provisioning script [`deployment/postgres/provisioning/01-roles-and-grants.sql`](https://github.com/eclipse-ditto/ditto/blob/master/deployment/postgres/provisioning/01-roles-and-grants.sql) grants it); the schema check fails with an actionable error if the role is not permitted to create it. If you install the extension by hand, put it into the schema the Ditto roles use: `CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA ditto;` — a plain `CREATE EXTENSION` as a superuser lands in `public`, which is not on the roles' `search_path`, and the trigram index build fails with `operator class "gin_trgm_ops" does not exist`; the error text of the search service names the remedy (`ALTER EXTENSION pg_trgm SET SCHEMA ditto`).
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

  On CloudNativePG, whose `initdb` default `LC_CTYPE` is `C`, set `spec.bootstrap.initdb.localeCType: C.UTF-8` (the default encoding is already `UTF8`). An existing search database that does not qualify cannot be changed in place: point the search service at a new, qualifying database and fill the index again (see [Cutover to the PostgreSQL search index](#cutover-to-the-postgresql-search-index)).
* **Migration = re-index:** the search index is a rebuildable projection of the things data; there is no copy tooling and none is needed. How to fill an empty PostgreSQL index and what to expect: [Cutover to the PostgreSQL search index](#cutover-to-the-postgresql-search-index).
* **Speeding up very large fills (optional):** every row written during the fill pays incremental maintenance on the `sf_trgm` trigram GIN index, and a bulk GIN build after the data is loaded is much faster than the same work done insert-by-insert. For very large populations an operator may `DROP INDEX sf_trgm` before the fill and recreate it once the index has caught up:

  ```sql
  CREATE INDEX CONCURRENTLY IF NOT EXISTS sf_trgm ON search_flat
      USING gin ((val_text COLLATE "C.utf8") gin_trgm_ops) WHERE val_text IS NOT NULL;
  ```

  While the index is absent, `like`/`ilike` queries fall back to scans — everything else is unaffected. **Recreate it, with `CONCURRENTLY` as above, before the next start of any search instance.** A start that finds `sf_trgm` missing runs the schema DDL, whose plain `CREATE INDEX IF NOT EXISTS sf_trgm` waits at most 500 ms for the table lock and then blocks the writes of the running instances to `search_flat` for as long as it builds; the schema check's connection carries the `statement_timeout` startup parameter (`POSTGRES_STATEMENT_TIMEOUT`, default `60s`) and the attempt is capped by `bootstrap.attempt-timeout` (default `60s`), so once the table is large enough for the build to take longer than that, every start fails and stops the service, which its platform starts again (see `bootstrap.exit-on-failure`), until the index exists again. Check `pg_index.indisvalid` after your own build, too: an INVALID `sf_trgm` left by a failed or cancelled `CREATE INDEX CONCURRENTLY` counts as present for the schema check and, unlike the per-path indexes and `sf_multi`, is not repaired by the service; drop it and build it again. This is purely an optimisation — by default GIN's `fastupdate` pending list amortises per-insert cost, and the fill works fine with the index in place.
* **`like`/`ilike` performance caveat:** a `like` pattern without a run of three consecutive letters or digits (`*ab*`, `?????`) cannot use the trigram index and is answered by a scan of the field's rows, and an `ilike` pattern without such a run reads the whole trigram index or scans — mirroring MongoDB's own unanchored-regex degradation. The slow-query log catches abuse. `ilike` on a path that many things carry is slow with the table-wide `sf_trgm` alone (the planner may add a scan of all rows of the path). Configure a [per-path search index](#per-path-search-indexes-optional) for such paths.
* **`ilike` case folding:** `ilike` lower-cases value and pattern character by character under `C.utf8`; it does not apply full Unicode case folding. So `ß` does not match `ss`/`SS` (the capital `ẞ` does match `ß`), the dotless `ı` matches neither `i` nor `I`, and the final sigma `ς` does not match `σ`/`Σ`, while the dotted `İ` (U+0130) matches `i` and the Kelvin sign `K` (U+212A) matches `k`. MongoDB's case-insensitive regex folds some of these characters differently.
* **Sort performance caveat:** sorting by any field other than `thingId` or `namespace` (an attribute, a feature property such as `sort(+features/env/properties/temperature)`, `_modified`, …) cannot be served by an index. To return any page, the search looks up the sort value of **every** thing that matches the filter and is visible to the caller, then orders them. The cost grows with the number of matching visible things, not with the page size, and neither `limit(offset,size)` nor cursor paging reduces it.
  * The cursor page costs more than the `limit` page because its resume filter is evaluated for every visible thing. Part of that cost is JIT compilation (`jit = on` is the PostgreSQL default); `jit = off` lowers it.
  * For fast sorted searches, sort by `thingId` (the default) or `namespace`. Both are index-served. Deep pages via `limit` still cost in proportion to the skipped things. Otherwise, narrow the matching set with a selective filter.
* **Mongo-only search knobs:** search settings specific to MongoDB (e.g. per-metric MongoDB index hints, custom MongoDB search indexes) are ignored (logged with a WARN) when the PostgreSQL search backend is active. Per-path PostgreSQL indexes are configured separately, see [Per-path search indexes](#per-path-search-indexes-optional).
* **Other differences from the MongoDB backend:** see [Differences from the MongoDB backend](#differences-from-the-mongodb-backend); in particular, the search service does not re-create a missing table while it runs ([Schema self-heal](#schema-self-heal)).

{% include note.html content="Custom search extensions compiled against the things-search service module need a recompile with this release: a `search-update-mapper` implementation must be re-typed to the backend-neutral write model (`org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel`), and a `search-update-observer` implementation must change its `Metadata` import to `org.eclipse.ditto.thingsearch.persistence.api.model.Metadata` (module `ditto-thingsearch-persistence-api`, same members). JARs built against earlier releases fail at runtime. The release notes list the moved types." %}

### Cutover to the PostgreSQL search index

The search index is a projection of the things data, so the cutover from a MongoDB search index to PostgreSQL copies
nothing: point the things-search service at an empty PostgreSQL database (provisioned as described in
[Running Ditto on PostgreSQL](installation-postgresql.html#provisioning-the-database)) and have the index filled from
the things service's snapshots. Two mechanisms fill it; for a large population use them in this order:

1. **Force-update, namespace by namespace.** Send the DevOps command
   [force search index update for the things of one or multiple namespaces](installation-operating.html#force-search-index-update-for-all-things-of-one-or-multiple-namespaces)
   (`common.commands:shutdown` to the background sync with the headers `force-update: true` and `namespaces: [...]`)
   for one or a few namespaces at a time. The next background-sync round (background sync must be enabled; it starts
   after `background-sync.quiet-period`, default `8m`, Helm chart `5m`) streams the snapshots of those namespaces and
   tells the search updater one update per thing, skipping the comparison with the index, the policy-revision check and
   the throttle. There is **no backpressure** on the search side: the updates are sent as fast as the snapshots stream,
   and each one creates an updater actor for its thing in the search service; the updaters then retrieve the things from
   the things service up to `stream.retrieval.parallelism` (`THINGS_SEARCH_UPDATER_STREAM_RETRIEVAL_PARALLELISM`,
   default `16`) at a time, so over the round every thing of the namespace is recovered once and its persistence actor
   stays in memory for `THING_ACTIVITY_CHECK_INTERVAL` (`2h` by default). Size the namespaces, or the
   batches of namespaces, to what the things service, the search service and PostgreSQL take, and wait for a round to
   finish (the search service logs the progress; `status.commands:retrieveHealth` on the background sync reports it)
   before you send the next. The same command without the `namespaces` header does this for every thing at once.
2. **The default background sync afterwards.** Leave the regular background sync on: round after round it compares
   every thing with its index entry and repairs what the force-update rounds missed (things changed meanwhile are
   indexed by event processing anyway). It is throttled to `background-sync.throttle.throughput` things per
   `background-sync.throttle.period` (defaults 100 per 10 s; the Helm chart sets 100 per 30 s), which is why it is not
   the tool for the initial fill: from an empty index it fills a large population in days, not hours.

Caveats:

* **The throttle is fixed at start.** `throttle.throughput` and `throttle.period` (`BACKGROUND_SYNC_THROTTLE_THROUGHPUT`
  and `BACKGROUND_SYCN_THROTTLE_PERIOD`, the latter spelled that way) are read once when the search service starts.
  `common.commands:modifyConfig` on the background sync changes `enabled`, `quiet-period`, `keep.events` and the
  backoff values (`min-backoff`, `max-backoff`, `max-restarts`, `recovery`), which are re-read at the next round; a
  throttle sent through it is shown by `retrieveConfig` but not used by the running sync. To change the throttle, set
  the variables and restart the search service.
* **Search results are incomplete until the fill is done:** a thing that is not indexed yet is not found, and search
  counts are too low. Plan the window into the cutover; the force-update rounds shorten it at the price of the load
  described above.
* The optional `DROP INDEX sf_trgm` of the previous section applies to the fill as well; mind its restart rule.
* When the things service runs on PostgreSQL too, the snapshot stream carries whole snapshots (see
  [Differences from the MongoDB backend](#differences-from-the-mongodb-backend)), so each fill round moves the full
  thing payloads.

### Background-sync and updater-stream settings — identical on PostgreSQL

The `ditto.search.updater` settings listed below are consumed by the backend-neutral service layer
(`SearchUpdaterRootActor`, `BackgroundSyncActor`, `ThingUpdater`, `PolicyModificationForwarder`, `EnforcementFlow`)
and therefore behave exactly the same whether the index lives in MongoDB or PostgreSQL. The `ditto-postgres-search`
profile does not define or override any `ditto.search.*` key, so the usual environment overrides keep working
unchanged. (`stream.persistence.with-acks-writeConcern` is a MongoDB write concern and has no effect on PostgreSQL.)

| Setting (`ditto.search.updater.…`)                                       | Env override                                                                                                    | On PostgreSQL                                                                                                                                                                                      |
|--------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `event-processing-active`                                                | `EVENT_PROCESSING_ACTIVE`                                                                                       | identical (service-level flag, not backend-specific)                                                                                                                                               |
| `background-sync.enabled`                                                | `BACKGROUND_SYNC_ENABLED`                                                                                       | identical — gates the background-sync actor's wake-up                                                                                                                                              |
| `background-sync.quiet-period`                                           | `BACKGROUND_SYNC_QUIET_PERIOD`                                                                                  | identical — start-up delay and bookmark interval; the bookmark is the single row of the `search_sync` table (the MongoDB capped-collection equivalent)                                             |
| `background-sync.tolerance-window`                                       | `BACKGROUND_SYNC_TOLERANCE_WINDOW`                                                                              | identical — compares against the thing's `_modified` timestamp, which the PostgreSQL backend stores in `search_things.t_modified` (`timestamptz`, microsecond precision)                           |
| `background-sync.throttle.throughput`, `background-sync.throttle.period` | `BACKGROUND_SYNC_THROTTLE_THROUGHPUT`, `BACKGROUND_SYCN_THROTTLE_PERIOD` (spelled so upstream)                  | identical — the pace of the comparison (defaults 100 per 10 s, Helm chart 100 per 30 s), read once at start; see [Cutover to the PostgreSQL search index](#cutover-to-the-postgresql-search-index) |
| `stream.write-interval`                                                  | `THINGS_SEARCH_UPDATER_STREAM_WRITE_INTERVAL`                                                                   | identical — the per-thing write tick lives in the updater actor; both backends receive one write per tick                                                                                          |
| `stream.policy-cache.retry-delay`, `stream.thing-cache.retry-delay`      | `THINGS_SEARCH_UPDATER_STREAM_POLICY_CACHE_RETRY_DELAY`, `THINGS_SEARCH_UPDATER_STREAM_THING_CACHE_RETRY_DELAY` | identical — enforcer-cache retry back-off in the shared enforcement flow (the policy-cache delay is parsed but currently not consumed; only the thing-cache delay drives the retry)                |

The only PostgreSQL-specific search tunables are the ones listed under [Operational tunables](#operational-tunables) (including the per-path indexes); there is no
Postgres-side override of any background-sync or stream setting.

## Differences from the MongoDB backend

The PostgreSQL backends behave like the MongoDB ones except for the following points.

* **Stored JSON is normalised.** Events and snapshots are stored as `jsonb`, which does not keep the order of object keys and rewrites numbers in exponent notation (`1E10` is stored as `10000000000`; `21.0` and integers beyond 2^53 are kept as written). Values never change, but once an entity is loaded from the database again (after a restart, after passivation, or when historical revisions or events are read), the keys of its objects, for example attributes and feature properties, can come back in a different order than they were written. MongoDB keeps the order. JSON objects are unordered: do not depend on key order.
* **U+0000 cannot be stored.** `jsonb` cannot hold the character U+0000; MongoDB can store it in string values (not in keys). For the payload of thing and policy commands this makes no difference: Ditto's API rejects a payload that contains it with HTTP 400 on both backends, before anything is stored. Connection commands are not checked, and neither are the `put-metadata` header values of thing commands (they become event metadata). MongoDB stores such a write. On PostgreSQL the journal rejects the event: nothing is stored, a WARN `Rejecting PostgreSQL journal write for pid <…> due to a SQLSTATE class-22 data exception …` is logged, the entity keeps running, and the request gets no successful answer (it fails when its ask timeout expires: 5 s for things, 50 s for connections). The same happens to any other event with U+0000 that reaches the PostgreSQL journal, for example from a custom extension.
* **Live journal queries end.** `eventsByPersistenceId`, `eventsByTag` and `persistenceIds` of the PostgreSQL read journal return the current state and complete, like their `current…` variants; they do not wait for new events. With Ditto's configuration MongoDB does the same (its realtime collection is disabled, `realtime-enable-persistence = false`), and no Ditto feature uses these queries, so this matters only for custom extensions.
* **Snapshot streaming reads whole snapshots.** When the things service runs on PostgreSQL, the snapshot stream that search background sync reads returns each thing's complete snapshot, although background sync asks only for `_revision`, `policyId` and `_modified`; MongoDB reads only the requested fields. The results are the same, but each background-sync pass reads and transfers the full snapshots. Background cleanup and the connectivity service's retrieval of all connection ids request no fields and read no snapshot payload: their pages select only the persistence id, sequence number, lifecycle and write time of each entity's newest snapshot.
* **Streaming is bound to the service's own tables.** The read journal of each service reads the tables of one entity type (`read-journal.entity`, `POSTGRES_READ_JOURNAL_ENTITY`: `things`, `policies`, `connections` or `wot`), and creating a snapshot stream of another entity type fails with a configuration error; MongoDB uses the service's configured collections. In Ditto only the things service streams snapshots, so a standard deployment is not affected.
* **Persistence ping.** On PostgreSQL the ping reads windows of entity ids in both orders (the priority order sorts the ids it collected in memory before the first ping), and every failed attempt counts towards the retry limit, while MongoDB keeps retrying an attempt that fails slowly. See [Persistence ping](#persistence-ping) and the runbook [`deployment/postgres/README.md`, section "Persistence ping on PostgreSQL"](https://github.com/eclipse-ditto/ditto/blob/master/deployment/postgres/README.md#persistence-ping-on-postgresql).
* **Pool overload fails fast.** A borrow from the connection pool fails after 4 s (`pool.max-acquire-time`, no acquire retry, a 2 s connect timeout), and borrowers beyond 2 × `max-size` waiting fail at once (a journal write reports "PostgreSQL pool overload"); for journal and snapshot writes the MongoDB persistence plugin's pool waits up to 2 minutes for a connection (`waitqueuetimeout`; Ditto's own MongoDB client, used by search and the read journal, waits up to 30 s, `maxWaitTime`). The circuit breakers use the MongoDB values and environment variables, and the failed request is answered exactly as on MongoDB; see [Circuit breakers and overload](#circuit-breakers-and-overload).
* **Late commits under overload remain possible on both backends.** A write can still commit after its caller's deadline (5 s for things and policies) expired and the caller was told it failed: on PostgreSQL when its statement is still running at the deadline (the wait for a connection ends after 4 s), on MongoDB also when it waited for a connection beyond the deadline. Under overload, a failed thing creation can also leave the policy it created behind (a retried policy creation that commits late, or a rollback whose delete is itself rejected). These paths are in Ditto's request handling, not in the backend; on PostgreSQL the pool's fast rejections and acquire timeouts trigger them.
* **Search: MongoDB-only settings** (index hints, custom MongoDB search indexes) are ignored with a WARN, see [Prerequisites, migration and known limitations](#prerequisites-migration-and-known-limitations).
* **Search: `empty()` on an array of arrays.** `empty()` also matches an attribute or feature property whose value is an array of arrays (such as `[[1,2],[3]]`): the search index stores one marker per inner array and cannot tell it from an empty array. MongoDB does not match it. RQL cannot address the inner arrays on either backend.
* **Search: purged namespaces stay findable for a while**, as on MongoDB. Search queries do not filter the things a namespace purge has marked for deletion; they disappear when they are removed: on PostgreSQL by a reaper every 30 s (`ditto.postgresql.search.reaper.interval`), at most `batch-size` × `max-batches-per-tick` (50,000 by default) per run; on MongoDB by its TTL index. A deleted thing disappears at once on both backends.
* **Search: no runtime self-heal**, see [Schema self-heal](#schema-self-heal).

## Dead rows and autovacuum

PostgreSQL does not remove a deleted row at once. The row stays in the table and in its indexes as a dead row until a `VACUUM` (normally autovacuum) processes the table; the space then becomes free for new rows of the same table, but the files keep their size. Dead rows that pile up ("bloat") make tables, indexes and backups larger and reads of the affected entities slower. This and the following sections describe how Ditto's cleanup creates dead rows, which autovacuum settings the schemas apply, the server settings that decide whether autovacuum keeps up, what to monitor, and how to get disk space back after a large prune.

The persistence services use the tables `<entity>_journal` (events), `<entity>_snaps` (snapshots) and `<entity>_journal_seq` (highest sequence number per entity) for the entities `things`, `policies`, `connections` and `wot`, plus the set `connection_remember` for connectivity's cluster-sharding remember-entities store (Pekko's own `/sharding/…` persistence ids, which Pekko prunes itself) and the single-row table `connections_encryption_migration`, which holds the progress record of the connection-secret encryption migration; every persistence service creates all sixteen. The search service uses `search_things`, `search_flat` and `search_sync`. With the provisioning script [`deployment/postgres/provisioning/01-roles-and-grants.sql`](https://github.com/eclipse-ditto/ditto/blob/master/deployment/postgres/provisioning/01-roles-and-grants.sql) the tables are in the schema `ditto`, which is on the `search_path` of both Ditto roles. Run the statements below on the primary, in the Ditto database.

### How cleanup creates dead rows

* **What is deleted.** The background cleanup of each persistence service deletes, entity by entity, the events and snapshots that are older than `history-retention-duration` and no longer needed to recover the entity (it keeps the newest snapshot older than that, and everything after it). It deletes with plain `DELETE` statements, so every deleted event or snapshot becomes a dead row.
* **Pace.** Every `interval`, each service instance gets `credits-per-batch` credits; one credit is one `DELETE` of at most `writes-per-credit` events or snapshots of one entity. An instance therefore deletes at most `credits-per-batch × writes-per-credit / interval` rows per second:

  | Service      | Configuration defaults (`history-retention-duration`, `interval`, `credits-per-batch`, `writes-per-credit`) | Rows/s per instance | Helm chart values        | Rows/s per instance |
  |--------------|-------------------------------------------------------------------------------------------------------------|---------------------|--------------------------|---------------------|
  | things       | `3d`, `3s`, `3`, `100`                                                                                      | ≤ 100               | `30d`, `1s`, `5`, `100`  | ≤ 500               |
  | policies     | `30d`, `10s`, `3`, `100`                                                                                    | ≤ 30                | `30d`, `1s`, `5`, `100`  | ≤ 500               |
  | connectivity | `30d`, `60s`, `1`, `100`                                                                                    | ≤ 1.7               | `30d`, `10s`, `1`, `100` | ≤ 10                |

  The instances of a service share the entities between them, so the cluster deletes up to the sum over its instances. The pace is a ceiling: a credit spent on snapshots, or on the last events of an entity, deletes fewer rows. The credits are the only throttle: the pace does not adapt to the database load (the `timer-threshold` setting does not currently lower it). The environment variables are `CLEANUP_HISTORY_RETENTION_DURATION`, `CLEANUP_INTERVAL`, `CLEANUP_CREDITS_PER_BATCH` and `CLEANUP_WRITES_PER_CREDIT`; the Helm chart sets the values under `<service>.config.cleanup`. The counters `cleanup_delete_events` and `cleanup_delete_snapshots` show what cleanup actually deletes.
* **At the paced rate, autovacuum keeps up.** With the schema's scale factor of 1 % (see [Autovacuum settings applied by the schema](#autovacuum-settings-applied-by-the-schema)), autovacuum starts on a journal of 35 million rows after about 350,000 dead rows: at 100 rows/s, about once an hour, and one short run brings its dead rows back down.
* **Search tables.** Every thing change replaces the thing's `search_things` row and its changed `search_flat` rows, so the search tables collect dead rows from normal updates, without any cleanup. The same monitoring applies.
* **A mass prune is an operator-induced worst case.** The following delete far faster than the paced cleanup:
  * raising the pace, in the configuration or at runtime with the DevOps command that [modifies the background cleanup configuration](installation-operating.html#modify-background-cleanup-coordinator-configuration) (it applies until the next restart);
  * shortening `history-retention-duration`, or enabling cleanup for the first time, on a database with a large backlog;
  * the [cleanup command for single entities](installation-operating.html#cleanup-events-and-snapshots-of-an-entity) sent for many entities, and [erasing the data of a namespace](installation-operating.html#erase-all-data-in-a-namespace-from-the-persistence), which deletes all of its rows at once.

  An unthrottled sweep deletes in minutes what the paced cleanup spreads over days, and leaves millions of dead rows behind at once. With the schema's settings, autovacuum runs on the journal again and again during such a sweep and finishes soon after it; with the scale factor alone and PostgreSQL's default vacuum speed it takes much longer. Neither makes the files smaller: the journal keeps its size for the events that are left. What to do after a mass prune: [After a mass prune](#after-a-mass-prune).

### Autovacuum settings applied by the schema

The schema DDL sets these storage parameters on the tables Ditto deletes from or updates heavily:

| Tables                                                                                                     | `autovacuum_vacuum_scale_factor` | `autovacuum_vacuum_cost_limit`                |
|------------------------------------------------------------------------------------------------------------|----------------------------------|-----------------------------------------------|
| `<entity>_journal`, `<entity>_snaps` for `things`, `policies`, `connections`, `wot`, `connection_remember` | `0.01`                           | `2000`                                        |
| `<entity>_journal_seq` for the same table sets                                                             | `0.01`                           | not set                                       |
| `search_things` (its TOAST table, which holds the thing documents, inherits the value), `search_flat`      | `0.01`                           | not set                                       |
| `search_sync`                                                                                              | not set                          | not set                                       |
| Not set means the server's setting; PostgreSQL's defaults:                                                 | `0.2`                            | `-1`, which means `vacuum_cost_limit` (`200`) |

* **Scale factor.** Autovacuum processes a table once its dead rows exceed `autovacuum_vacuum_threshold` (default 50) plus the scale factor times its rows, so after 1 % instead of 20 %. (PostgreSQL 18 also caps that number at `autovacuum_vacuum_max_threshold`, 100 million by default.)
* **Cost limit.** Autovacuum sleeps for `autovacuum_vacuum_cost_delay` (default `2ms`) each time it has used up its cost limit; reading a page from disk costs 2 and dirtying a page 20. With `2000` instead of the default `200`, a vacuum of a journal or snapshot table may read and write ten times as much per second, up to about 390 MiB of dirtied pages per second instead of about 39 MiB, so it finishes these large tables soon after a prune. A table with its own cost limit is left out of the sharing of the server's budget between autovacuum workers: every worker on such a table uses the whole `2000`, so with the default three workers up to three times that at once. While such a vacuum runs, it competes with Ditto's statements for I/O: event appends and snapshot writes take longer than with no vacuum running, and longer than with a vacuum at the default speed. Watch the storage latency and the duration of Ditto's PostgreSQL statements (`ditto_persistence_command_duration`) while these tables are vacuumed, and slow the vacuums down if the storage cannot take it (see "Changing them" below).
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

* **Changing them.** No Ditto configuration overrides these values: they are part of the schema DDL, whose checksum each service compares at start (see [Schema check at every start](#schema-check-at-every-start)). The checksum covers the DDL text, not the tables' current parameters, so a change made with `ALTER TABLE` is neither detected nor a reason to refuse a start. What happens to such a change:
  * A start that runs no DDL (the normal case: same schema version, every table and index present) keeps it.
  * A start that runs the schema DDL (a schema upgrade, a missing table or index), and a persistence [self-heal](#schema-self-heal) that runs it, set every parameter of the table above back to the schema's value. Parameters the schema does not set, such as `autovacuum_vacuum_cost_delay` or `autovacuum_vacuum_threshold`, stay: `ALTER TABLE … SET` only changes the parameters it names.

  So prefer the server settings. To change a single table, use a parameter the schema does not set: for example, to slow down the vacuums of a journal, raise its `autovacuum_vacuum_cost_delay` (`4` milliseconds about halves its budget) rather than lowering its cost limit, which the next DDL run would set back to `2000`. Run the statement as the owner of the table (the DDL role, e.g. `ditto_ddl`), and check the parameters again after each Ditto upgrade. Changing an autovacuum parameter does not rewrite the table and does not block reads or writes. It waits for a running manual `VACUUM` of that table, while a running autovacuum of it is interrupted (unless it runs to prevent transaction ID wraparound):

  ```sql
  ALTER TABLE things_journal SET (autovacuum_vacuum_cost_delay = 4);
  ALTER TABLE things_journal RESET (autovacuum_vacuum_cost_delay);
  ```

### Server settings for vacuum

Server settings apply to all tables of the PostgreSQL server. Set them in `postgresql.conf`, with `ALTER SYSTEM`, or, on a managed service such as Amazon RDS, Azure Database for PostgreSQL or Google Cloud SQL, in its server parameters.

* **Memory for dead rows: `autovacuum_work_mem` and `maintenance_work_mem`.** A vacuum collects the ids of a table's dead rows in memory; each time that memory is full, and once at the end, it cleans every index of the table. On PostgreSQL 16 one dead row takes 6 bytes, so the default (`autovacuum_work_mem = -1`, which means `maintenance_work_mem`, `64MB`) holds about 11.2 million dead rows, and a table with more makes the vacuum read all of its indexes more than once. Set `autovacuum_work_mem` to at least the largest number of dead rows you expect in one table times 6 bytes, with room to spare: `256MB` holds about 44 million. PostgreSQL 16 uses at most `1GB` (about 179 million dead rows) per vacuum, whatever the setting. Every autovacuum worker may use that much at the same time (three workers with `256MB`: 768 MB). PostgreSQL 17 and later store dead row ids more compactly and have no 1GB limit. `maintenance_work_mem` is also used by manual `VACUUM` and by index builds such as the per-path search indexes, so set `autovacuum_work_mem` on its own rather than raising `maintenance_work_mem` for everything.
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

### Monitoring vacuum and table growth

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

| Condition                                                                                | Example threshold                                                                    | What it indicates                                                       |
|------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| `n_dead_tup` above 5 × `autovacuum_starts_at`                                            | for more than 1 hour                                                                 | autovacuum does not keep up, or cannot remove the rows                  |
| `dead_pct` of a journal or snapshot table above 20                                       | for more than 1 hour                                                                 | bloat is building up                                                    |
| `last_autovacuum` older than 24 hours while `n_dead_tup` is above `autovacuum_starts_at` | —                                                                                    | autovacuum does not get to the table (workers busy, lock conflicts)     |
| a row in `pg_stat_progress_vacuum`                                                       | running for more than 6 hours, or `index_vacuum_count` of 2 or more on PostgreSQL 16 | vacuum too slow, or too little memory                                   |
| oldest `transaction_age`                                                                 | more than 1 hour                                                                     | a transaction holds back the removal of dead rows in the whole database |
| table plus index size                                                                    | grows by more than 20 % a week while `n_live_tup` does not                           | bloat; plan [reclaiming disk space](#reclaiming-disk-space)             |
| free disk                                                                                | less than the size of the largest table plus its indexes                             | no room left to rewrite a table                                         |

### After a mass prune

After a mass prune, vacuum and analyze the pruned tables by hand rather than waiting for autovacuum. If autovacuum has already caught up (with the schema's settings it usually does soon), this takes little time; otherwise it finishes the work at once. It also brings the planner statistics up to date, and its output shows whether something holds back the removal of dead rows. For example, after shortening the retention of things:

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

### Reclaiming disk space

Usually there is no need: once cleanup has caught up and the tables are vacuumed regularly, new rows reuse the freed space and the tables stop growing. Reclaim space when you need the disk back, for example after a one-time reduction of the retention: rewriting a pruned table and its indexes returns the space of its dead rows to the file system.

Two ways to rewrite a table:

* **`pg_repack` (online).** The [pg_repack](https://reorg.github.io/pg_repack/) extension rebuilds a table and its indexes into a new copy while Ditto keeps reading and writing; it takes an exclusive lock only briefly, at the start and at the end. It needs the extension in the database (`CREATE EXTENSION pg_repack;`), the `pg_repack` client of the same version, run as a superuser (managed services that offer pg_repack describe how to run it with their admin role), a primary key on the table (every Ditto table has one), and free disk for a full copy of the table and its indexes plus the changes made meanwhile. For example (with your connection options):

  ```shell
  pg_repack --dbname=ditto --table=ditto.things_journal
  ```

* **`VACUUM FULL` (offline).** Built into PostgreSQL. It rewrites the table and its indexes under an exclusive lock: every read and write of the table waits until it is done, so writes and recoveries of the entities in that table stall and time out meanwhile (for `things_journal`: every change of a thing). Plan a maintenance window. It needs the same free disk as `pg_repack`.

  ```sql
  VACUUM (FULL, VERBOSE, ANALYZE) things_journal;
  ```

Both keep the table's storage parameters. Do not run either during a Ditto upgrade that changes the schema. The search index can also be rebuilt from scratch instead: point the search service at a new, empty database and fill it again, see [Cutover to the PostgreSQL search index](#cutover-to-the-postgresql-search-index).

## Further reading

* [Running Ditto on PostgreSQL](installation-postgresql.html) — JARs, activation config, provisioning, environment variables, Docker Compose and Helm
* [Operating - Configuration](operating-configuration.html)
* [Operating Ditto](installation-operating.html) — background cleanup, DevOps commands, connection-secret encryption
* [`deployment/postgres/README.md`](https://github.com/eclipse-ditto/ditto/blob/master/deployment/postgres/README.md) — the operator runbook
