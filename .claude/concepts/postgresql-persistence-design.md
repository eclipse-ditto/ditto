# PostgreSQL Persistence and Search Backend Design Document

**Related Issue**: [GitHub #2629 - Support PostgreSQL as persistence and search backend via pluggable persistence providers](https://github.com/eclipse-ditto/ditto/issues/2629)

**Status**: Implemented on branch `feat/postgres-persistance-search`; what the code still owes this design: §9
**Last Updated**: 2026-11-04

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
- **Own Pekko plugin on the plain R2DBC driver.** Reactive end to end like the MongoDB path; the upstream
  `pekko-persistence-r2dbc` plugin was rejected (no tag queries, `bytea` payloads, a delete marker inside the
  journal, single-row snapshots) and the bom bans it (§2.4).
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
monitor. The backend is part of Eclipse Ditto itself, not a separately maintained plugin, so that it evolves with
the persistence contracts it implements.

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

- Data migration from MongoDB to PostgreSQL for things/policies/connections: a separate migrator tool exists and is
  maintained outside eclipse-ditto; it is no part of this design, and the user documentation calls the data
  migration the operator's responsibility. Search needs no migration: it is re-indexed (§4.6).
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
The check runs with the MongoDB provider as well, where a mismatch only warns (§9): a MongoDB deployment that
wires plugin classes of its own keeps booting as before.

### 2.2 Modules

| Module                                               | Purpose                                                                                                                                                                           | Ships in |
|------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|
| `internal/utils/persistence-api`                     | Backend-neutral persistence contracts, configs, streaming actors                                                                                                                  | image    |
| `thingsearch/persistence-api`                        | Backend-neutral search contracts, write model, index-document mapping                                                                                                             | image    |
| `internal/utils/persistence` (changed)               | MongoDB provider + `MongoSnapshotCodec`; Postgres *event* adapter base `AbstractPostgresEventAdapter` (JSON only)                                                                 | image    |
| `internal/utils/postgres-client`                     | R2DBC pool, SSL, config, schema manager/healer/bootstrap, monitoring                                                                                                              | base JAR |
| `internal/utils/persistence-r2dbc`                   | Journal, snapshot store, read journal, ops, persistence schema                                                                                                                    | thin JAR |
| `internal/utils/search-r2dbc`                        | Search schema, flattener, RQL→SQL, read/write, reaper, path indexes                                                                                                               | thin JAR |
| `internal/utils/postgres-*-extension` (3)            | Shade modules producing the base and the two thin JARs                                                                                                                            | —        |
| `internal/utils/postgres-extension-it`               | IT: the three shaded JARs load in isolated class loaders over an allinone-like parent; a thin JAR without the base fails with an actionable error; runs in no CI job yet (§7, §9) | —        |
| `internal/utils/persistence-bench`                   | PG-vs-Mongo persistence benchmark harness and evidence; development tooling that leaves the tree before the upstream change (§9)                                                  | —        |
| `ide-postgres-launcher`, `deployment/postgres-local` | Local multi-service runs on PostgreSQL: IDE classpath module, IntelliJ run configs in `.run/`, overlay confs + CLI launcher, Compose for the database                             | —        |
| `deployment/postgres`                                | Operator runbook, two-role provisioning SQL, PgBouncer notes                                                                                                                      | —        |

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
  `pekko.cluster.sharding.remember-entities-store` to `ddata` JVM-wide (§3.3; §9 replaces this with `eventsourced`
  plugins). The search include needs none of this.
- **Auto-start narrowing is mandatory.** The profile lists all four entities in Pekko's `auto-start-*`; each
  service must narrow the lists to the plugins whose dispatchers it defines (the things JVM keeps the `things`
  pair; its WoT plugins start lazily on the same dispatchers), because Pekko resolves every auto-started plugin's
  dispatcher eagerly and the other entities' dispatchers do not exist in that service. Nothing checks the narrowing
  at boot: a service that forgets it fails with a Pekko `ConfigurationException` when its first persistent actor
  starts, after the service has reported itself up.
- **Snapshot encoding follows the backend.** The services' snapshot serializers (selected by
  `ditto.extensions.snapshot-adapter`; three of the four keep their Mongo-era names `ThingMongoSnapshotAdapter`,
  `PolicyMongoSnapshotAdapter`, `ConnectionMongoSnapshotAdapter`) are backend-neutral JSON serializers; the
  provider's `SnapshotCodec` decides between the BSON and the JSONB envelope, so no per-service override is needed.
- **Dev profiles** `<service>-pg-dev.conf` (four, shipped inert in the allinone JARs) and six `.run/* (Postgres)`
  run configurations (one per service plus the compound; the gateway one carries no PostgreSQL config) exist for
  local runs only.

### 2.4 Alternatives considered

The designs that were not chosen, and why, so that the choices of §2–§4 can be checked against their reasons.

- **JDBC with a thread pool instead of R2DBC.** The MongoDB path is reactive end to end: the reactive-streams driver,
  `Source.fromPublisher` at every call site, no blocking pool. R2DBC gives the PostgreSQL path the same shape — both
  drivers expose a reactive-streams `Publisher`, so the same bridge serves both — and keeps the journal, snapshot store
  and read journal free of threads that block on the database. A sized blocking pool (HikariCP) would work and is the
  simpler fallback should reactive parity ever be given up; it was not chosen because PostgreSQL would then be the one
  backend that holds a service thread for the duration of every query. The price is the third-party content of the base
  JAR (§2.3: reactor, netty, r2dbc) and that schema tooling built on JDBC is out (below).
- **`pekko-persistence-r2dbc` as the Pekko plugin.** Evaluated against version 1.1.0 and rejected; `bom/pom.xml` bans
  it (§2.3) so it cannot return as a transitive dependency. Four facts, each sufficient on its own:
  - Its read journal implements slice, persistence-id and paged queries only — no `EventsByTagQuery`, and nothing like
    the persistence ping's query for distinct pids ordered by the priority tag of each pid's newest event (§3.4).
  - Its journal and snapshot payloads are `bytea` through Pekko serialization, with no JSON option, so Ditto's JSON
    adapters would need their own serializer and DAO anyway, and SQL could not read the payloads (§3.3).
  - Its `deleteMessagesTo` deletes the rows and inserts a marker row (`deleted = true`, empty payload) at the deleted
    sequence number into the journal table, and the highest sequence number is read from the journal rows. Ditto's
    background cleanup deletes journal rows by range outside the plugin (`Cleanup` through the read journal), so that
    number would depend on rows Ditto removes; the `<e>_journal_seq` row (§3.1) keeps it independent of the journal.
  - Its snapshot store keeps one row per persistence id (`ON CONFLICT (persistence_id) DO UPDATE`): no snapshot
    history, no recovery at an older sequence number, no retention of several snapshots (§3.1 keys on
    `(pid, sn, written_at)`).
- **`pekko-persistence-jdbc`.** The same query gaps, and Slick runs blocking JDBC on a thread pool (first item).
- **Flyway or Liquibase for the schema.** Both need a JDBC driver next to R2DBC in the base JAR and keep a migration
  history of their own. The service owns its DDL instead (§3.2): idempotent statements, a version + checksum row as the
  history, an advisory lock as the coordinator. The price is that transforming changes cannot be expressed (§3.2).
- **`bytea` payloads (BSON or Pekko-serialized bytes) instead of `jsonb`.** Cleanup, ping, streaming and operators read
  events and snapshots in SQL; bytes would move that into the application and take `psql` away from the operator.
  Accepted consequence: JSON normalisation and the U+0000 rejection (§3.3). The one payload that is bytes by nature,
  sharding's remember-entities state, gets a JSON envelope on plugins of its own (§9).
- **One journal table for all entity types** (the upstream plugins' layout) instead of a table set per entity. MongoDB
  keeps one collection set per entity type, and every per-entity walk — ping, cleanup, namespace purge, snapshot
  streaming — scans a whole table in pid order; a shared table would make each walk filter by pid prefix and put
  Pekko's own `/sharding/…` persistence ids next to Ditto's (§9). The twelve tables of §3.1 form one DDL component
  with one version row, so the roll rule of §3.2 has a single row to check.
- **Search.** The alternatives there were measured rather than argued and are recorded with the decision: a GIN index
  on the `jsonb` document (§4.1), a semi-join translation of RQL (§4.4), generic plans (§4.4), table-wide versus
  per-path trigram indexes (§4.3, §8), MongoDB's TTL index versus the reaper (§4.2).

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
  service pinned to an older image. A row written before the compatibility column existed has it `NULL`, which
  counts as equal to `version`. A same-version checksum mismatch (manual DDL edits) refuses to boot; recovery is to
  reconcile the DDL and set the stored checksum to the value the boot error names.
- **Rules for a DDL change:** any change to a component's DDL changes its checksum and therefore bumps `VERSION`;
  each bump declares `MIN_COMPATIBLE_VERSION`. A change older code can run against (a new or dropped index, storage
  parameters) keeps the previous value; a change older code cannot run against (column rename or type change, a
  `NOT NULL` column without default, dropping a column or table older code uses) sets it to the new version. Both
  versions run side by side during a roll, so a compatible change must also let the new code cope with rows the old
  code writes. A column added to an existing table needs its own `ALTER TABLE … ADD COLUMN IF NOT EXISTS`. An index
  whose plain build would block writes on a populated table is not part of the DDL and is built `CONCURRENTLY` after
  the bootstrap (§4.3); introducing one still bumps `VERSION`, so that older code mid-roll leaves it to the newer
  code (search v3 kept v2's DDL and checksum for exactly this reason).
- **Fast path:** version and checksum match and every table and index exists → no DDL, no table lock. The
  transaction-scoped advisory lock is taken on every boot before the row is read; otherwise DDL runs under it with
  `SET LOCAL lock_timeout = '500ms'`, so each DDL statement waits at most half a second for a table or index lock, a
  conflict fails the start and the next start retries. A wait for the advisory lock itself (another pod
  bootstrapping or healing) is bounded only by `bootstrap.attempt-timeout` (60 s, not retried). The fast path
  re-applies no storage parameters: a manually changed reloption stays changed until the next version bump or a
  self-heal that runs the DDL.
- **Waiting for PostgreSQL:** classified retries (refused, DNS, starting up/shutting down, too many clients) with
  exponential 1 → 10 s back-off (×2 per attempt, ±20 % jitter) for `bootstrap.retry.max-duration` (60 s);
  everything else fails at once. A final failure runs Pekko's coordinated shutdown with reason `BootstrapFailed` —
  exit code 1 through a reason override, as Ditto services run with `exit-jvm = on` — so the orchestrator restarts
  the pod; `bootstrap.exit-on-failure = false` keeps the JVM up without its root actor instead.
- **Self-heal at runtime** (persistence services only, not search): SQLSTATE `42P01` triggers a schema check, DDL
  where needed, one retry; counted by `ditto_persistence_schema_self_heal`; `POSTGRES_SCHEMA_SELF_HEAL=false` fails
  loud instead. The heal follows the boot's rules (no DDL against a newer stored version, the same 500 ms lock
  timeout) but skips the catalog verification and the version upsert. Documented as masking data loss, exactly like
  MongoDB's implicit collection creation.
- **Two roles:** DDL owner (`ditto.postgresql.pool.ddl-credentials`, `POSTGRES_DDL_USER`) vs DML-only runtime role;
  when no DDL credentials are set the runtime role runs the DDL. The bootstrap and heals open one short-lived
  connection outside the pool as the DDL role; a search index build holds its own non-pooled DDL connection for the
  whole build.

### 3.3 Events and snapshots as JSONB

- Service-specific adapters `ThingPostgresEventAdapter`, `PolicyPostgresEventAdapter`,
  `ConnectivityPostgresEventAdapter`, `WotValidationConfigPostgresEventAdapter` extend
  `AbstractPostgresEventAdapter` in `internal/utils/persistence` (JSON only, no R2DBC). Connection secrets keep the
  AES-GCM field encryption: events decrypt with the current key and fall back to `old-symmetrical-key`, also when
  only the old key is set; snapshots go through the shared `ConnectionMongoSnapshotAdapter`, which today falls back
  to the old key only next to a current one and otherwise leaves the fields encrypted with a WARN — the design
  makes it fall back like the event adapter (§9, on both backends).
- `jsonb` normalises: key order is not preserved after reload, `1E10` is re-rendered, U+0000 cannot be stored (the
  journal rejects such an event with a class-22 WARN; the API already rejects it with 400 for thing/policy payloads).
- The journal accepts only a Ditto `JsonValue` or JSON text. That is why connectivity's remember-entities store
  could not use `eventsourced` (Pekko-serialized binary events) and the profile switched to `ddata` — superseded:
  §9 gives the remember-entities store its own plugins with a Pekko-serialization envelope in JSONB.
- **Write errors** are classified by SQLSTATE: a unique-key violation (`23505`) is treated as a possible
  commit-then-retry duplicate and reconciled against the stored row, a genuine mismatch is fatal; any other
  integrity violation (NOT NULL, FK, CHECK) is a lost write that stops the persistence actor; a class-22 data error
  (U+0000, oversized values) rejects that single write and the actor continues; a pool acquire timeout and every
  other failure are lost writes that stop the actor. A rejected write leaves the caller without a reply until its
  ask timeout.

### 3.4 Read journal and streams

- `DittoReadJournal` (neutral) replaces direct `MongoReadJournal` use in cleanup, ping, namespace ops and
  snapshot streaming. Live queries (`eventsByTag` etc.) complete instead of waiting — Ditto never relied on the
  live variants (MongoDB realtime collection is disabled).
- **Persistence ping** walks entity ids in keyset windows of 500 ids (`read-journal-batch-size` in ID order; the
  fixed `PRIORITY_WALK_WINDOW` in priority order, connectivity's default) and keeps the tagged ones; no statement,
  transaction or snapshot is held between windows (a server-side cursor walk, the first design, was cancelled by
  `statement_timeout` after about 256 ids on a large population). Priority order collects `(id, priority)` in
  memory first. Only the two ping walks retry, with the MongoDB read journal's back-off; slow failures count too, so
  retries end. Cleanup pages, snapshot streams and the pid lookups fail on their first error (MongoDB parity).
  Windows and retries live in `PostgresReadJournal`; what `PersistencePingActor` itself gained applies to both
  backends and lands as a bug fix of its own before this change (§5, §9).
- **Cleanup pages are payload-free** on PostgreSQL too: a page requesting no fields selects only pid, sn, lifecycle
  and write time of the newest snapshot (§8: formerly the one scenario where PostgreSQL was an order of magnitude
  slower). MongoDB already returned no payload here, so the change is PostgreSQL-only; `Cleanup` itself changed in
  types only.
- **Streaming is bound to one entity type** per service (`read-journal.entity`, required with no default: `things`,
  `policies` or `connections`; WoT validation config lives in the things JVM and is no read-journal consumer); only
  things streams snapshots in Ditto. Known gap: the snapshot stream returns whole snapshots although background sync
  asks for three fields.
- **Boot order** in a persistence service: provider construction → `bootstrapSchema()` (DDL under the advisory
  lock) → `getReadJournal()` (lazy, first use) → self-check (§2.1) → shard regions and health route.

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
- **Same-release check across the JARs:** both providers compare a version marker that every extension JAR carries
  (`META-INF/ditto-postgres-extension.properties`, read through `PostgresExtensionVersions`) and refuse to start on a
  mismatch with an `IllegalStateException` naming each component. A thin JAR mounted without its base JAR fails the
  same way where the check can run (the classloading IT, §7); on the real boot path the provider class links against
  base types first, so the symptom is `NoClassDefFoundError … PostgresConfig` (§9 documents it).

### 3.6 Differences from MongoDB (persistence)

JSON normalisation and U+0000 (§3.3); live journal queries complete (§3.4); snapshot stream reads whole snapshots;
streaming bound to the service's entity; ping walks windows and counts slow failures; pool fails fast (4 s) instead of
waiting 2 min; late commits after the caller's deadline remain possible on both backends (Ditto request handling, not
the backend); connection encryption *migration* commands currently answer 501 on PostgreSQL (§9 implements them).

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
  gets the raw wildcard pattern instead of the Mongo-regex translation (japicmp-safe; tagged `@since 4.0.0`).

### 4.5 Write path

`SearchUpdaterFlow` (neutral) — the PostgreSQL implementation re-flattens the whole document per update in one
transaction: an unconditional `search_things` upsert, then an anti-join diff of `search_flat` in one statement
(`ANTIJOIN_FLAT_SQL`) that deletes and re-inserts only the changed leaves and never rewrites an unchanged row; all
flat rows go only for an emptied-out thing. Sustained 179 upd/s with 0 errors in the 10-minute laptop bench. A 3 h
soak against RDS (db.m7g.2xlarge, PostgreSQL 16, 10 M things) averaged 215 upd/s with no failed update; 17 of its 360
thirty-second windows fell below 200/s while autovacuum ran on `search_flat` (IO-bound cycles of 43–54 min), so the
design claims no sustained plateau at that size. Background sync, `ThingUpdater`, `EnforcementFlow` and all
`ditto.search.updater.*` settings are shared and behave identically on both backends.

### 4.6 Migration = re-index

Point search at an empty PostgreSQL database; background sync rebuilds the index. At the service default throttle
(100 things / 10 s) that is ≈28 h per 1 M things, at the Helm default (100 / 30 s) ≈83 h — identical on MongoDB,
where nobody starts from empty. The cutover procedure (§9 documents it) uses the `force-update` devops piggyback,
namespace by namespace: it bypasses the inconsistency-filter throttle but stays paced by the snapshot-stream batch
and the updater's write rate, has no backpressure, and needs background sync enabled. The throttle cannot be raised
at runtime: the devops config modification re-parses the block (so `enabled` and the intervals take effect), but the
metadata source was built with the throttle values when the actor was created, so new throttle values apply only
after a restart.

### 4.7 Differences from MongoDB (search)

A `like` without a run of three literal letters/digits (or prefix-only) stays on the `wpath`-bounded `sf_text`
B-tree instead of the trigram index — a per-path scan, not a table scan; `ilike` folds case character by character
under `C.utf8` (`ß`≠`ss`, dotless `ı` matches neither `i` nor `I`); sorting by any field other than
`thingId`/`namespace` costs in proportion to the matching visible things; MongoDB-only knobs (index hints, custom
Mongo indexes) are ignored with a WARN; purged namespaces stay findable until the reaper removes them (MongoDB:
until TTL); no runtime self-heal in search.

---

## 5. Changes to shared code

These change behaviour for MongoDB deployments too, or are the places where the seams cut through existing code.
The goal in §1 allows no MongoDB behaviour change; the rows that have one are collected in §9 with the ways to
resolve them.

| Area                                                                                                   | Change                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | Why / MongoDB impact                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
|--------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `internal/utils/persistence` (Mongo module)                                                            | Neutral types moved out to `persistence-api`; `MongoPersistenceBackendProvider`, `MongoSnapshotCodec`, `MongoPersistencePluginConfig`, ops factory and a `reference.conf` (Mongo provider default + `plugin-ids`) added                                                                                                                                                                                                                                                    | Package move (internal modules, not japicmp-guarded; out-of-tree users recompile) of the config interfaces `ActivityCheckConfig`, `SnapshotConfig`, `EventConfig`, the streaming actors, `PidWithSeqNr`, `SnapshotFilter`, `SnapshotAdapter` and the ops interfaces                                                                                                                                                                                                                                   |
| `internal/utils/persistent-actors`                                                                     | `AbstractPersistenceActor` snapshot envelope via codec; `AbstractPersistenceSupervisor` and `Cleanup` re-typed to `DittoReadJournal` (error mapping unchanged); `PersistencePingActor`: an iteration now ends on stream failure too (before, a failed stream left `pingInProgress` set forever), an ID-order walk resumes after the last pid reached, malformed pids are skipped                                                                                           | Behavioural on MongoDB: the ping changes only (failure cases); they land as a bug fix of their own before this change (§9); the supervisor answers errors as before                                                                                                                                                                                                                                                                                                                                   |
| `things/service` enforcement (+ `internal/utils/config`, `policies/enforcement`)                       | `CreatedPolicyRollback`; `AskWithRetryConfig` block `created-policy-rollback` in `internal/utils/config/…/ditto-enforcement.conf` (ask 3 s, 3 retries, back-off 500 ms–4 s, ≈22.5 s worst case; env `ENFORCEMENT_CREATED_POLICY_ROLLBACK_*`, not wired in Helm), accessor in `policies/enforcement`; `ThingSupervisorActor` forgets the created policy once the twin signal is answered                                                                                    | A failed thing create under overload retries the rollback of its implicit policy with bounded retries instead of the former unbounded loop; the caller keeps the original error; a later failing `CreateThing` can no longer delete an unrelated policy. Behavioural on MongoDB, with a new config block; lands as a bug fix of its own, Helm wiring included, before this change (§9)                                                                                                                |
| `things/service` WoT validation config                                                                 | Snapshots are serialized by `WotValidationConfigSnapshotSerializer`, chosen through a `resolveSnapshotSerializer` hook of the persistence actor                                                                                                                                                                                                                                                                                                                            | Before, the WoT actor obtained the things snapshot adapter through `SnapshotAdapter.get`, whose `isDeleted(Thing)` throws a `ClassCastException` at the first WoT snapshot (the actor restarts; no WoT snapshot is ever saved). A bug fix on MongoDB; lands as a change of its own before this one (§9)                                                                                                                                                                                               |
| `things/policies/connectivity` `*RootActor`, `*OperationsActor`, `*StreamingActorCreator`, supervisors | Resolve plugin IDs, read journal, ops and streaming through the provider; run the boot self-check (§2.1)                                                                                                                                                                                                                                                                                                                                                                   | Pure wiring; `PersistenceBoundaryArchTest` in each service bans `com.mongodb..`, `org.bson..`, the Mongo persistence plugin, `MongoReadJournal` and every Postgres type in main code (whitelist: the `…mongo.config` classes and `AbstractMongoEventAdapter`). The self-check also runs with the MongoDB provider: stock configurations pass; a custom journal or snapshot `class` or an auto-start entry without `class` warns there by design (§9) and fails the boot with `DittoConfigError` today |
| `connectivity/service`                                                                                 | `EncryptionMigrationUnsupportedActor` (501 on PostgreSQL), `ConnectivityPostgresEventAdapter`; `ConnectionMongoSnapshotAdapter` only re-typed to `SnapshotSerializer`                                                                                                                                                                                                                                                                                                      | Encryption migration on PostgreSQL and the snapshot adapter's old-key-only fallback are §9 items; the fallback is shared code and applies to both backends                                                                                                                                                                                                                                                                                                                                            |
| `thingsearch/service`                                                                                  | `Mongo*` implementations now implement the neutral `thingsearch/persistence-api` contracts; `ThingsSearchPersistence`, `ThingsSearchUpdaterPersistence`, `ThingsAggregationPersistence`, `ResultList`, `TimestampedThingId`, `Metadata`, `EvaluatedPolicy`, `IndexLengthRestrictionEnforcer` moved there; neutral `AbstractWriteModel`/`ThingWriteModel`/`ThingDeleteModel` ADDED next to the Mongo-specific classes of the same name, which stay in `thingsearch/service` | **Breaking for extension authors**: new extension point `ditto.extensions.search-persistence-provider`; every out-of-tree implementation of the three moved persistence interfaces breaks on the package move; `SearchUpdateMapper` re-typed to the neutral `AbstractWriteModel` and lost `maxWireVersion`, its `(ActorSystem, Integer)` constructor and `toIncrementalMongo`; custom `SearchUpdateObserver`s break on the `Metadata` package move                                                    |
| `rql/query`                                                                                            | `Like*PredicateImpl` now pass the raw wildcard pattern to new `PredicateVisitor` defaults `visitLikeWithWildcards` / `visitILikeWithWildcards`; the defaults keep the regex-translated behaviour for existing visitors                                                                                                                                                                                                                                                     | Additive; Java 8 module, compiles under `--release 8`                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| `things/model` `WotValidationConfig`                                                                   | Public `JsonFields` constants (`CREATED`, `REVISION`, `MODIFIED`, `DELETED`, `METADATA`) for the neutral snapshot serializer; tagged `@since 4.0.0`                                                                                                                                                                                                                                                                                                                        | Additive; Java 8 module                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| Service `*.conf`                                                                                       | `snapshot-adapter` keeps its key and MongoDB default and now selects only the backend-neutral serializer; `persistence-backend-provider.extension-config.read-journal.entity` per service; `search.conf` gains `search-persistence-provider` (Mongo default); the Mongo persistence provider default and its `plugin-ids` live in the `reference.conf` of `internal/utils/persistence`                                                                                     | No MongoDB setting changes                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `bom/pom.xml`, the four service poms, root `pom.xml`, `build-images.sh`, `dockerfile-snapshot`         | R2DBC/reactor versions and `enforce-no-pekko-r2dbc-plugin` (bom); `enforce-no-r2dbc-in-service` in `things`/`policies`/`connectivity`/`thingsearch` `service/pom.xml`; root pom only adds the `ide-postgres-launcher` module; `BAKE_POSTGRES_EXTENSIONS=true` bakes the JARs into snapshot images — only `dockerfile-snapshot` has the `COPY`, `dockerfile-release` and `dockerfile-snapshot-arm64` ignore the staged JARs (§9)                                            | —                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `deployment/helm`                                                                                      | `postgres.tls.enabled` CA/cert mounts and `extraInitContainers` hooks in the things/policies/connectivity deployments; six chart files changed, `Chart.yaml` still 4.9.0 and `CHANGELOG.md` `[Unreleased]` empty                                                                                                                                                                                                                                                           | Search deployment misses the init containers and TLS mount although `values.yaml` promises `thingsSearch.extraInitContainers` is "injected" (§9); CI Helm lint fails until the chart version is bumped (§9); no `POSTGRES_*` or `ENFORCEMENT_CREATED_POLICY_ROLLBACK_*` variable is wired — those settings reach a Helm deployment only via raw `extraEnv` or the file-based overlay (§9)                                                                                                             |
| `.github/workflows/maven.yml`                                                                          | Job `postgres-its`: Testcontainers ITs on PostgreSQL 16, 17, 18; fails if any IT was skipped                                                                                                                                                                                                                                                                                                                                                                               | —                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |

Unrelated changes carried by the branch — kept in the change set, listed as such in its description, never
attributed to PostgreSQL in release notes: two upstream doc corrections — the connection-encryption migration matrix
("enabled + only current key" is an initial encryption, not an error; three workflows, not two) and the cleanup
retention defaults (`3d` things, `30d` policies/connectivity, Helm `30d`, not `0d`) — a search env binding
(`THINGS_SEARCH_UPDATER_STREAM_THING_CACHE_RETRY_DELAY` for `ditto.search.updater.stream.thing-cache.retry-delay`)
and the non-Postgres `.run/ThingsService.run.xml` (Temurin 25 JRE + envfile block). The branch changes nothing under
`gateway/`: the websocket/SSE fix that once rode on it became its own bugfix change.

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

- **Unit + Testcontainers ITs** in the three PostgreSQL modules (`postgres-client`, `persistence-r2dbc`,
  `search-r2dbc`) and the two API modules; `*IT` classes need Docker and skip without it, except the classloading
  IT — CI checks that none skipped. The database image is selected with `-Dditto.test.postgres.image` (CI matrix
  16/17/18).
- **HOCON lint tests** (`PostgresProfileHoconLintTest`, `PostgresSearchProfileHoconLintTest`) assert the shipped
  profiles: provider class, plugin blocks per entity, auto-start lists, no Mongo leftovers; plus
  `PersistenceBackendSelfCheckTest`, `*PersistencePluginConfigTest`.
- **Classloading IT** (`PostgresExtensionClassloadingIT`, needs no Docker) copies the real shaded JARs and loads them
  in child `URLClassLoader`s whose parent is the test classpath (standing in for the service allinone, SPI only); it
  also proves that a thin JAR without the base fails with an error naming the missing base JAR. It runs in **no CI
  job** yet: `postgres-its` lists three modules and the main job stops at `mvn package` (§9).
- **Service-level tests** (things/policies/connectivity/search) cover the wiring, the created-policy rollback,
  the encryption-migration refusal, moved search types.
- **Search parity corpus** (`internal/utils/search-r2dbc/src/test/resources/parity/`): the same queries run against
  both backends over one corpus of things and policies; every known divergence is listed in
  `divergence-allowlist.json`, so a new divergence fails the build.
- **System tests** (eclipse-ditto/ditto-testing) must pass on both databases through the same CI entry points; they
  did on 2026-10-27 (1,930 + 3 × 25 tests on each). The `database` workflow input that selects the backend is a
  ditto-testing change not yet merged there.
- **Benchmarks** (`internal/utils/persistence-bench`, `internal/utils/search-r2dbc/benchmark`) with committed
  evidence and EXPLAINs are development tooling and no part of the upstream change set (§9).

---

## 8. Performance summary (laptop Docker, both engines limited to 4 CPUs / 8 GiB unless stated)

Persistence, 250 k things / 35 M events, PostgreSQL 16 vs MongoDB 7.0, driver level:

| Scenario                                         | PostgreSQL                                           | MongoDB              | Note                                                                                                            |
|--------------------------------------------------|------------------------------------------------------|----------------------|-----------------------------------------------------------------------------------------------------------------|
| Recovery: latest snapshot p95                    | 1.01 ms                                              | 0.80 ms              | Mongo 1.3× faster                                                                                               |
| Recovery: replay 40–50 k events p95              | 258.6 ms                                             | 272.5 ms             | parity                                                                                                          |
| Single append p95                                | 2.90 ms                                              | 2.30 ms              | Mongo 1.3× faster                                                                                               |
| Fresh snapshot write p95                         | 2.33 ms                                              | 3.00 ms              | PG faster; overwrite at parity                                                                                  |
| Cleanup page p95 (payload-free, minAge 0)        | 1.22 cold / 0.51 warm                                | 1.66 ms (not re-run) | formerly an order of magnitude slower (TOAST payload reads after a fresh load); closed by the payload-free page |
| Single-entity cleanup, longest histories         | 1,820 ms                                             | 633 ms               | open: PG 2.9× slower; tolerable because Ditto paces cleanup                                                     |
| Unthrottled sweep of 22 M rows                   | 838 s                                                | 298 s                | identical rows deleted on both                                                                                  |
| Autovacuum settle after a mass prune (own loads) | 70 s at cost limit 2000 vs 3,172 s at server default | —                    | schema v3 sets the limit; 2000 raises snapshot-write p95 2–10× while it runs, kept on purpose                   |

Search (no container limits), 1 M things / 68.5 M flat rows, PostgreSQL 16 stock settings (128 MB `shared_buffers`,
JIT on), final re-run 2026-10-22: 8 of 20 query variants meet the 200 ms p95 gate; equality + range and count shapes
1.4–1.6 s → 267 / 205 ms p95 (below 200 ms in warm rounds); low-cardinality `ilike` 53 ms; infix `ilike` on a path
most things carry 357 ms, and 298 → 120 ms in the per-path-index A/B (51 MB index vs 304 MB table-wide in the
Phase-0 bench, built online in 43 s); sorting by `thingId` or `namespace` 0.3 ms p95 page 1, other sort fields
375–720 ms per page. The numbers are trends on one laptop, not plateau claims, and driver-level (no Pekko overhead).
Claims to avoid: "faster than MongoDB" in general, "production-proven at scale" (the RDS soak in §4.5 did not hold
200 upd/s through every window of its three hours at 10 M things), "zero-downtime migration".

---

## 9. What the implementation still owes this design

§1–§8 describe the target design and, where the branch differs from it, say so with a pointer here. This section
lists those differences: what the design requires and why, and what the branch does today. A row is closed when the
code matches its "Design" column.

| Topic                                     | Design and why                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Branch today                                                                                                                                                                                                                                              |
|-------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Connectivity remember-entities            | Pekko sharding's remember-entities store on PostgreSQL is `eventsourced`, as on MongoDB: a fifth table set `connection_remember_{journal,journal_seq,snaps}` (schema v4; additive, so `MIN_COMPATIBLE_VERSION` stays 2), two plugin blocks `ditto-postgres-connection-remember-journal` / `-snapshots` that wrap a Pekko-serialized payload in a JSON envelope `{serializerId, manifest, bytes (base64)}` and unwrap it on read — enabled on these two plugins only —, connectivity's sharding `journal-plugin-id` / `snapshot-plugin-id` resolved to them through the provider (the plugin-config accessors already exist), the JVM-wide `ddata` override dropped, the HOCON lint assertion flipped, an IT that restarts connectivity and sees remembered connections return. Why: Pekko's remember-entities events and shard state are Pekko-serialized bytes, which the JSONB journal rejects (§3.3), so the profile fell back to `ddata`; `ddata` with durable keys writes LMDB files under the working directory `/opt/ditto`, which the release image leaves root-owned while Helm runs the pods as uid 1000 (mechanism verified; the failure itself not yet reproduced on a release image); dedicated tables keep the persistence ping and cleanup away from Pekko's `/sharding/…` persistence ids, as MongoDB's separate collections do. Rejected: a typed JSON mapping of the remember events (couples Ditto to Pekko `@InternalApi` classes, and the snapshot store has no adapter hook) and a `bytea` payload column (changes the row contract and the replay path shared by all entities)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | `remember-entities-store = ddata` JVM-wide in the persistence profile (§2.3)                                                                                                                                                                              |
| Snapshot decryption without a current key | `ConnectionMongoSnapshotAdapter`, the snapshot serializer shared by both backends, decrypts with `old-symmetrical-key` when no `symmetrical-key` is set, as the event adapter already does. Disabling encryption is then the MongoDB procedure on both backends: `encryption-enabled = false`, no current key, the last key kept as `old-symmetrical-key`; reads decrypt, writes are plaintext. The 501 refusal (§3.6) drops its "keep `old-symmetrical-key` configured" sentence when no old key is configured (the boot WARN already appears only with an old key set)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | The adapter falls back to the old key only next to a current one and otherwise leaves the secret fields encrypted with a WARN, so a connection whose newest snapshot predates the key switch comes back with ciphertext in its secrets — on both backends |
| Encryption migration on PostgreSQL        | The three workflows of `MigrateConnectionEncryption` (initial encryption, key rotation, decryption when disabling) run on PostgreSQL as on MongoDB: a cluster-singleton actor streams `connection_snaps` and `connection_journal`, re-encrypts the secret fields, saves progress for resume, supports dry-run, throttling, abort and status queries, and answers 202                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | `EncryptionMigrationUnsupportedActor` answers 501 and the boot WARN tells operators to keep the old key                                                                                                                                                   |
| MongoDB-visible changes in shared code    | §1 promises MongoDB deployments unchanged behaviour, so three of the four MongoDB-visible changes of §5 leave this change set and the fourth stops failing on MongoDB. (1) Persistence ping: a bug-fix change of its own on master — an iteration ends on stream failure too, an ID-order walk resumes after the last pid reached, malformed pids are skipped — each with a test; it lands first, and this change keeps only the re-typing to `DittoReadJournal`. Why: on master a failed stream leaves `pingInProgress` set until a restart and one malformed pid fails every walk at the same position, on either backend; no toggle, because a toggle would keep the bug as the default. (2) Created-policy rollback: a bug-fix change of its own with the `created-policy-rollback` block in all four places (conf default + env var, Helm `values.yaml`, Helm template); it lands first. Why: master retries the compensating `DeletePolicy` without bound on the general ask-with-retry settings and `ThingSupervisorActor` keeps the created policy, so a later failing create could delete an unrelated policy — under MongoDB overload as under PostgreSQL. (3) WoT validation config snapshots: a bug-fix change of its own on master — the WoT actor resolves an `AbstractMongoSnapshotAdapter<WotValidationConfig>` of its own through an overridable hook of `AbstractPersistenceActor`, with a test that saves and recovers a WoT snapshot; it lands first, and this change re-types the hook to the neutral serializer. Why: on master every persistence actor takes the JVM-wide `ThingMongoSnapshotAdapter`, whose `isDeleted(Thing)` throws `ClassCastException` for a `WotValidationConfig`, so the first WoT snapshot (15 min after a change, or 500 changes) restarts the actor and no WoT snapshot is ever saved. (4) Boot self-check: stays in this change and runs with both providers; with `BackendFamily` MongoDB a mismatch logs a WARN naming the plugin, its class and the expected classes, and the service boots; with PostgreSQL it throws as today. Why: stock MongoDB configurations pass either way, a MongoDB deployment with a plugin class of its own must keep booting, and the warning keeps the diagnostic for a half-applied PostgreSQL overlay whose provider stayed MongoDB. Rejected: feature toggles for (1)–(3) (a toggle for a bug fix keeps the bug as the default and costs three config places each) and skipping the self-check on MongoDB (loses the diagnostic) | All four ship in this change set without a toggle; the self-check throws on both providers; the rollback block is not wired in Helm                                                                                                                       |
| Provisioning on managed PostgreSQL        | `deployment/postgres/provisioning/01-roles-and-grants.sql` runs as the master user of a managed service (RDS, Azure, Cloud SQL), who is no superuser: since PostgreSQL 16 a `CREATEROLE` user gets only `ADMIN OPTION` on the roles it creates and can neither `SET ROLE` to them nor hand them ownership (`CREATE SCHEMA ditto AUTHORIZATION ditto_ddl` fails with "must be able to SET ROLE"), so the script grants itself membership (`GRANT ditto_ddl TO CURRENT_USER`) right after the `CREATE ROLE`s. The DDL role gets `GRANT CREATE ON DATABASE`, because creating the trusted extension `pg_trgm` needs `CREATE` on the database, not on the schema. Every text that tells an operator to create the extension by hand says `CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA ditto`, because a superuser's plain `CREATE EXTENSION` lands in `public`, where the roles' `search_path = ditto` cannot resolve `gin_trgm_ops` (SQLSTATE `42704`, not mapped to the actionable error). The PostgreSQL 16 rule is taken from its documentation; the first step is to run the script as an RDS master user and confirm it                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | The script needs a real superuser (CI runs it as one); the DDL role cannot create `pg_trgm`; the error text suggests the schema-less statement                                                                                                            |
| Helm chart                                | The search deployment renders `thingsSearch.extraInitContainers` and mounts the PostgreSQL CA/client certificate like the three persistence deployments, since search connects with `verify-full` too; every chart change bumps `Chart.yaml` `version` and adds a `CHANGELOG.md` entry (CI lint). Activation on Helm is by a file-based overlay (the extension JARs and a `<service>-extension.conf` mounted into the pod); chart-native `postgres.*` values are a later step                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | `values.yaml` documents the hook; `thingssearch-deployment.yaml` renders neither it nor the mount; `Chart.yaml` is still 4.9.0 and `[Unreleased]` is empty                                                                                                |
| Classloading IT in CI                     | `postgres-extension-it` runs in CI: it is the only test of the three-JAR packaging and needs no Docker                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | `postgres-its` lists the three PostgreSQL modules only and the main job stops at `mvn package`                                                                                                                                                            |
| Search `null` operands                    | The SQL translation gives `in()` and the range predicates MongoDB's `null` semantics: `in([…, null])` matches null and missing values, `ge(null)` / `le(null)` behave like `eq(null)`, `gt(null)` / `lt(null)` match nothing — pinned by parity cases in `parity/queries.json`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | `CreateSqlPredicateVisitor` binds a `null` operand of `in` / `ge` / `gt` / `le` / `lt` as the text literal `'null'`; only `eq(null)` / `ne(null)` got the `type_rank` special case; the parity corpus has no such pins                                    |
| Benchmarks outside the tree               | The upstream change set carries no benchmark harness, results, READMEs or scripts, and the user documentation quotes no benchmark figures; the figures that justify design decisions stay in §8 with their environment                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | `internal/utils/persistence-bench` (a reactor module) and `internal/utils/search-r2dbc/benchmark` are in the branch; the user docs quote numbers                                                                                                          |
| Small cleanups                            | The three `*-pg-dev.conf` and the local overlays carry no Mongo `overrides { *-collection }` placeholders (dead on PostgreSQL); the `pooler-mode` comment in `ditto-postgres-client.conf` says that the value gates index maintenance (§4.3) instead of "informational only"; `deployment/postgres-local/run-compound-postgres.sh` takes `mvn` from `PATH`; `BAKE_POSTGRES_EXTENSIONS` is documented as a snapshot-image switch that behaves the same in both snapshot dockerfiles, while the release dockerfile ignores it by design (stock images carry no PostgreSQL code)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | Placeholders present; comment wrong; a personal Homebrew path as the `MVN` default; the switch is undocumented and a no-op in `dockerfile-release` and `dockerfile-snapshot-arm64`                                                                        |
| Documentation                             | Two user pages: `installation-postgresql.md` (activation, provisioning, configuration) and `operating-postgresql.md` (schema lifecycle and upgrades, self-heal, pool budget, TLS, PgBouncer, monitoring, differences). Activation per platform: Docker Compose mounts the JARs and a `/opt/ditto/<service>-extension.conf` overlay with the production include (not a `*-dev` profile); Helm uses the file-based overlay. The missing-base-JAR failure is documented by its symptom, `NoClassDefFoundError … PostgresConfig`, and its fix (mount the base JAR), because no check can run before the provider class links. The cutover (§4.6): initial fill via `force-update` namespace by namespace, then default background sync, with the no-backpressure and throttle-at-start caveats                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | One 650-line section in `installation-extending.md`; the Compose example includes the `things-dev` profile; neither the missing-base error nor the cutover is documented                                                                                  |
| Release notes                             | The 4.0.0 release-notes page carries the PostgreSQL items in the house style (one line per item with a link) and a breaking-changes section for extension authors (§5: moved search types, `SearchUpdateMapper`, `SearchUpdateObserver` / `Metadata`); no standalone page; the unrelated changes listed at the end of §5 are not attributed to PostgreSQL                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | A standalone `release_notes_postgres_search.md` stub, written at guide detail                                                                                                                                                                             |

Known limitations that stay (documented): snapshot stream reads whole snapshots; search has no runtime self-heal;
`ilike` folding and sort-cost differences (§4.7); U+0000 rejected by the journal; no persistence data migration
tooling in this repository. Open search parity gaps besides the `null` operands above: two recorded `empty()`
divergences — `empty-wildcard-property` (`parity/queries.json`: MongoDB matches a thing whose feature lacks the
property, PostgreSQL does not) and `empty-overmatch-array-of-array` (`parity/divergence-allowlist.json`: PostgreSQL
matches where MongoDB does not).

---

## 10. Where the implementation lives

| Topic                                     | Location                                                                                                                                                         |
|-------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| User-facing setup and operations          | `documentation/.../installation-extending.md` §"PostgreSQL …" (to become two pages, §9), `installation-operating.md`, `operating-configuration.md`               |
| Operator runbook, provisioning, PgBouncer | `deployment/postgres/README.md`, `deployment/postgres/provisioning/01-roles-and-grants.sql`                                                                      |
| Local development                         | `deployment/postgres-local/README.md`, `.run/* (Postgres).run.xml`, the `ide-postgres-launcher` Maven module (pom-only classpath aggregator for the run configs) |
| Persistence schema                        | `…/persistence/postgres/schema/PostgresSchema.java` (Javadoc explains every table decision)                                                                      |
| Search schema and translator              | `…/postgres/search/PostgresSearchSchema.java`, `…/search/query/`, `…/search/pathindex/`                                                                          |
| Search parity corpus                      | `internal/utils/search-r2dbc/src/test/resources/parity/` (`queries.json`, `divergence-allowlist.json`, the corpus of things and policies)                        |
| Benchmark method and evidence             | `internal/utils/persistence-bench/benchmark/README.md`, `internal/utils/search-r2dbc/benchmark/README.md` (leave the tree with §9)                               |
| Release-note material, breaking changes   | `documentation/.../release_notes_postgres_search.md` (folded into the 4.0.0 notes, §9)                                                                           |
