# PostgreSQL Persistence and Search Backend Design Document

**Related Issue**: none yet — to be opened together with the upstream pull request (target release 4.0.0)

**Status**: Implemented on branch `feat/postgres-persistance-search` (fork); pre-PR work open, see §9
**Last Updated**: 2026-11-02

---

## TL;DR

Ditto can store things, policies and connections — and, independently, its search index — in PostgreSQL instead
of MongoDB. It is opt-in per service through two drop-in JARs and a few lines of HOCON; MongoDB stays the default and
nothing changes for API users or existing deployments.

### What It Does

| Capability                 | Description                                                                                                                                                                                              |
|----------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Pluggable persistence**  | New extension point `PersistenceBackendProvider` selects journal, snapshot store, read journal, ops                                                                                                      |
| **Pluggable search**       | New extension point `SearchPersistenceProvider` selects the whole thing-search persistence layer                                                                                                         |
| **PostgreSQL persistence** | Pekko journal / snapshot store / read journal on R2DBC, events and snapshots as `jsonb`                                                                                                                  |
| **PostgreSQL search**      | One row per thing plus a flattened side table (a row per JSON node, two per node under `features`); a trigram GIN serves `ilike` and `like` with a 3-character literal run, the per-path B-tree the rest |
| **Opt-in packaging**       | One shared base JAR (the third-party runtime the images do not already carry) + thin backend JARs; stock images carry no PostgreSQL driver                                                               |
| **Built for operation**    | Versioned, checksummed schema; rolling-upgrade safe; bounded pool waits; self-heal; PgBouncer; TLS                                                                                                       |

### Key Design Decisions

- **Seams, not forks.** Everything backend-specific a service needs is reachable through the two provider
  interfaces — the one exception being the per-entity JSONB event adapters, which Pekko wires through the profile's
  `event-adapters` HOCON (§3.3); the service actors (`*RootActor`, `*PersistenceActor`, `*OperationsActor`, search
  actors) stay backend-agnostic. Backend-neutral types moved out of the MongoDB persistence packages and out of the
  search service packages into two new API modules; a few neutral config types (`WithActivityCheckConfig`,
  `WithSnapshotConfig`, `NamespaceActivityCheckConfig`) still live in `persistence.mongo.config`.
- **JSONB, not BSON-as-bytes.** Events and snapshots are stored as `jsonb` so that SQL can read them (cleanup,
  ping, streaming, diagnostics). The known consequence is JSON normalisation (key order, number rendering).
- **Flattened side table for search.** PostgreSQL has no counterpart of MongoDB's wildcard index, so the search
  index is "what a wildcard index is internally, as rows": `search_things` (document) + `search_flat` (one row per
  JSON node, containers included, and two per node under `/features/<id>/…` — exact path and `/features/*/…` form;
  typed value columns, B-tree indexes per type and a trigram GIN).
- **Schema owned by the service, bounded by design.** DDL is idempotent, checksummed and runs under an advisory
  lock; a start with an up-to-date schema runs no DDL; each DDL statement waits at most 500 ms for a table lock;
  the search index builds use `CREATE INDEX CONCURRENTLY` outside the bootstrap transaction.
- **Fail fast inside Ditto's deadlines.** A connection borrow fails after 4 s (the supervisor's ask timeout is 5 s
  for things and policies, 50 s for connections) and the pending queue is bounded at 2 × pool size, so a write under
  overload fails before its caller gives up. The request is then answered exactly as on MongoDB: the supervisor's
  error mapping is unchanged. MongoDB's journal plugin waits up to 2 minutes (Ditto's own Mongo client 30 s).
- **MongoDB parity where it is cheap, documented difference where it is not** (§3.6, §4.7).

---

## 1. Motivation

Ditto has required MongoDB since its first release. Many operators already run — or can only get — PostgreSQL,
often managed (RDS, Azure, Cloud SQL, CloudNativePG), and want one database technology to operate, back up and
monitor. The work is meant to land upstream.

### Goals

- Things (incl. WoT validation config), policies, connectivity and thing-search run on PostgreSQL, each service
  independently of the others.
- MongoDB remains the default; nothing changes for MongoDB deployments (same images, same config, same behaviour).
- Every background mechanism works on both backends: cleanup, persistence ping, snapshot streaming (search
  background sync), namespace purge, health checks, field-level encryption of connection secrets — with the gaps
  recorded in §9 (encryption migration answers 501, snapshot decryption without a current key, whole-snapshot
  streaming, remember-entities on `ddata`).
- Production-grade operation: rolling upgrades, bounded locks, connection budgeting, TLS by default, PgBouncer.

### Non-Goals

- Data migration from MongoDB to PostgreSQL for things/policies/connections: a migrator was developed on the fork
  branch `feat/postgres-persistance-migration` (module `persistence-migrator/`) and will be maintained outside
  eclipse-ditto (commit `4d5847fae3`); it never enters this PR, and the docs say "operator's responsibility". Search
  needs no migration: it is re-indexed.
- Making the stock images carry PostgreSQL code (per-service enforcer rules and ArchUnit tests forbid R2DBC in the
  service modules).
- Beating MongoDB on every benchmark. Parity on the hot paths and honest numbers elsewhere (§8).

---

## 2. Architecture

### 2.1 The two seams

```
                     ditto.extensions.persistence-backend-provider
services ───────────────────────────────────────────────────────────────────────────┐
 things / policies / connectivity                                                   │
   RootActor ──► PersistenceBackendProvider ──► plugin IDs per entity type          │
                 │                              bootstrapSchema() (DDL, first)      │
                 │                              DittoReadJournal (streams)          │
                 │                              operations(entity) → purge ops      │
                 │                              SnapshotCodec (envelope)            │
                 │                              health check, streaming props       │
                 │                              backendFamily(), plugin classes     │
                 ├── MongoPersistenceBackendProvider   (default, in the image)      │
                 └── PostgresPersistenceBackendProvider (extension JAR)             │
                                                                                    │
                     ditto.extensions.search-persistence-provider                   │
 things-search                                                                      │
   SearchRootActor ──► SearchPersistenceProvider ──► bootstrapSchema()              │
                       │                             read / updater / aggregation   │
                       │                             SearchUpdaterFlow, bookmark    │
                       │                             health props, query builder    │
                       ├── MongoSearchPersistenceProvider   (default)               │
                       └── PostgresSearchPersistenceProvider (extension JAR)        ┘
```

Both providers follow the `DittoExtensionPoint` pattern (constructed with `(ActorSystem, Config)`); they read
`ditto.postgresql.*` and their own `extension-config`, never the `ditto.mongodb` block (the search provider has no
config block of its own and touches the Mongo-only `ditto.search.*` knobs solely to warn that they are ignored).
`PersistenceBackendProvider` also drives a boot-time **self-check** (`PersistenceBackendSelfCheck`): the
provider-declared plugin IDs, the read-journal class and every entry of the effective Pekko `auto-start-*` lists
must resolve to classes of the selected backend (an entry without a `class` is its own error), so a half-applied
overlay (Postgres provider with Mongo plugins or vice versa) fails at boot instead of on the first write. The check
throws from the root actor's constructor and no production conf sets a guardian strategy, so Pekko stops the root
actor while the JVM keeps running without shard regions or health route (a schema-bootstrap failure, by contrast,
exits the JVM — §3.2); and it runs after `bootstrapSchema()`, so such an overlay creates its schema before it dies.

### 2.2 Modules

| Module                                               | Purpose                                                                                                                                                                                          | Ships in |
|------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|
| `internal/utils/persistence-api`                     | Backend-neutral persistence contracts, configs, streaming actors                                                                                                                                 | image    |
| `thingsearch/persistence-api`                        | Backend-neutral search contracts, write model, index-document mapping                                                                                                                            | image    |
| `internal/utils/persistence` (changed)               | MongoDB provider + `MongoSnapshotCodec`; Postgres *event* adapter base `AbstractPostgresEventAdapter` (JSON only); `PostgresSnapshotAdapter` there is dead code (only a parity test uses it; §9) | image    |
| `internal/utils/postgres-client`                     | R2DBC pool, SSL, config, schema manager/healer/bootstrap, monitoring                                                                                                                             | base JAR |
| `internal/utils/persistence-r2dbc`                   | Journal, snapshot store, read journal, ops, persistence schema                                                                                                                                   | thin JAR |
| `internal/utils/search-r2dbc`                        | Search schema, flattener, RQL→SQL, read/write, reaper, path indexes                                                                                                                              | thin JAR |
| `internal/utils/postgres-*-extension` (3)            | Shade modules producing the base and the two thin JARs                                                                                                                                           | —        |
| `internal/utils/postgres-extension-it`               | IT: the three shaded JARs load in isolated class loaders over an allinone-like parent; a thin JAR without the base fails with an actionable error; runs in no CI job (§7)                        | —        |
| `internal/utils/persistence-bench`                   | PG-vs-Mongo persistence benchmark harness and evidence (to leave the PR, D6 pending)                                                                                                             | —        |
| `ide-postgres-launcher`, `deployment/postgres-local` | Local multi-service runs on PostgreSQL: IDE classpath module, IntelliJ run configs in `.run/`, overlay confs + CLI launcher, Compose for the database                                            | —        |
| `deployment/postgres`                                | Operator runbook, two-role provisioning SQL, PgBouncer notes                                                                                                                                     | —        |

### 2.3 Packaging and activation

- **Layered drop-in JARs.** `ditto-postgres-client-extension` (base, ≈3.8 MB: the `postgres-client` module itself,
  r2dbc-spi/-pool/-proxy, r2dbc-postgresql, reactor-core/-pool/-netty-core, SCRAM, netty resolver-dns/codec-dns and
  the optional native transports) plus `ditto-postgres-persistence-extension` (≈92 KB) or
  `ditto-postgres-search-extension` (≈270 KB); sizes from a local `0-SNAPSHOT` build. The images already put
  `/opt/ditto/extensions/*` on the classpath.
  A Maven enforcer execution `enforce-no-r2dbc-in-service`, declared in each of the four persistence/search service
  poms, keeps R2DBC out of those modules; `bom/pom.xml` adds `enforce-no-pekko-r2dbc-plugin`, which bans
  `org.apache.pekko:pekko-persistence-r2dbc*` project-wide.
- **One include activates a backend.** Each thin JAR ships a profile — `ditto-postgres-persistence.conf`,
  `ditto-postgres-search.conf`, both pulling in `ditto-postgres-client.conf` from the base — that swaps the
  provider extension class and defines the Pekko plugin blocks (`ditto-postgres-<entity>-journal` / `-snapshots`,
  entities `things`, `policies`, `connections`, `wot`) plus the `ditto.postgresql.*` client defaults. The
  persistence include must be a top-level `include classpath("…")` (the plugin blocks are top-level), needs the
  narrowing below and a `read-journal.entity` value (the stock service confs set it), and also switches
  `pekko.cluster.sharding.remember-entities-store` to `ddata` JVM-wide (§3.3, to be reversed by D3). The search
  include needs none of this.
- **Auto-start narrowing is mandatory.** The profile lists all four entities in Pekko's `auto-start-*`; each
  service must narrow the lists to the plugins whose dispatchers it defines (the things JVM keeps the `things`
  pair; its WoT plugins start lazily on the same dispatchers), because Pekko resolves every auto-started plugin's
  dispatcher eagerly and the other entities' dispatchers do not exist in that service.
- **Snapshot encoding follows the backend.** The services' snapshot serializers (selected by
  `ditto.extensions.snapshot-adapter`; three of the four keep their Mongo-era names `ThingMongoSnapshotAdapter`,
  `PolicyMongoSnapshotAdapter`, `ConnectionMongoSnapshotAdapter`) are backend-neutral JSON serializers; the
  provider's `SnapshotCodec` decides between the BSON and the JSONB envelope, so no per-service override is needed.
- **Dev profiles** `<service>-pg-dev.conf` (four, shipped inert in the allinone JARs) and six `.run/* (Postgres)`
  run configurations (one per service plus the compound; the gateway one carries no PostgreSQL config) exist for
  local runs only.

---

## 3. Persistence backend

### 3.1 Data model

Per entity (`things`, `policies`, `connections`, `wot`), three tables; every persistence service creates all twelve
plus the shared `schema_version`:

| Table             | Columns                                                                             | Key                         |
|-------------------|-------------------------------------------------------------------------------------|-----------------------------|
| `<e>_journal`     | `pid`, `sn`, `seq` identity, `manifest`, `tags TEXT[]`, `event JSONB`, `written_at` | `(pid, sn)` + GIN on `tags` |
| `<e>_journal_seq` | `pid`, `highest_sn`, `deleted_to`                                                   | `(pid)`                     |
| `<e>_snaps`       | `pid`, `sn`, `snapshot JSONB`, `lifecycle`, `written_at`                            | `(pid, sn, written_at)`     |

- `pid` is `COLLATE "C"` in all three tables (the catalog verifier checks it). `seq` is the monotonic offset for
  `eventsByTag`; `tags` carry Ditto's journal tags (`always-alive`, `priority-<n>`).
- `<e>_journal_seq` keeps the highest sequence number independent of journal rows, so it survives cleanup.
- The snapshot key includes `written_at` so a re-snapshot at the same `sn` is allowed; `DELETED` snapshots are
  filtered in the application after the newest-per-pid pick (schema v2 dropped the `(pid, sn DESC)` index, v3 the
  partial `lifecycle` index — neither served a query).
- Autovacuum storage parameters are set in the bootstrap transaction: scale factor 0.01 on every table (since v2);
  schema **v3** (`MIN_COMPATIBLE_VERSION = 2`) adds cost limit 2000 on the two pruned tables `<e>_journal` and
  `<e>_snaps` (§8).

### 3.2 Schema lifecycle

- One `schema_version(component, version, checksum, min_compatible_version)` table is shared by the persistence
  and the search component (the compatibility column was added later by a one-time, un-checksummed
  `ALTER TABLE … ADD COLUMN IF NOT EXISTS`, which needs table ownership). The checksum is a SHA-256 over the
  whitespace-normalised DDL statements themselves, so it cannot drift from what is executed. The bootstrap also
  verifies the live catalog (PK definition, column types, collations) against `TableContract`s, because
  `CREATE TABLE IF NOT EXISTS` would silently keep a divergent table.
- **Rolling upgrades:** the newer code declares, as `MIN_COMPATIBLE_VERSION`, the oldest code version that may still
  run against its schema, and writes it into the row. A pod whose code is older than the stored version boots only if
  the stored `min_compatible_version` is ≤ its own `VERSION`; it then runs no DDL and never writes the row. A stored
  row that declares a higher `min_compatible_version` refuses to boot. The upgrade direction is unchecked: the DDL is
  the full idempotent definition, so any older schema upgrades in one boot, but only additively. A transforming
  change (column type, rename, `NOT NULL` without default) cannot be expressed: `CREATE TABLE IF NOT EXISTS` keeps the
  old shape and the catalog verification refuses to boot, so such a change needs an out-of-band migration. Things,
  policies and connectivity share one row, so an incompatible bump is rolled across all three together, with no
  service pinned to an older image. Decision table, legacy `NULL` rows and the developer rules for choosing
  `MIN_COMPATIBLE_VERSION`: `deployment/postgres/README.md`, section "Schema upgrades".
- **Fast path:** version and checksum match and every table and index exists → no DDL, no table lock. The
  transaction-scoped advisory lock is taken on every boot before the row is read; otherwise DDL runs under it with
  `SET LOCAL lock_timeout = '500ms'`, so each DDL statement waits at most half a second for a table or index lock, a
  conflict fails the start and the next start retries. A wait for the advisory lock itself (another pod
  bootstrapping or healing) is bounded only by `bootstrap.attempt-timeout` (60 s, not retried).
- **Waiting for PostgreSQL:** classified retries (refused, DNS, starting up/shutting down, too many clients) with
  exponential 1 → 10 s back-off (×2 per attempt, ±20 % jitter) for `bootstrap.retry.max-duration` (60 s);
  everything else fails at once. A final failure runs Pekko's coordinated shutdown with reason `BootstrapFailed` —
  exit code 1 through a reason override, as Ditto services run with `exit-jvm = on` — so the orchestrator restarts
  the pod; `bootstrap.exit-on-failure = false` keeps the JVM up without its root actor instead.
- **Self-heal at runtime** (persistence services only, not search): SQLSTATE `42P01` triggers a schema check, DDL
  where needed, one retry; counted by `ditto_persistence_schema_self_heal`; `POSTGRES_SCHEMA_SELF_HEAL=false` fails
  loud instead. Documented as masking data loss, exactly like MongoDB's implicit collection creation.
- **Two roles:** DDL owner (`ditto.postgresql.pool.ddl-credentials`, `POSTGRES_DDL_USER`) vs DML-only runtime role;
  when no DDL credentials are set the runtime role runs the DDL. The bootstrap and heals open one short-lived
  connection outside the pool as the DDL role; a search index build holds its own non-pooled DDL connection for the
  whole build.

### 3.3 Events and snapshots as JSONB

- Service-specific adapters `ThingPostgresEventAdapter`, `PolicyPostgresEventAdapter`,
  `ConnectivityPostgresEventAdapter`, `WotValidationConfigPostgresEventAdapter` extend
  `AbstractPostgresEventAdapter` in `internal/utils/persistence` (JSON only, no R2DBC). Connection secrets keep the
  AES-GCM field encryption: events decrypt with the current key and fall back to `old-symmetrical-key`, also when
  only the old key is set; snapshots go through the shared `ConnectionMongoSnapshotAdapter`, which falls back to
  the old key only next to a current one and otherwise leaves the fields encrypted with a WARN (open on both
  backends, §9 D4).
- `jsonb` normalises: key order is not preserved after reload, `1E10` is re-rendered, U+0000 cannot be stored (the
  journal rejects such an event with a class-22 WARN; the API already rejects it with 400 for thing/policy payloads).
- The journal accepts only a Ditto `JsonValue` or JSON text. That is why connectivity's remember-entities store
  could not use `eventsourced` (Pekko-serialized binary events) and the profile switched to `ddata` — a decision
  now reversed, see §9 (D3/D3b).

### 3.4 Read journal and streams

- `DittoReadJournal` (neutral) replaces direct `MongoReadJournal` use in cleanup, ping, namespace ops and
  snapshot streaming. Live queries (`eventsByTag` etc.) complete instead of waiting — Ditto never relied on the
  live variants (MongoDB realtime collection is disabled).
- **Persistence ping** walks entity ids in keyset windows of 500 ids (`read-journal-batch-size` in ID order; the
  fixed `PRIORITY_WALK_WINDOW` in priority order, connectivity's default) and keeps the tagged ones; no statement,
  transaction or snapshot is held between windows (the earlier cursor walk was cancelled by `statement_timeout`
  after about 256 ids, see `deployment/postgres/README.md`). Priority order collects `(id, priority)` in memory
  first. Only the two ping walks retry, with the MongoDB read journal's back-off; slow failures count too, so
  retries end. Cleanup pages, snapshot streams and the pid lookups fail on their first error (MongoDB parity).
  Windows and retries live in `PostgresReadJournal`; what `PersistencePingActor` itself gained applies to both
  backends (§5).
- **Cleanup pages are payload-free** on PostgreSQL too: a page requesting no fields selects only pid, sn, lifecycle
  and write time of the newest snapshot (closed the C1 benchmark flag, §8). MongoDB already returned no payload
  here, so the change is PostgreSQL-only; `Cleanup` itself changed in types only.
- **Streaming is bound to one entity type** per service (`read-journal.entity`, required with no default: `things`,
  `policies` or `connections`; WoT validation config lives in the things JVM and is no read-journal consumer); only
  things streams snapshots in Ditto. Known gap: the snapshot stream returns whole snapshots although background sync
  asks for three fields.

### 3.5 Pool, overload, TLS, PgBouncer

- One pool per service JVM, shared by journal, snapshot store and read journal (the search service's JVM has its
  own instance of the same pool). Defaults: `max-size` 25, `max-acquire-time` 4 s, no acquire retry, pending queue
  2 × `max-size`, connect 2 s, idle close after r2dbc-pool's default 30 min (`max-idle-time = 0s`),
  `statement_timeout` 60 s sent as a startup parameter. Connection budget: Σ over services of
  `(replicas + maxSurge) × max-size` + reserved ≤ `max_connections`.
- The PostgreSQL plugin blocks declare the same circuit-breaker settings and env vars as the MongoDB plugins
  (`BREAKER_*`, `SNAPSHOT_BREAKER_*`); a rejected or timed-out borrow counts as a breaker failure and stops the
  persistence actor; the supervisor's error answers are unchanged from MongoDB (no PostgreSQL-specific status
  mapping).
- TLS `verify-full` by default (`POSTGRES_SSL_ROOT_CERT`), optional mTLS; `POSTGRES_SSL_MODE=disable` for local use.
- PgBouncer transaction mode works with the defaults (no server-side cursors, unnamed statements) given
  `track_extra_parameters = statement_timeout,plan_cache_mode`; session-bound work (index builds) needs a direct URI.

### 3.6 Differences from MongoDB (persistence)

JSON normalisation and U+0000 (§3.3); live journal queries complete (§3.4); snapshot stream reads whole snapshots;
streaming bound to the service's entity; ping walks windows and counts slow failures; pool fails fast (4 s) instead of
waiting 2 min; late commits after the caller's deadline remain possible on both backends (Ditto request handling, not
the backend); connection encryption *migration* commands currently answer 501 on PostgreSQL (to change, §9 D4b).

---

## 4. Search backend

### 4.1 Why a flattened side table

MongoDB serves RQL on arbitrary, undeclared JSON paths with a wildcard (`$**`) index. PostgreSQL's GIN on `jsonb` is
equality-only and cannot serve ranges, `like`, or per-type sorting on arbitrary paths. The design therefore stores
each thing twice: the document (for results, authorization and sorting) and one row per JSON node with typed value
columns, which is what a wildcard index holds internally. Object, array and null containers get a row too (they
serve `exists()`), and every node under `/features/<id>/…` is stored twice — under its exact path and under
`/features/*/…` — so the side table holds about twice the node count of the feature subtrees. A two-predicate `and`
then uses an index on *both* legs, which a MongoDB wildcard index cannot do.

### 4.2 Data model (search schema v3, `MIN_COMPATIBLE_VERSION = 1`)

| Table           | Columns (abridged)                                                                                                                                                                                  | Key / indexes                                                                                                                                                                                                                 |
|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `search_things` | `thing_id`, `namespace`, `revision`, `policy_id`, `policy_rev`, `referenced_policies`, `global_read TEXT[]`, `thing JSONB`, `policy_auth`, `features_auth`, `t_modified`, `delete_at`, `updated_at` | PK `thing_id`; `st_namespace` `(namespace, thing_id)`, `st_global_read` (GIN), `st_policy` `(policy_id, policy_rev)`, `st_referenced_pols` (GIN `jsonb_path_ops`), `st_delete_at` (partial), `st_modified`                    |
| `search_flat`   | `thing_id` FK cascade, `path`, `wpath` (wildcarded path), `f_id`, `ord`, `type_rank`, `val_bool`, `val_num NUMERIC`, `val_text` (COLLATE "C")                                                       | PK `(thing_id, path, wpath, ord)`; partial composite B-trees `sf_num`/`sf_text`/`sf_bool` on `(wpath, val_x) WHERE val_x IS NOT NULL`, `sf_exists` on `wpath`, `sf_trgm` (partial GIN trigram on `val_text COLLATE "C.utf8"`) |
| `search_sync`   | `id`, `ts`, `tag`                                                                                                                                                                                   | PK `id` — the background-sync bookmark (MongoDB: capped collection)                                                                                                                                                           |

- `wpath` is the path with feature ids replaced by `*`. Feature-subtree nodes are stored as two physical rows (exact
  path and starred `wpath`), so `features/*/properties/x` queries hit one index key; `ord` is a per-path occurrence
  counter for exploded arrays, not an array index.
- `sf_trgm` uses `COLLATE "C.utf8"` to restore non-ASCII case folding for `ilike` while eq/range/sort stay on the
  plain `C` column. Hence the start-up guard: UTF8 database, UTF-8 `LC_CTYPE`, `C.utf8` collation present —
  otherwise the service refuses to start with an actionable message. `CREATE EXTENSION IF NOT EXISTS pg_trgm` is the
  first DDL statement; a permission failure there is re-classified into the same kind of message.
- `delete_at` + a **reaper** (every 30 s, `DELETE … FOR UPDATE SKIP LOCKED` in batches of 1,000, at most 50 per
  tick, under `pg_try_advisory_xact_lock`) replace MongoDB's TTL index for namespace purges. No cluster singleton.

### 4.3 Online indexes outside the schema

- **Per-path trigram indexes** (`ditto.postgresql.search.path-indexes.paths`, the only kind is `trigram`) are the
  counterpart of MongoDB custom search indexes: partial GIN indexes on one `wpath`, named `sf_path_trgm_<sha256-16>`
  so the name is deterministic across instances. Unset list = manage nothing (existing `sf_path_*` indexes are
  listed at INFO); `[]` = drop every managed `sf_path_*` index, never `sf_multi`; an entry removed from the list is
  dropped. All search instances sharing a database must use the same list.
- **`sf_multi`** (`(wpath, thing_id) WHERE ord = 1`) is a system index: it serves the multi-valued branch of a
  two-sided range leg inside the selective-leg CTE (§4.4), and only once `SystemIndexProbe` (a 60 s poll of
  `pg_index.indisvalid`) has seen it valid.
- Both are built after bootstrap by `PathIndexMaintenance`: one instance under a *session* advisory lock,
  `CREATE INDEX CONCURRENTLY`, `statement_timeout = 0` and `lock_timeout = 0` for that session only, progress
  logged every 30 s; INVALID managed indexes are dropped and rebuilt in the same run, a build that fails mid-run
  is retried at the next start. Transaction-mode PgBouncer cannot carry this, so
  `path-indexes.direct-uri` is needed with `pooler-mode = transaction` — not enforced: without it the maintenance
  logs an ERROR (paths configured) or a WARN (`sf_multi` only) and skips, while the service starts and serves. Index
  maintenance is best-effort; its failures never stop the service.

### 4.4 Query translation

- RQL → SQL with an **EXISTS chain** per predicate over `search_flat` (measured 1.0–4.7× faster at p95 than a
  semi-join translation), authorization as per-candidate checks against the document row.
- **Materialised selective-leg CTE** for `and(eq, …)` and `and(in, …)` shapes: the most selective point leg
  (`eq(non-null)` or `in(non-empty)`) is materialised first and the rest of the filter is applied per candidate —
  43× faster cold start, 6.3× fewer buffer reads than the raw plan. **Point leg + two-sided range**: both legs'
  index entries are counted inside the statement (point leg up to 50,000, range up to one more) and the smaller one
  starts; a lone range leg is never materialised.
- **Arrays:** two range conditions may be satisfied by different elements (MongoDB semantics); a range start adds
  multi-valued things via `sf_multi`.
- **`plan_cache_mode = force_custom_plan`** on the search pool: path-parameterised statements would otherwise flip
  to a generic plan after five executions (observed 55 s vs 0.4 s) and per-path indexes are only usable under custom
  plans.
- `PredicateVisitor` gained default methods `visitLikeWithWildcards` / `visitILikeWithWildcards` so the SQL backend
  gets the raw wildcard pattern instead of the Mongo-regex translation (japicmp-safe; tagged `@since 3.10.0` until
  E4 moves it to `4.0.0`).

### 4.5 Write path

`SearchUpdaterFlow` (neutral) — the PostgreSQL implementation re-flattens the whole document per update in one
transaction: an unconditional `search_things` upsert, then an anti-join diff of `search_flat` in one statement
(`ANTIJOIN_FLAT_SQL`) that deletes and re-inserts only the changed leaves and never rewrites an unchanged row; all
flat rows go only for an emptied-out thing. Sustained 179 upd/s with 0 errors in the 10-minute laptop bench. A 3 h
soak on RDS db.m7g.2xlarge (10 M things) averaged 215/s with dips below 200/s during autovacuum; its evidence is not
on this branch but on the fork branch `bench/search-write-soak-rds`, which stays unmerged. Background sync,
`ThingUpdater`, `EnforcementFlow` and all `ditto.search.updater.*` settings are shared and behave identically on
both backends.

### 4.6 Migration = re-index

Point search at an empty PostgreSQL database; background sync rebuilds the index. At the service default throttle
(100 things / 10 s) that is ≈28 h per 1 M things, at the Helm default (100 / 30 s) ≈83 h — identical on MongoDB,
where nobody starts from empty. The decided cutover procedure (§9, D9) uses the `force-update` devops piggyback,
namespace by namespace: it bypasses the inconsistency-filter throttle but stays paced by the snapshot-stream batch
and the updater's write rate, and needs background sync enabled.

### 4.7 Differences from MongoDB (search)

A `like` without a run of three literal letters/digits (or prefix-only) stays on the `wpath`-bounded `sf_text`
B-tree instead of the trigram index — a per-path scan, not a table scan; `ilike` folds case character by character
under `C.utf8` (`ß`≠`ss`, dotless `ı` matches neither `i` nor `I`); sorting by any field other than
`thingId`/`namespace` costs in proportion to the matching visible things; MongoDB-only knobs (index hints, custom
Mongo indexes) are ignored with a WARN; purged namespaces stay findable until the reaper removes them (MongoDB:
until TTL); no runtime self-heal in search.

---

## 5. Changes to shared code — what reviewers must look at

These change behaviour for MongoDB deployments too, or are the places where the seams cut through existing code.

| Area                                                                                                   | Change                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | Why / MongoDB impact                                                                                                                                                                                                                                                                                                                                                                                                                               |
|--------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `internal/utils/persistence` (Mongo module)                                                            | Neutral types moved out to `persistence-api`; `MongoPersistenceBackendProvider`, `MongoSnapshotCodec`, `MongoPersistencePluginConfig`, ops factory and a `reference.conf` (Mongo provider default + `plugin-ids`) added                                                                                                                                                                                                                                                    | Package move (internal modules, not japicmp-guarded; out-of-tree users recompile) of the config interfaces `ActivityCheckConfig`, `SnapshotConfig`, `EventConfig`, the streaming actors, `PidWithSeqNr`, `SnapshotFilter`, `SnapshotAdapter` and the ops interfaces                                                                                                                                                                                |
| `internal/utils/persistent-actors`                                                                     | `AbstractPersistenceActor` snapshot envelope via codec; `AbstractPersistenceSupervisor` and `Cleanup` re-typed to `DittoReadJournal` (error mapping unchanged); `PersistencePingActor`: an iteration now ends on stream failure too (before, a failed stream left `pingInProgress` set forever), an ID-order walk resumes after the last pid reached, malformed pids are skipped                                                                                           | Behavioural on MongoDB: the ping changes only (failure cases; a MongoDB behaviour change without a feature toggle); the supervisor answers errors as before                                                                                                                                                                                                                                                                                        |
| `things/service` enforcement (+ `internal/utils/config`, `policies/enforcement`)                       | `CreatedPolicyRollback`; `AskWithRetryConfig` block `created-policy-rollback` in `internal/utils/config/…/ditto-enforcement.conf` (ask 3 s, 3 retries, back-off 500 ms–4 s, ≈22.5 s worst case; env `ENFORCEMENT_CREATED_POLICY_ROLLBACK_*`, not wired in Helm), accessor in `policies/enforcement`; `ThingSupervisorActor` forgets the created policy once the twin signal is answered                                                                                    | A failed thing create under overload retries the rollback of its implicit policy with bounded retries instead of the former unbounded loop; the caller keeps the original error; a later failing `CreateThing` can no longer delete an unrelated policy                                                                                                                                                                                            |
| `things/policies/connectivity` `*RootActor`, `*OperationsActor`, `*StreamingActorCreator`, supervisors | Resolve plugin IDs, read journal, ops and streaming through the provider                                                                                                                                                                                                                                                                                                                                                                                                   | Pure wiring; `PersistenceBoundaryArchTest` in each service bans `com.mongodb..`, `org.bson..`, the Mongo persistence plugin, `MongoReadJournal` and every Postgres type in main code (whitelist: the `…mongo.config` classes and `AbstractMongoEventAdapter`)                                                                                                                                                                                      |
| `connectivity/service`                                                                                 | `EncryptionMigrationUnsupportedActor` (501 on PostgreSQL), `ConnectivityPostgresEventAdapter`; `ConnectionMongoSnapshotAdapter` only re-typed to `SnapshotSerializer`                                                                                                                                                                                                                                                                                                      | Encryption migration to be implemented on PostgreSQL instead (§9 D4b); the snapshot adapter's old-key-only fallback is NOT on the branch (open, D4, on both backends)                                                                                                                                                                                                                                                                              |
| `thingsearch/service`                                                                                  | `Mongo*` implementations now implement the neutral `thingsearch/persistence-api` contracts; `ThingsSearchPersistence`, `ThingsSearchUpdaterPersistence`, `ThingsAggregationPersistence`, `ResultList`, `TimestampedThingId`, `Metadata`, `EvaluatedPolicy`, `IndexLengthRestrictionEnforcer` moved there; neutral `AbstractWriteModel`/`ThingWriteModel`/`ThingDeleteModel` ADDED next to the Mongo-specific classes of the same name, which stay in `thingsearch/service` | **Breaking for extension authors**: new extension point `ditto.extensions.search-persistence-provider`; every out-of-tree implementation of the three moved persistence interfaces breaks on the package move; `SearchUpdateMapper` re-typed to the neutral `AbstractWriteModel` and lost `maxWireVersion`, its `(ActorSystem, Integer)` constructor and `toIncrementalMongo`; custom `SearchUpdateObserver`s break on the `Metadata` package move |
| `rql/query`                                                                                            | `Like*PredicateImpl` now pass the raw wildcard pattern to new `PredicateVisitor` defaults `visitLikeWithWildcards` / `visitILikeWithWildcards`; the defaults keep the regex-translated behaviour for existing visitors                                                                                                                                                                                                                                                     | Additive; Java 8 module, compiles under `--release 8`                                                                                                                                                                                                                                                                                                                                                                                              |
| `things/model` `WotValidationConfig`                                                                   | Public `JsonFields` constants (`CREATED`, `REVISION`, `MODIFIED`, `DELETED`, `METADATA`) for the neutral snapshot serializer; tagged `@since 3.7.0` today (E4 moves it to `4.0.0`)                                                                                                                                                                                                                                                                                         | Additive; Java 8 module                                                                                                                                                                                                                                                                                                                                                                                                                            |
| Service `*.conf`                                                                                       | `snapshot-adapter` keeps its key and MongoDB default and now selects only the backend-neutral serializer; `persistence-backend-provider.extension-config.read-journal.entity` per service; `search.conf` gains `search-persistence-provider` (Mongo default); the Mongo persistence provider default and its `plugin-ids` live in the `reference.conf` of `internal/utils/persistence`                                                                                     | No MongoDB setting changes                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `bom/pom.xml`, the four service poms, root `pom.xml`, `build-images.sh`, `dockerfile-snapshot`         | R2DBC/reactor versions and `enforce-no-pekko-r2dbc-plugin` (bom); `enforce-no-r2dbc-in-service` in `things`/`policies`/`connectivity`/`thingsearch` `service/pom.xml`; root pom only adds the `ide-postgres-launcher` module; `BAKE_POSTGRES_EXTENSIONS=true` bakes the JARs into snapshot images — only `dockerfile-snapshot` has the `COPY`, `dockerfile-release` and `dockerfile-snapshot-arm64` ignore the staged JARs (§9 A17)                                        | —                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `deployment/helm`                                                                                      | `postgres.tls.enabled` CA/cert mounts and `extraInitContainers` hooks in the things/policies/connectivity deployments; six chart files changed, `Chart.yaml` still 4.9.0 and `CHANGELOG.md` `[Unreleased]` empty                                                                                                                                                                                                                                                           | Search deployment misses the init containers and TLS mount although `values.yaml` promises `thingsSearch.extraInitContainers` is "injected" (§9, E3); CI Helm lint fails until the chart version is bumped (A18); no `POSTGRES_*` or `ENFORCEMENT_CREATED_POLICY_ROLLBACK_*` variable is wired — those settings reach a Helm deployment only via raw `extraEnv`                                                                                    |
| `.github/workflows/maven.yml`                                                                          | Job `postgres-its`: Testcontainers ITs on PostgreSQL 16, 17, 18; fails if any IT was skipped                                                                                                                                                                                                                                                                                                                                                                               | —                                                                                                                                                                                                                                                                                                                                                                                                                                                  |

Out-of-scope changes riding on the branch (decision D8: kept, listed in the PR description, not attributed to
PostgreSQL in release notes): two upstream doc corrections — the connection-encryption migration matrix ("enabled +
only current key" is an initial encryption, not an error; three workflows, not two) and the cleanup retention
defaults (`3d` things, `30d` policies/connectivity, Helm `30d`, not `0d`) — a search env binding
(`THINGS_SEARCH_UPDATER_STREAM_THING_CACHE_RETRY_DELAY` for `ditto.search.updater.stream.thing-cache.retry-delay`)
and the non-Postgres `.run/ThingsService.run.xml` (Temurin 25 JRE + envfile block).
The gateway websocket/SSE fix `fa4fcd32f1` is no longer on the branch: it was split off to
`bugfix/websocket-sse-publisher-queue` on 2026-10-30 (`git diff eclipse/master...HEAD -- gateway/` is empty).

---

## 6. Configuration at a glance

| Setting                                                                     | Default / values                                                                                                                                                                | Env                                                   |
|-----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------|
| `ditto.postgresql.uri` / `username` / `password`                            | —                                                                                                                                                                               | `POSTGRES_URI`, `POSTGRES_USER`, `POSTGRES_PASSWORD`  |
| `ditto.postgresql.pool.ddl-credentials.{username,password}`                 | unset → runtime role runs DDL                                                                                                                                                   | `POSTGRES_DDL_USER`, `POSTGRES_DDL_PASSWORD`          |
| `ditto.postgresql.ssl.mode` (+ root cert, cert, key)                        | `verify-full`; `disable`, `allow`, `prefer`, `require`, `verify-ca`; `verify-*` without `root-cert` fails at load unless `ssl.allow-system-truststore = true` (default `false`) | `POSTGRES_SSL_{MODE,ROOT_CERT,CERT,KEY,KEY_PASSWORD}` |
| `ditto.postgresql.pool.max-size`                                            | 25 (pending queue 2 ×)                                                                                                                                                          | `POSTGRES_POOL_MAX_SIZE`                              |
| `ditto.postgresql.statement-timeout`                                        | 60 s (startup parameter)                                                                                                                                                        | `POSTGRES_STATEMENT_TIMEOUT`                          |
| `ditto.postgresql.bootstrap.attempt-timeout`                                | 60 s per attempt, not retried                                                                                                                                                   | `POSTGRES_BOOTSTRAP_ATTEMPT_TIMEOUT`                  |
| `ditto.postgresql.bootstrap.retry.max-duration`                             | 60 s                                                                                                                                                                            | `POSTGRES_BOOTSTRAP_RETRY_MAX_DURATION`               |
| `ditto.postgresql.bootstrap.exit-on-failure`                                | `true`: coordinated shutdown, exit code 1                                                                                                                                       | `POSTGRES_BOOTSTRAP_EXIT_ON_FAILURE`                  |
| `ditto.postgresql.schema.self-heal`                                         | `true` (persistence services)                                                                                                                                                   | `POSTGRES_SCHEMA_SELF_HEAL`                           |
| `ditto.postgresql.pooler-mode`                                              | `direct`; `transaction` gates index maintenance                                                                                                                                 | `POSTGRES_POOLER_MODE`                                |
| `…persistence-backend-provider.extension-config.read-journal.entity`        | required, no default (boot fails with `DittoConfigError`); `things`, `policies`, `connections` per service — `wot` is a table prefix only, never a read-journal entity          | `POSTGRES_READ_JOURNAL_ENTITY`                        |
| `ditto.postgresql.search.reaper.{interval,batch-size,max-batches-per-tick}` | 30 s, 1000, 50                                                                                                                                                                  | `POSTGRES_SEARCH_REAPER_*`                            |
| `ditto.postgresql.force-custom-plan`                                        | `true` for search                                                                                                                                                               | `POSTGRES_SEARCH_FORCE_CUSTOM_PLAN`                   |
| `ditto.postgresql.search.path-indexes.{paths,direct-uri}`                   | unset / `""`                                                                                                                                                                    | `POSTGRES_SEARCH_PATH_INDEXES`, `…_DIRECT_URI`        |

Circuit breakers: the MongoDB env vars `BREAKER_*` / `SNAPSHOT_BREAKER_*` apply unchanged. Persistence ping:
`persistence-ping.read-journal-batch-size` (500; the keyset window in ID order — priority order uses a fixed 500)
and the existing `rate`/`interval` keys, with their per-service env names (`POLICIES_PERSISTENCE_PING_*`,
`RECONNECT_*`; things has no `persistence-ping` block and uses the code defaults).

---

## 7. Testing and CI

- **Unit + Testcontainers ITs** in the three PostgreSQL modules (`*IT` need Docker and skip without it, except the
  classloading IT — CI checks that none skipped): 1,169 tests by surefire/failsafe count at the 2026-10-30 merge
  `4f17f09911` (postgres-client 214 + 40 IT, persistence-r2dbc 186 + 114 IT, search-r2dbc 388 + 147 IT,
  persistence-api 57, thingsearch persistence-api 19, postgres-extension-it 4). Image via
  `-Dditto.test.postgres.image` (CI matrix 16/17/18).
- **HOCON lint tests** (`PostgresProfileHoconLintTest`, `PostgresSearchProfileHoconLintTest`) assert the shipped
  profiles: provider class, plugin blocks per entity, auto-start lists, no Mongo leftovers; plus
  `PersistenceBackendSelfCheckTest`, `*PersistencePluginConfigTest`.
- **Classloading IT** (`PostgresExtensionClassloadingIT`, needs no Docker) copies the real shaded JARs and loads them
  in child `URLClassLoader`s whose parent is the test classpath (standing in for the service allinone, SPI only); it
  also proves that a thin JAR without the base fails with an error naming the missing base JAR. It runs in **no CI
  job**: `postgres-its` lists three modules and the main job stops at `mvn package`.
- **Service-level tests** (things/policies/connectivity/search) cover the wiring, the created-policy rollback,
  the encryption-migration refusal, moved search types.
- **System tests** (eclipse/ditto-testing) run green on both databases through the unified CI entry points
  (2026-10-27: 1,930 + 3 × 25 tests each on PG and Mongo); the `database` input lives on a ditto-testing workflow
  branch, not in this repository.
- **Benchmarks** (`internal/utils/persistence-bench`, `internal/utils/search-r2dbc/benchmark`) with committed
  evidence and EXPLAINs; still in the branch diff (227 files, `persistence-bench` is a reactor module) and to be
  moved to a fork branch before the PR (decision D6, pending).

---

## 8. Performance summary (laptop Docker, both engines limited to 4 CPUs / 8 GiB unless stated)

Persistence, 250 k things / 35 M events, PostgreSQL 16 vs MongoDB 7.0, driver level:

| Scenario                                         | PostgreSQL                                           | MongoDB              | Note                                                                                          |
|--------------------------------------------------|------------------------------------------------------|----------------------|-----------------------------------------------------------------------------------------------|
| Recovery: latest snapshot p95                    | 1.01 ms                                              | 0.80 ms              | Mongo 1.3× faster                                                                             |
| Recovery: replay 40–50 k events p95              | 258.6 ms                                             | 272.5 ms             | parity                                                                                        |
| Single append p95                                | 2.90 ms                                              | 2.30 ms              | Mongo 1.3× faster                                                                             |
| Fresh snapshot write p95                         | 2.33 ms                                              | 3.00 ms              | PG faster; overwrite at parity                                                                |
| Cleanup page p95 (payload-free, minAge 0)        | 1.22 cold / 0.51 warm                                | 1.66 ms (not re-run) | the C1 flag, closed                                                                           |
| Single-entity cleanup, longest histories         | 1,820 ms                                             | 633 ms               | FLAG: PG 2.9× slower; Ditto paces cleanup                                                     |
| Unthrottled sweep of 22 M rows                   | 838 s                                                | 298 s                | identical rows deleted on both                                                                |
| Autovacuum settle after a mass prune (own loads) | 70 s at cost limit 2000 vs 3,172 s at server default | —                    | schema v3 sets the limit; 2000 raises snapshot-write p95 2–10× while it runs, kept on purpose |

Search (no container limits), 1 M things / 68.5 M flat rows, PostgreSQL 16 stock settings (128 MB `shared_buffers`,
JIT on), final re-run 2026-10-22: 8 of 20 query variants meet the 200 ms p95 gate; equality + range and count shapes
1.4–1.6 s → 267 / 205 ms p95 (below 200 ms in warm rounds); low-cardinality `ilike` 53 ms; infix `ilike` on a path
most things carry 357 ms, and 298 → 120 ms in the per-path-index A/B (51 MB index vs 304 MB table-wide in the
Phase-0 bench, built online in 43 s); sorting by `thingId` or `namespace` 0.3 ms p95 page 1, other sort fields
375–720 ms per page. The numbers are trends on one laptop, not plateau claims, and driver-level (no Pekko overhead).
Claims to avoid: "faster than MongoDB" in general, "production-proven at scale" (the 10 M / ≥1 h plateau gate was
not met in the RDS soak, whose evidence lives only on the fork branch `bench/search-write-soak-rds`),
"zero-downtime migration".

---

## 9. Open work before the upstream PR, known limitations

Decisions were taken on 2026-10-29 after a full documentation review; its material is local and untracked, so
everything the rows rely on is stated here. Nothing below is implemented yet.

| Id                      | Decision / finding                                                                                                                                                                                                                                                                                                                                                                                                                                          | Kind |
|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------|
| D3/D3b                  | Connectivity remember-entities on PostgreSQL via `eventsourced` like MongoDB: new `connection_remember_*` table pair (schema v4), two plugins storing a Pekko-serialization envelope in JSONB, drop the `ddata` override. Why: `ddata` keeps durable keys in LMDB files under the working directory `/opt/ditto`, which `dockerfile-release` leaves root-owned (Helm runs as uid 1000); mechanism verified, the crash not yet reproduced on a release image | code |
| D4                      | Snapshot adapter falls back to `old-symmetrical-key` when no current key is set (disable-encryption procedure = MongoDB's); shared class, fixes MongoDB too; drop the "keep `old-symmetrical-key`" advice from the 501 refusal and the startup WARN when no old key is set                                                                                                                                                                                  | code |
| D4b                     | Implement `migrateEncryption*` on PostgreSQL, replacing `EncryptionMigrationUnsupportedActor` (501)                                                                                                                                                                                                                                                                                                                                                         | code |
| D6                      | Move `persistence-bench`, the search bench code/results/READMEs and all numbers out of the user docs to a fork branch                                                                                                                                                                                                                                                                                                                                       | repo |
| B1–B3                   | Provisioning script: `GRANT ditto_ddl TO CURRENT_USER` (PG 16+ managed; B1 is read from the PG 16 docs and not yet confirmed on RDS — run the script as the RDS master user first), `GRANT CREATE ON DATABASE` for `pg_trgm`, `CREATE EXTENSION … SCHEMA ditto` in error text                                                                                                                                                                               | code |
| E3                      | Helm: render `thingsSearch.extraInitContainers` and the TLS mount in the search deployment                                                                                                                                                                                                                                                                                                                                                                  | code |
| E4                      | `@since` → `4.0.0` in the 67 files the branch added: 36 carry `3.7.0` (14 `persistence-api` types, `things/model` `WotValidationConfig.JsonFields`, the four `PersistenceBoundaryArchTest`s, …) and 31 carry `3.10.0` (rql, `thingsearch/persistence-api`, `ReadJournalRestarts`, …); release-notes stub comment                                                                                                                                            | code |
| A9, C-m9, A16, A17, A18 | Dead Mongo `overrides` placeholders in the overlays; `pooler-mode` comment; personal `MVN` path in a script; bake no-op in `dockerfile-release` and `dockerfile-snapshot-arm64`; chart `version` bump (still 4.9.0) + `CHANGELOG.md` entry                                                                                                                                                                                                                  | code |
| 2026-11-02 review       | Delete the dead `PostgresSnapshotAdapter` (`internal/utils/persistence`, §2.2); add `in()`/range-`null` parity pins (follow-up 1 below); run `postgres-extension-it` in CI (§7)                                                                                                                                                                                                                                                                             | code |
| D1/D2/D5                | Docs: split into `installation-postgresql.md` + `operating-postgresql.md`; Compose via `/opt/ditto/<service>-extension.conf` (no `things-dev` include), Helm via `filebased` overlay; document `NoClassDefFoundError … PostgresConfig` for a missing base JAR                                                                                                                                                                                               | docs |
| D7                      | Release notes folded into the 4.0.0 release-notes page (`release_notes_400.md`, to be created) with a breaking-changes section; drop `release_notes_postgres_search.md`                                                                                                                                                                                                                                                                                     | docs |
| D9                      | Cutover docs: initial fill via `force-update` namespace by namespace, then default background sync                                                                                                                                                                                                                                                                                                                                                          | docs |
| D8                      | Unrelated changes stay in the PR, listed in its description (the list: end of §5); release notes must not attribute them to PostgreSQL                                                                                                                                                                                                                                                                                                                      | PR   |

Known limitations that stay (documented): snapshot stream reads whole snapshots; search has no runtime self-heal;
`ilike` folding and sort-cost differences (§4.7); U+0000 rejected by the journal; no persistence data migration
tooling in this repository. Search follow-ups from the 2026-07-16 system-test triage, not yet resolved: (1) `in()`
and the range predicates `ge`/`gt`/`le`/`lt` still bind a `null` operand as the text literal `'null'`
(`CreateSqlPredicateVisitor`; only `eq(null)`/`ne(null)` received the `type_rank` special case), whereas MongoDB's
`$in: [null]` matches null and missing values, `$gte: null` behaves like `eq(null)` and `$gt`/`$lt: null` match
nothing — not recorded anywhere in the tree, the parity corpus has no `in`/range-null pins; (2) two recorded
`empty()` divergences: `empty-wildcard-property` (`parity/queries.json`: MongoDB matches a thing whose feature lacks
the property, PostgreSQL does not) and `empty-overmatch-array-of-array` (`parity/divergence-allowlist.json`:
PostgreSQL matches where MongoDB does not).

---

## 10. Where to look

| Topic                                     | Location                                                                                                                                                                                                                                                  |
|-------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| User-facing setup and operations          | `documentation/.../installation-extending.md` §"PostgreSQL …" (to be split, D1), `installation-operating.md`, `operating-configuration.md`                                                                                                                |
| Operator runbook, provisioning, PgBouncer | `deployment/postgres/README.md`, `deployment/postgres/provisioning/01-roles-and-grants.sql`                                                                                                                                                               |
| Local development                         | `deployment/postgres-local/README.md`, `.run/* (Postgres).run.xml`, the `ide-postgres-launcher` Maven module (pom-only classpath aggregator for the run configs)                                                                                          |
| Persistence schema                        | `…/persistence/postgres/schema/PostgresSchema.java` (Javadoc explains every table decision)                                                                                                                                                               |
| Search schema and translator              | `…/postgres/search/PostgresSearchSchema.java`, `…/search/query/`, `…/search/pathindex/`                                                                                                                                                                   |
| Benchmark method and evidence             | `internal/utils/persistence-bench/benchmark/README.md`, `internal/utils/search-r2dbc/benchmark/README.md` (until D6 moves them to the fork branch, which retires this row; the search README predates the RDS soak and still calls it "not yet executed") |
| Release-note material, breaking changes   | `documentation/.../release_notes_postgres_search.md` (to be folded, D7)                                                                                                                                                                                   |
