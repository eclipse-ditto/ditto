<!--
  Copyright (c) 2026 Contributors to the Eclipse Foundation

  See the NOTICE file(s) distributed with this work for additional
  information regarding copyright ownership.

  This program and the accompanying materials are made available under the
  terms of the Eclipse Public License 2.0 which is available at
  http://www.eclipse.org/legal/epl-2.0

  SPDX-License-Identifier: EPL-2.0
-->
# Ditto PostgreSQL persistence backend — ops runbook

Operational artifacts for the phase-2 PostgreSQL persistence backend (custom
Pekko plugin on the r2dbc driver). For the design see
`docs/superpowers/specs/postgres-persistence-backend.md` — §7 (HOCON config),
§11 (role privileges + schema). `policies`, `things` and `connectivity` use this
backend; `thingsearch` can use the separate PostgreSQL search backend
(`documentation/src/main/resources/pages/ditto/installation-extending.md`), which
shares the client and pool settings and counts toward the same
[connection budget](#connection-budget).

Vacuum, table bloat and disk space (how cleanup creates dead rows, the autovacuum
settings of the schemas, server settings, monitoring queries and alert thresholds,
`VACUUM` after a mass prune, `pg_repack` and `VACUUM FULL`) are described in
`documentation/src/main/resources/pages/ditto/installation-extending.md`, section
"Operating PostgreSQL".

Contents:

- `provisioning/01-roles-and-grants.sql` — two-role provisioning (run once as
  the database owner: a superuser, or the master user of a managed service).
- Helm TLS wiring: `postgres.tls.*` in `deployment/helm/ditto/values.yaml`,
  rendered by `templates/postgres-tls-secret.yaml` and mounted via the
  `ditto.postgres.tls.{volume,volumeMount}` helpers.

## PostgreSQL version

PostgreSQL 16 or newer (tested: 16, 17, 18). At every start the schema
bootstrap reads `server_version_num`; on an older server it logs a WARN and
still starts. Technically, the persistence backend needs PostgreSQL ≥ 10 and
the search backend PostgreSQL ≥ 13 (from 13 on `pg_trgm` is a trusted
extension, so the DDL role can create it without superuser once it has
`CREATE` on the database, which the provisioning script grants).

## Search database: encoding and locale

The PostgreSQL search backend needs a UTF8-encoded database with a UTF-8-based
`LC_CTYPE` (e.g. `C.UTF-8`, `en_US.UTF-8`) and the libc collation `C.utf8`; at
every start `things-search` checks this and refuses to start otherwise. If the
cluster's default `LC_CTYPE` is `C`/`POSIX` (CloudNativePG's `initdb` default),
create the search database with

    CREATE DATABASE <db> TEMPLATE template0 ENCODING 'UTF8' LC_COLLATE 'C.UTF-8' LC_CTYPE 'C.UTF-8';

or, on CloudNativePG, set `bootstrap.initdb.localeCType: C.UTF-8`. A query to
check a database: the installation guide's "Prerequisites, migration and known
limitations" of the PostgreSQL search backend.

## Connection budget

Every instance of a PostgreSQL-backed service opens its own connection pool:
`policies`, `things` and `connectivity` one each (journal, snapshot store and read
journal share it), and `things-search` one when it runs on the PostgreSQL search
backend. A pool holds at most `ditto.postgresql.pool.max-size` connections
(`POSTGRES_POOL_MAX_SIZE`, default `25`). The pools know nothing of each other,
so size them together against PostgreSQL's `max_connections`, counting the
extra pods of a rolling update:

    Σ over the PostgreSQL-backed services of ((replicas + maxSurge) × max-size)
      + reserved connections  ≤  max_connections

`maxSurge` is the number of extra pods a Deployment starts while it rolls. The
Helm chart rolls `policies`, `things`, `things-search` and `connectivity` with
`maxSurge: 1` and `maxUnavailable: 0` (each service's `updateStrategy`), and a
`helm upgrade` that changes all four rolls them at the same time. While the old
pod shuts down and hands its entities over, its pool is still open, and the new
pod recovers those entities with a pool of its own. So each rolling service
needs `maxSurge × max-size` connections on top of its steady state
(`replicas × max-size`).

Reserved connections are `superuser_reserved_connections` (PostgreSQL default 3),
monitoring, `psql` sessions, and the connections Ditto opens outside its pool.
Those exist per service instance, so reserve them per replica: a DDL connection
for the schema bootstrap at startup and for the schema self-heal (when a
statement hits a missing table), and in `things-search` the per-path index
maintenance, which runs at every start (one connection, two while an index
builds; to `ditto.postgresql.search.path-indexes.direct-uri` if set, else to
`ditto.postgresql.uri`).

**Worked example.** Two Kubernetes nodes, one replica each of `policies`,
`things`, `connectivity` and `things-search`, all on PostgreSQL with
`max_connections = 300`, about 20 of them kept for administration (280 left).
The chart's `maxSurge: 1` adds one pool per service while it rolls:

| Setup | Steady state | All four services rolling | Fits 280? |
|---|---|---|---|
| default `max-size = 25`, one replica each | 4 × 25 = 100 | 4 × (1 + 1) × 25 = 200 | yes |
| things and policies ×3, things-search ×2, `max-size = 25` | (3 + 3 + 1 + 2) × 25 = 225 | (4 + 4 + 2 + 3) × 25 = 325 | no (only between upgrades) |
| the same replicas, `max-size = 20` | 9 × 20 = 180 | 13 × 20 = 260 | yes |
| the former default `max-size = 100`, one replica each | 4 × 100 = 400 | 4 × (1 + 1) × 100 = 800 | no |

A stock PostgreSQL (`max_connections = 100`, 3 of them
`superuser_reserved_connections`) holds the three persistence services at one
replica each only in steady state (75): while they roll they take up to 150 if
all three roll at once, and still 100 if they roll one at a time. So a stock
server needs a larger `max_connections`, smaller per-service pools, or PgBouncer
(below), and so does adding `things-search` or replicas. Past
`max_connections`, PostgreSQL refuses new connections (SQLSTATE 53300, "sorry, too
many clients already"); recoveries and writes then fail.

**Bursts.** A pool opens connections on demand (`initial-size = 0`), at most
`max-size` at a time, each handshake bounded by `connect-timeout` (2 s). There is
no further rate limit (r2dbc-pool has no counterpart of MongoDB's
`maxConnecting`), so a burst can open a service's whole share at once. Borrowers
beyond `max-size` wait in a queue of `pool.pending-acquire-max-size` (default
2 × `max-size` = 50) for at most `pool.max-acquire-time` (4 s); beyond the queue
they fail at once: a journal write with "PostgreSQL pool overload", a snapshot
load (every recovery starts with one), a read-journal query or a search borrow
with reactor-pool's `PoolAcquirePendingLimitException`. Connections a burst
opened close after 30 minutes idle (r2dbc-pool's default, which
`max-idle-time = 0s` keeps).

**Overriding per service.** Set `POSTGRES_POOL_MAX_SIZE` on that service only
(its pending queue follows at 2 × the new value unless
`POSTGRES_POOL_PENDING_ACQUIRE_MAX_SIZE` is set), or
`ditto.postgresql.pool.max-size` in the service's activation config (see "2.
Create a service activation config" in
`documentation/src/main/resources/pages/ditto/installation-extending.md`). The
Helm chart has no dedicated value; use the service's `extraEnv`:

```yaml
things:
  extraEnv:
    - name: POSTGRES_POOL_MAX_SIZE
      value: "40"
```

Raise a value only when that pool is the measured bottleneck
(`ditto_persistence_pool_pending` above 0 for long, "PostgreSQL pool overload"
for journal writes, `PoolAcquirePendingLimitException` for recoveries, reads and
search, or acquire-timeout errors while PostgreSQL has headroom), and re-check
the sum before scaling a service out. `25` is a starting point: confirm production
sizing by load testing.

### Scaling out with PgBouncer

When the sum no longer fits, put PgBouncer in **transaction** mode between Ditto
and PostgreSQL: an idle Ditto connection then holds no server connection, so
many replicas share a smaller server pool. Ditto's defaults are safe under
transaction pooling (`pool.fetch-size = 0`: no server-side cursors;
`pool.prepared-statement-cache-queries = 0`: unnamed statements;
`PgBouncerTransactionPoolingIT` runs the persistence round trip through
PgBouncer in `pool_mode=transaction`). Set `ditto.postgresql.pooler-mode =
transaction` (`POSTGRES_POOLER_MODE`) for the startup cross-check, list Ditto's
startup parameters in `track_extra_parameters` as recommended in
[Statement timeout: role default and PgBouncer](#statement-timeout-role-default-and-pgbouncer),
and give per-path search index builds a direct URI
(`ditto.postgresql.search.path-indexes.direct-uri`). Size behind PgBouncer as:

    Σ over the services of ((replicas + maxSurge) × max-size)  ≤  PgBouncer max_client_conn
    PgBouncer server pool + reserved connections  ≤  max_connections

where the server pool is PgBouncer's `default_pool_size` (plus
`reserve_pool_size`) per database and role. A transaction that finds no free
server connection waits inside PgBouncer for up to `query_wait_timeout`.
Ditto's 4 s acquire budget does not see that wait; when it outlasts the
callers' 5 s deadline, a write can commit after its caller was told it failed.
Either keep the sum within the server pool (PgBouncer then never queues), or
lower `query_wait_timeout` below 5 s.

## 1. Provisioning order (MUST be followed in this sequence)

The order is load-bearing because PostgreSQL `ALTER DEFAULT PRIVILEGES` applies
**only to objects created after it runs** — it never back-fills existing tables.

1. **Create roles + set default privileges (as the database owner).**

   ```bash
   psql -h <pg-host> -U <database-owner> -d ditto \
        -v ddl_pw="$DITTO_DDL_PASSWORD" \
        -v app_pw="$DITTO_APP_PASSWORD" \
        -f provisioning/01-roles-and-grants.sql
   ```

   Run it as the owner of the `ditto` database: a superuser, or on a managed
   service (Amazon RDS, Azure Database for PostgreSQL, Cloud SQL) the master /
   admin user, who is no superuser; both work (verified on Amazon RDS for
   PostgreSQL 16).

   This creates `ditto_ddl` (schema owner, CREATE) and `ditto_app` (DML-only),
   makes the executing user a member of `ditto_ddl` (without it, PostgreSQL 16+
   refuses the schema handover to a non-superuser's freshly created role),
   `CREATE SCHEMA ditto AUTHORIZATION ditto_ddl`, revokes CREATE from `ditto_app`
   and `PUBLIC`, grants `ditto_ddl` `CREATE` on the database (needed to create
   the trusted `pg_trgm` extension at the search service's first start), and
   sets `ALTER DEFAULT PRIVILEGES FOR ROLE ditto_ddl … GRANT SELECT, INSERT,
   UPDATE, DELETE ON TABLES TO ditto_app`. The default-privileges statement
   **must** be set here, before the app boots — see comment in the SQL. If you
   install `pg_trgm` by hand instead, pin its schema:
   `CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA ditto;` — a plain
   `CREATE EXTENSION` as a superuser lands in `public`, which is not on the
   roles' `search_path`, and the trigram index build fails with
   `operator class "gin_trgm_ops" does not exist`.
   It also sets the role defaults `search_path = ditto` (both roles) and
   `statement_timeout = '60s'` (`ditto_app` only, see
   [Statement timeout](#statement-timeout-role-default-and-pgbouncer)).

2. **App boots → schema manager runs bootstrap DDL as `ditto_ddl`.**
   On first boot `PostgresSchemaManager` connects with the `ddl-credentials`
   (DDL role) and runs all `CREATE TABLE`/`CREATE INDEX` inside one
   `pg_advisory_xact_lock`'d transaction `[H2]`. Because step 1 already set the
   default privileges, every new table is born with the DML grant to `ditto_app`
   — no post-boot re-grant pass is needed. If PostgreSQL is not reachable yet,
   the service waits for it (`ditto.postgresql.bootstrap.retry.max-duration`,
   default 60 s) and then stops so that its orchestrator restarts it;
   authentication, unknown-database, TLS and schema errors stop it at once.
   Settings: `ditto-postgres-client.conf`, block `bootstrap`.

3. **Runtime traffic uses `ditto_app`.** The hot-path `uri` (`${POSTGRES_URI}`)
   authenticates as `ditto_app`, which has DML only — it cannot run DDL.

Notes on the role model (all intentional, see SQL comments):

- **No sequence grant.** PKs use `GENERATED ALWAYS AS IDENTITY`, whose INSERT
  needs no `USAGE ON SEQUENCE`. Do **not** use `SERIAL`/`nextval()` (which would
  require a sequence grant).
- **No TRUNCATE grant.** Cleanup deletes rows with `DELETE`, never `TRUNCATE`.

Re-provisioning an existing schema: skip `CREATE ROLE`/`CREATE SCHEMA` (they
already exist) and run the GRANT/REVOKE/ALTER block; the
`GRANT … ON ALL TABLES IN SCHEMA ditto` line covers tables that predate the
default-privileges setting. That block also sets the `statement_timeout` default
of `ditto_app` back to `60s`.

### Statement timeout: role default and PgBouncer

The script gives the runtime role a server-side statement timeout, the same value
as the `ditto.postgresql.statement-timeout` default:

```sql
ALTER ROLE ditto_app SET statement_timeout = '60s';
```

Ditto also sends `ditto.postgresql.statement-timeout` (`POSTGRES_STATEMENT_TIMEOUT`)
as a connection startup parameter. Which of the two a statement runs under depends
on the path to PostgreSQL:

| Path | Timeout in effect |
|---|---|
| Direct connection | the startup parameter; it overrides the role default |
| PgBouncer with `track_extra_parameters = statement_timeout` and `ignore_startup_parameters = extra_float_digits` (recommended) | the startup parameter: PgBouncer applies each client's value to the server connection it hands out |
| PgBouncer with `ignore_startup_parameters = extra_float_digits,statement_timeout` | the role default only: PgBouncer drops the startup parameter |
| PgBouncer with `statement_timeout` in neither | n/a: PgBouncer refuses the connection (`unsupported startup parameter: statement_timeout`) |

**Recommended: `track_extra_parameters`.** It makes PgBouncer apply Ditto's
values per client, as on a direct connection; `ignore_startup_parameters`
drops them silently, so only the role default above is left. things-search
sends a second startup parameter, `plan_cache_mode`
(`ditto.postgresql.force-custom-plan`, on by default for search), which
PgBouncer refuses, drops or applies in the same way (verified with PgBouncer
1.26.0). Configure:

```ini
ignore_startup_parameters = extra_float_digits
track_extra_parameters = statement_timeout,plan_cache_mode
```

A dropped `plan_cache_mode` matters once named prepared statements are enabled
(`prepared-statement-cache-queries` above 0): PostgreSQL may then switch the
search statements to generic plans. With the default `0`, every statement is
planned with its values anyway.

- The PgBouncer rows hold for session and transaction pooling alike. They were
  verified with PgBouncer 1.26.0; `PgBouncerStatementTimeoutIT` covers the
  `ignore_startup_parameters` case in transaction mode. PostgreSQL does not
  report `statement_timeout` to clients, and how PgBouncer tracks such a
  parameter may differ between versions: check the `track_extra_parameters` row
  on your version.
- The driver always sends `extra_float_digits` as well, which PgBouncer refuses
  in the same way unless it is listed; hence `extra_float_digits` in
  `ignore_startup_parameters` in both setups.
- **`statement-timeout = 0` does not mean "no timeout".** It sends no startup
  parameter, so the role default (60 s) applies on every path. To lift the cap:
  on a direct connection, or behind a PgBouncer with `track_extra_parameters`,
  set a large `statement-timeout`, which overrides the role default (Ditto
  cannot send an explicit `0`). Behind a PgBouncer with
  `ignore_startup_parameters` only the role default counts: run
  `ALTER ROLE ditto_app SET statement_timeout = 0` (or `… RESET
  statement_timeout`), then `RECONNECT` PgBouncer. With `statement-timeout = 0`
  that role change lifts the cap on every path.

Before this role default existed, a PgBouncer with `ignore_startup_parameters`
ran Ditto's statements without any timeout. With it, statements that run longer
than 60 s are cancelled there too (SQLSTATE `57014`), as on a direct connection.
That includes streaming reads that are consumed slowly, because the timeout
counts across the whole stream, not per fetch. The persistence ping, which is
consumed at about one entity per second, therefore reads its entity ids in
short statements that are read completely before the ping uses them (see
[Persistence ping](#persistence-ping-on-postgresql)).

- **PgBouncer must log in to PostgreSQL as the client's role.** A role default
  applies only to server sessions opened *as that role*. A `[databases]` entry
  with a forced `user=` makes PgBouncer open every server connection as that
  user, and the default of `ditto_app` never applies. Use an entry without
  `user=`, e.g. `ditto = host=<pg-host> port=5432 dbname=ditto`, and let
  PgBouncer authenticate clients via `auth_file` or `auth_query`. An `auth_user=`
  in the entry (some images generate one) is fine: it only names the role that
  runs the auth query.
- **A changed value applies to new server sessions only.** PostgreSQL reads role
  defaults when a session starts. After `ALTER ROLE ditto_app SET
  statement_timeout = …`, open sessions keep the old value until they reconnect;
  behind PgBouncer, run `RECONNECT` on its admin console.
- **The DDL role deliberately gets no timeout default.** The schema bootstrap and
  `CREATE INDEX CONCURRENTLY` builds (per-path search indexes) run as the DDL role
  and must not be cut off by a role-level timeout. The bootstrap is still bounded
  by `ditto.postgresql.statement-timeout` wherever that startup parameter reaches
  the server; a boot that runs DDL also waits at most 500 ms for each table lock
  (`lock_timeout`, see [Schema upgrades](#schema-upgrades)); the per-path index
  builds set `statement_timeout = 0` for their own session.
- **Without a separate DDL role the default also applies to DDL.** When
  `ddl-credentials` are empty (`POSTGRES_DDL_USER` unset), Ditto runs the schema
  bootstrap as the runtime role, so the runtime role's timeout default applies to
  it as well, also behind a PgBouncer with `ignore_startup_parameters`, where the
  bootstrap would otherwise run without a timeout. A schema upgrade that builds an
  index on a large table can then be cancelled. Configure a separate DDL role
  (`ditto_ddl` from this script) via `POSTGRES_DDL_USER`/`POSTGRES_DDL_PASSWORD`.
  Otherwise raise the timeout for the boot that runs such DDL and restore it
  afterwards: the role default behind a PgBouncer with
  `ignore_startup_parameters`, `ditto.postgresql.statement-timeout` elsewhere.
  Per-path search index builds are not affected either way.

### Persistence ping on PostgreSQL

The persistence ping keeps entities running that must always be started: the
policies service walks every policy tagged `always-alive` in ID order, the
connectivity service walks every connection in priority order (`persistence-ping`
in `policies.conf` and `connectivity.conf`). Both walks are throttled, by
default to one entity per second. Nothing of a walk runs on the database while
the ping waits: no statement, no open transaction, no snapshot holding back
VACUUM, and `statement_timeout` applies to one short statement at a time.
(Before, the ID-order walk read through one server-side cursor, which
`statement_timeout` cancelled after about the first 256 ids; the ping then
stopped until the service restarted.)

**ID order (policies): windows of ids.** Each statement reads the next
`read-journal-batch-size` entity ids (default 500) after the previous window,
tagged or not, and keeps the tagged ones; the statement is read completely before
its entities are pinged. A window costs the same wherever it lies and however the
tagged entities are spread, also when they are a small minority clustered in one
part of the id space (one namespace among many): a window over untagged ids
simply delivers nothing, and a walk takes (all entity ids / batch size)
statements. Measured at 1,000,000 entities with 2 events each, 500 ids per
window, warm cache: about 1 ms per window and 2–3 s for the whole walk without
the throttle, flat from the first to the last window, with every entity tagged
(heap in id order or not) and with 1 % tagged clustered at the end
(`internal/utils/persistence-bench/src/test/resources/bench/results/ping-keyset-paging-1m.md`).

What a window and a walk cost grows with the number of events each entity keeps
in the journal: a window reads the index entries of its ids and, to check the
tag, every journal row of each untagged entity in it, so a walk reads every
journal row of every entity it examines, once per walk. That matters with very
many entities of which few are always-alive. For example, 10,000,000 policies
with 500 ids per window are about 20,000 statements per walk, run one after the
other on one connection: about 20–30 s of database time with 2 events per entity
and a warm cache, up to about 160 s cold, and correspondingly more with long
event histories (hundreds of events per policy with the default history
retention). At 2 events per entity each statement stays far below
`statement_timeout`; with long histories a window that is not in the cache can
take seconds, because it reads every row of its untagged entities (measured at
250,000 things with about 140 events each, 87 % of them untagged, in an 8 GiB
container: 572 ms per window at the median and 1.04 s at most, see
`internal/utils/persistence-bench/src/test/resources/bench/results/ping-partly-tagged-walks-250k-repeat.md`): lower
`read-journal-batch-size` well before a window approaches `statement_timeout`,
and size the ping `interval` so that this read once per interval is acceptable.

**Priority order (connectivity): windows, then a sort in memory.** The priority
comes from the tags and is not stored, so no index orders the connections by it.
The walk therefore reads the journal in windows of 500 ids in id order (fixed;
`read-journal-batch-size` applies to the ID order), keeps each id that carries the
tag with the priority of its newest tagged event, and when the last window is read
sorts them, highest priority first and equal priorities in id order; only then is
the first connection pinged. Every statement costs what an ID-order window costs,
whatever share of the journal carries the tag. (Before, one statement read every
tagged row; with millions of tagged rows PostgreSQL chose a sequential scan of the
whole journal, which `statement_timeout` cancelled on every retry.) Measured:
3.3 s for 1,000,000 ids with 2 events each (2,001 statements) and 183 s for
250,000 entities with about 140 events each (501 statements, the slowest 799 ms),
in the evidence file above. The price is memory in the connectivity service: one
id and its priority per tagged connection until the walk has pinged them, about
100–120 bytes each, about 12 MB for 100,000 connections. (MongoDB keeps the same
set on the server while it sorts.)

**Changes during a walk.**

- ID order: every window reads the current state. "The walk's position" is the
  last window read, which is at most one window ahead of what has been pinged.
  An entity that starts to qualify (created, or tagged) after that position is
  pinged in this walk, otherwise in the next walk; an entity deleted before the
  walk reaches it is not pinged; no entity is pinged twice in one walk.
- Priority order: every window reads the current state, but nothing is pinged
  before the last window has been read. A connection that starts to qualify in a
  part of the id space the walk has not read yet is pinged in this walk, otherwise
  in the next one; one deleted after its window was read is still pinged (as on
  MongoDB, whose priority order is one query); none is pinged twice.

**Failures.** A failed statement (for example `57014` from `statement_timeout`,
a lost connection, or the pool's acquire timeout under overload) is retried from
the same position after an exponential back-off: 1 s, doubling up to 128 s, plus
up to 10 % jitter. The number of retries per statement follows the ping
`interval`: the binary logarithm of its seconds up to 128 s, beyond that
`6 + interval / 128 s` and at least 7, i.e. 10 retries for the default `10m`.
These are the values the MongoDB read journal uses; they are not configurable.
One difference: MongoDB stops counting an attempt that ran longer than one second
before it failed, so a query that keeps failing slowly is retried there without
end; on PostgreSQL every failed attempt counts, so the retries do end. Each failed
attempt logs a WARN with its SQLSTATE (`Page query of read-journal stream …
failed on attempt …`). When the retries are exhausted, the walk ends with a WARN
of the ping actor (`Ping iteration failed after pinging <n> …`) and the next walk
starts at the next `interval`. In ID order it continues after the last
persistence id the failed walk reached (pinged or skipped, on either backend), so
an intermittent failure does not keep a long walk from reaching its end. If the
continued walk fails before it reaches a new persistence id, it gives that
position up (WARN `… giving that position up: the next iteration starts from the
beginning`) and the walk after it starts from the beginning again. That happens
with any such failure, also a database outage that is still going on when the
continued walk starts: the entities after the position then wait one more full
walk, but pinging does not stop. A failure that recurs at the same position
cannot be walked past this way: the entities before it keep being pinged at least
every other walk, the ones after it are reached once the failure is gone. The position is kept in memory only: after
a restart of the service the next walk starts from the beginning. In priority
order a failed window is retried the same way; a walk whose retries are
exhausted has pinged nothing, and the next walk starts from the beginning. Every
completed walk logs one INFO line with the number of entities pinged and the
duration.

**Ids that are no entity ids.** A persistence id in the journal that the ping
cannot turn into an entity id (no entity-type prefix, or an id the entity type
rejects) is skipped with a WARN (`Skipping persistence id <…>`), on either
backend; the walk goes on.

**Throughput.** At one entity per second a walk takes as many seconds as there
are entities, on either backend: 100,000 always-alive policies take about 28
hours, 1,000,000 about 11.6 days. A scheduled walk that finds the previous one
still running is skipped. To walk faster, raise the rate: `rate.entities` per
`rate.frequency` of `persistence-ping` (`POLICIES_PERSISTENCE_PING_RATE_ENTITIES`
and `POLICIES_PERSISTENCE_PING_RATE_FREQUENCY` for policies,
`RECONNECT_RATE_ENTITIES` and `RECONNECT_RATE_FREQUENCY` for connections). The
defaults are unchanged.

## 2. TLS / certificate mount

The r2dbc-postgresql client runs `sslMode=verify-full` (validates the server
cert chain **and** hostname). The Helm chart mounts the CA (and optional mTLS
material) read-only into the persistence pods. File names map 1:1 to the
persistence-backend `ssl.*` HOCON keys (spec §7) that `DittoPostgresClient`
(WU4) reads:

| Mounted file (`postgres.tls.mountPath`) | HOCON key       | r2dbc Option   |
|-----------------------------------------|-----------------|----------------|
| `/etc/ditto/pg-tls/ca.crt`              | `ssl.root-cert` | `sslRootCert`  |
| `/etc/ditto/pg-tls/tls.crt` (mTLS)      | `ssl.cert`      | `sslCert`      |
| `/etc/ditto/pg-tls/tls.key` (PKCS#8)    | `ssl.key`       | `sslKey`       |

Set `POSTGRES_SSL_ROOT_CERT=/etc/ditto/pg-tls/ca.crt` (and the cert/key env vars
for mTLS) so the HOCON `ssl.root-cert`/`ssl.cert`/`ssl.key` resolve to the mount.
Keep `ssl.allow-system-truststore = false` (default) so a `verify-full`/`verify-ca`
config with an unset `root-cert` fails fast at boot instead of silently falling
back to the JVM truststore and failing at first connect `[G5]`.

If PgBouncer terminates TLS, mount the PgBouncer CA here (the `verify-full`
checks are against whichever endpoint the r2dbc URI hits).

Enable via Helm values:

```yaml
postgres:
  tls:
    enabled: true
    mountPath: /etc/ditto/pg-tls
    caCert: |
      -----BEGIN CERTIFICATE-----
      ...PG / PgBouncer CA...
      -----END CERTIFICATE-----
    # or reference a pre-created Secret (keys: ca.crt[, tls.crt, tls.key]):
    # existingSecret: my-pg-ca
    mTLS:
      enabled: false        # set true + supply PKCS#8 clientKey for mutual TLS
      clientCert: ""
      clientKey: ""          # MUST be PKCS#8 (not PKCS#1)
```

The client private key **must be PKCS#8** — r2dbc-postgresql's `sslKey` does not
accept PKCS#1. Convert with:
`openssl pkcs8 -topk8 -nocrypt -in client-pkcs1.key -out tls.key`.

## 3. CA rotation procedure

r2dbc reads the certificate **once**, when the `ConnectionFactory` is built — so
a changed CA only takes effect after a **pod restart**. Rotate without downtime
using a staged multi-CA bundle:

1. **Stage:** build a PEM bundle containing **both** the current and the new CA
   (concatenate them into one file) and put it in `postgres.tls.caCert` (or the
   `existingSecret`'s `ca.crt`).
2. **Roll the persistence pods:** `kubectl rollout restart deployment/<release>-things
   <release>-policies <release>-connectivity`. After this, every pod trusts both
   CAs. (The managed Secret's `checksum` is not auto-wired into a pod annotation,
   so the restart must be explicit.)
3. **Cut over the server:** install the server certificate signed by the **new**
   CA on PostgreSQL / PgBouncer. Clients already trust it (bundle from step 1).
4. **Prune:** once all servers present new-CA certs, drop the old CA from the
   bundle and roll the persistence pods again so they no longer trust it.

Never remove the old CA from the client bundle before every server has cut over,
or in-flight pods that still present the old cert will fail `verify-full`.

## Schema upgrades

Every PostgreSQL-backed component keeps one row in `schema_version`: things, policies and
connectivity share the row `ditto-postgres-persistence`; the optional search backend has its own row
`ditto-postgres-search`. The row holds the schema `version`, the `checksum` of the DDL, and
`min_compatible_version`, the oldest code version that can still run against that schema.

The bootstrap runs in one transaction under a cluster-wide advisory lock. It reads the row **before**
it runs any DDL and decides:

| Stored row | What the booting pod does |
|---|---|
| none (first boot) | creates the schema, verifies the catalog, inserts the row |
| `version` older than the code | upgrade: runs its idempotent DDL, verifies the catalog, advances the row (including its own `min_compatible_version`) |
| `version` equal to the code | a checksum mismatch refuses to boot (drift). Checksum matches: if every table and index its DDL creates exists, runs **no** DDL (no lock on the component's tables), verifies the catalog and rewrites the row unchanged; otherwise runs its idempotent DDL (re-creating what is missing) and verifies the catalog |
| `version` newer, `min_compatible_version` ≤ code | **compatible newer** (rolling upgrade in progress): runs **no** DDL and does **not** write the row; only verifies the catalog facts it needs (primary keys, column types, `COLLATE "C"`; extra columns or extra/dropped indexes are fine); logs an INFO line naming both versions |
| `version` newer, `min_compatible_version` > code | refuses to boot; the message names `min_compatible_version` |

Every boot (and self-heal) that runs DDL first sets `SET LOCAL lock_timeout = '500ms'` in its transaction, after
the advisory lock: each DDL statement waits at most 500 ms for a table lock, below PostgreSQL's default
`deadlock_timeout` (1 s). A boot that meets a conflicting lock — a running `CREATE INDEX CONCURRENTLY` or
`DROP INDEX CONCURRENTLY` (per-path search indexes), a long transaction, or an autovacuum of that table (a lock
wait shorter than `deadlock_timeout` does not make PostgreSQL cancel the autovacuum) — fails with SQLSTATE `55P03`,
changes nothing, logs a WARN naming the component, and is retried at the next start (the failed start stops the
service, see `bootstrap.exit-on-failure`, and its orchestrator restarts it). On large, busy tables a schema upgrade
may therefore need several start attempts; cancelling the blocking autovacuum worker (`pg_cancel_backend`) lets the
next one through.

Consequence of the same-version rule: a manually dropped table or index is re-created at the next start, but a
manually changed or reset reloption (e.g. `autovacuum_vacuum_scale_factor`) is not re-applied until the next
schema version bump or a self-heal that runs the DDL. Which storage parameters the schemas set, and which manual
changes a DDL run sets back: "Autovacuum settings applied by the schema" in the section "Operating PostgreSQL" of
`installation-extending.md`.

A row written before the `min_compatible_version` column existed has it `NULL`, which counts as
`min_compatible_version = version`. The first boot of an image with this check adds the column and
fills it in: the bootstrap checks the catalog first and issues
`ALTER TABLE schema_version ADD COLUMN IF NOT EXISTS min_compatible_version INT` only while the
column is missing. That statement is not part of the checksummed DDL, so no stored checksum changes.

Privileges: this one-time `ALTER TABLE` needs **ownership** of `schema_version`. With the default
provisioning (`provisioning/01-roles-and-grants.sql`) the DDL role created the table and owns it.
If a component's DDL role does not own `schema_version`, boot a component whose role owns it first,
or add the column once as the owner. Once the column exists, boots and heals need the same
privileges on `schema_version` as before this change (`SELECT`, `INSERT`, `UPDATE`), no ownership.

The runtime self-heal (re-creating a missing table on SQLSTATE `42P01`) follows the same rule: if the
stored version is newer, it runs no DDL. With a compatible newer schema the heal is a no-op and the
failed statement is retried once. With an incompatible one the heal fails and the original error
surfaces. With the same version and checksum it follows the boot's rule as well: it runs no DDL while
every table and index exists, and its DDL runs under the same 500 ms `lock_timeout`.
The self-heal runs in policies, things and connectivity only; things-search
re-creates a missing table at its next start. What a heal does, why it masks
data loss, and how to monitor and disable it:
`documentation/src/main/resources/pages/ditto/installation-extending.md`,
section "Operating PostgreSQL".

### Rolling upgrades

As soon as the first upgraded pod advances the row, the not-yet-upgraded pods run against the newer
schema. Pods that keep running are unaffected either way. A not-yet-upgraded pod that **restarts**
mid-roll:

- keeps booting if the new version declares it compatible (`min_compatible_version` ≤ its version);
- refuses to boot if it does not. Then complete the roll across all services that share the row
  promptly, and don't pin one service to an older image across such a bump.

**One-time window for existing databases:** images built before `min_compatible_version` existed don't
read it. They treat any newer stored version as a downgrade, and their self-heal still runs their own
DDL. So during the first schema bump after this change, a pod still on such an older image refuses to
boot if it restarts mid-roll, the same window as before. Roll every pod to an image with this check
first (that roll needs no schema bump); later bumps then follow the table above.

### Rules for schema bumps (developers)

Any change to a component's DDL changes its checksum, so it must bump the component's `VERSION`, and
each bump declares its `MIN_COMPATIBLE_VERSION` (`PostgresSchemaDescriptor#minCompatibleVersion()`):

- **Backward-compatible changes** keep the previous `MIN_COMPATIBLE_VERSION`. Examples: an index
  drop, reloptions (e.g. autovacuum settings), a new index.
- **Changes older code can't run against** set it to the new version. Examples: a column rename or
  type change, a new `NOT NULL` column without a default, dropping a column or table older code
  still uses.
- Both versions run side by side during a roll, so a "compatible" change must also let the new code
  cope with rows the old code writes.

- An index whose plain build would block writes on a populated table is not part of the DDL: it is
  built `CONCURRENTLY` after the bootstrap (search: the per-path indexes; since search schema v3 the
  system index `sf_multi`). Introducing one still bumps `VERSION` (search v3 kept v2's DDL and
  checksum, `MIN_COMPATIBLE_VERSION` 1), so that older code mid-roll leaves it to the newer code.

`CREATE TABLE IF NOT EXISTS` never changes an *existing* table's shape: a version that adds columns
to an already-created table must ship `ALTER TABLE … ADD COLUMN IF NOT EXISTS …` statements for
them, not just a changed `CREATE TABLE` block that Postgres silently skips for the existing table.

### Recovery from same-version drift

Recovery from a genuine same-version drift (manual DDL edits): reconcile the DDL, then
`UPDATE schema_version SET checksum = '<expected-from-the-boot-error>' WHERE component = 'ditto-postgres-persistence';`
(use `ditto-postgres-search` for the search row).
