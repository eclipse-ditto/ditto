# PostgreSQL Persistence and Search Backend Design Document

**Related Issue**: [GitHub #2629 - Support PostgreSQL as persistence and search backend via pluggable persistence providers](https://github.com/eclipse-ditto/ditto/issues/2629)

**Status**: Implemented on branch `feat/postgres-persistance-search`; pull request
[#2651](https://github.com/eclipse-ditto/ditto/pull/2651); §9 lists the open items
**Last Updated**: 2026-11-13

---

## TL;DR

Ditto can store things, policies and connections — and, independently, its search index — in PostgreSQL instead
of MongoDB. It is opt-in per service through two drop-in JARs and a few lines of HOCON; MongoDB stays the default and
nothing changes for API users or existing deployments, apart from four bug fixes of `master` that the change carries
(§9).

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
  The one exception is the four bug fixes of `master` listed in §9.
- Every background mechanism works on both backends: cleanup, persistence ping, snapshot streaming (search
  background sync), namespace purge, health checks, field-level encryption of connection secrets and its migration
  (initial encryption, key rotation, decryption when disabling) — with the one gap recorded in §3.6 (the snapshot
  stream reads whole snapshots).
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
                 │                              payloadStore(entity) (bulk rewrite) │
                 │                              verifyPluginWiring (boot check)     │
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
`PersistenceBackendProvider` also offers a boot-time **plugin-wiring check**, the hook
`verifyPluginWiring(serviceName)`: a no-op by default, called by the root actor right before `bootstrapSchema()`, and
implemented only by the PostgreSQL provider (`PostgresPluginWiringCheck` in `persistence-r2dbc`). There, the
provider-declared auto-start plugin IDs and every entry of the effective Pekko `auto-start-*` lists must resolve to
the PostgreSQL journal or snapshot-store class (a plugin ID is a HOCON path, as Pekko resolves it; an entry without a
`class` is its own error), so a half-applied overlay (PostgreSQL provider with MongoDB plugins) fails at boot instead
of on the first write. A failure is logged with the plugin, its class and the expected classes and stops the service
the way a failed schema bootstrap does (§3.2: coordinated shutdown with reason `BootstrapFailed`, exit code 1 under
`exit-jvm = on`, unless `bootstrap.exit-on-failure = false`), so the orchestrator restarts the pod and a corrected
configuration is picked up; running before `bootstrapSchema()`, the check lets a mis-wired overlay touch no
database. The MongoDB provider inherits the no-op: a MongoDB deployment runs no check and boots whatever plugin
classes it wires, as before — a MongoDB deployment with a plugin class of its own must keep booting, and no
MongoDB-side code, not even a WARN, is the strongest form of the §1 promise. The reverse overlay (PostgreSQL plugins
wired, provider left at MongoDB) is therefore not detected; the shipped profile sets provider and plugin lists in one
file, so reaching it takes a hand-split configuration.

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
| `internal/utils/postgres-extension-it`               | IT: the three shaded JARs load in isolated class loaders over an allinone-like parent; a thin JAR without the base fails with an actionable error; in CI: `postgres-its` job (§7) | —        |
| `ide-postgres-launcher`, `deployment/postgres-local` | Local multi-service runs on PostgreSQL: IDE classpath module, IntelliJ run configs in `.run/`, overlay confs + CLI launcher, Compose for the database                             | —        |
| `deployment/postgres`                                | Operator runbook, two-role provisioning SQL, PgBouncer notes                                                                                                                      | —        |

### 2.3 Packaging and activation

- **Layered drop-in JARs.** `ditto-postgres-client-extension` (base, ≈3.8 MB: the `postgres-client` module itself,
  r2dbc-spi/-pool/-proxy, r2dbc-postgresql, reactor-core/-pool/-netty-core, SCRAM, netty resolver-dns/codec-dns and
  the optional native transports) plus `ditto-postgres-persistence-extension` (≈124 KB) or
  `ditto-postgres-search-extension` (≈274 KB); sizes from a local `0-SNAPSHOT` build. The images already put
  `/opt/ditto/extensions/*` on the classpath. The three extension poms set `maven-jar-plugin` `forceCreation`:
  without it an incremental `mvn install` keeps the previous shaded JAR as the "up-to-date" project artifact and
  `shade:shade` merges it over the fresh module JAR, shipping the previous build's classes with BUILD SUCCESS;
  `build-images.sh` refuses such a JAR before baking (every class of the module JAR compared by length
  and CRC-32 with the shaded JAR's copy; `CHECK_ONLY=1` runs only that check).
  A Maven enforcer execution `enforce-no-r2dbc-in-service`, declared in each of the four persistence/search service
  poms, keeps R2DBC out of those modules: compile and runtime scope would leak r2dbc into the service allinone (the
  shade plugin resolves runtime scope), provided scope would let a module's main code compile against PostgreSQL
  types. `connectivity/service` alone exempts test scope, which never ships and carries the PostgreSQL integration
  test of the encryption migration (§7). `bom/pom.xml` adds `enforce-no-pekko-r2dbc-plugin`, which bans
  `org.apache.pekko:pekko-persistence-r2dbc*` project-wide.
- **One include activates a backend.** Each thin JAR ships a profile — `ditto-postgres-persistence.conf`,
  `ditto-postgres-search.conf`, both pulling in `ditto-postgres-client.conf` from the base — that swaps the
  provider extension class and defines the Pekko plugin blocks (`ditto-postgres-<entity>-journal` / `-snapshots`,
  entities `things`, `policies`, `connections`, `wot`, plus the two `connection-remember` blocks of §3.3) and the
  `ditto.postgresql.*` client defaults. The
  persistence include must be a top-level `include required(classpath("…"))` (the plugin blocks are top-level;
  `required` makes a missing thin JAR fail at config load instead of silently starting on MongoDB — the dev-only
  `*-pg-dev.conf` and `deployment/postgres-local/*.conf` use the plain form), needs the
  narrowing below and a `read-journal.entity` value (the stock service confs set it). Connectivity's remember-entities
  store keeps its `eventsourced` mode, on the profile's `connection-remember` plugin blocks (§3.3). The search include
  needs none of this.
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
- **Dev profiles** `<service>-pg-dev.conf` (four, shipped inert in the allinone JARs) and six `.run/*-Postgres.run.xml`
  run configurations (one per service plus the compound; the gateway one carries no PostgreSQL config) exist for
  local runs only.
- **Helm.** The chart (4.11.0) activates no backend by itself, and `/opt/ditto/<service>-extension.conf` is
  chart-owned there (rendered from the chart's own template), so the activation config is injected as a file-based
  overlay: an operator ConfigMap holding the production include and the narrowing, mounted through `extraVolumes` /
  `extraVolumeMounts` and selected with `extraEnv` `HOSTING_ENVIRONMENT=filebased` plus
  `HOSTING_ENVIRONMENT_FILE_LOCATION` — a configuration layer above the service's own; the chart itself never sets
  `HOSTING_ENVIRONMENT`. Each of the four PostgreSQL-capable deployments (`policies`, `things`, `connectivity`,
  `thingsSearch`) offers `extraInitContainers` (rendered after the built-in init containers) to stage the extension
  JARs into a volume mounted at `/opt/ditto/extensions`, and `extraEnv` for the `POSTGRES_*` variables;
  `postgres.tls` (off by default) renders the CA, and for mTLS the client certificate, into a managed
  `<fullname>-postgres-tls` Secret (or takes `existingSecret`) and mounts it read-only at `postgres.tls.mountPath`
  into the same four deployments. A MongoDB deployment renders the same manifests as before. Chart-native
  `postgres.*` connection values are a later step.

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
  sharding's remember-entities state, gets a JSON envelope on plugins of its own (§3.3).
- **One journal table for all entity types** (the upstream plugins' layout) instead of a table set per entity. MongoDB
  keeps one collection set per entity type, and every per-entity walk — ping, cleanup, namespace purge, snapshot
  streaming — scans a whole table in pid order; a shared table would make each walk filter by pid prefix and put
  Pekko's own `/sharding/…` persistence ids next to Ditto's, which is why the remember-entities store has a table set
  of its own (§3.3). The sixteen tables of §3.1 form one DDL component with one version row, so the roll rule of §3.2
  has a single row to check.
- **Search.** The alternatives there were measured rather than argued and are recorded with the decision: a GIN index
  on the `jsonb` document (§4.1), a semi-join translation of RQL (§4.4), generic plans (§4.4), table-wide versus
  per-path trigram indexes (§4.3, §8), MongoDB's TTL index versus the reaper (§4.2).

---

## 3. Persistence backend

### 3.1 Data model

Per entity (`things`, `policies`, `connections`, `wot`) and for connectivity's remember-entities store
(`connection_remember`, §3.3), three tables, plus the single-row progress table of connectivity's encryption migration
(§3.3); every persistence service creates all sixteen plus the shared `schema_version`:

| Table                              | Columns                                                                             | Key                         |
|------------------------------------|-------------------------------------------------------------------------------------|-----------------------------|
| `<e>_journal`                      | `pid`, `sn`, `seq` identity, `manifest`, `tags TEXT[]`, `event JSONB`, `written_at` | `(pid, sn)` + GIN on `tags` |
| `<e>_journal_seq`                  | `pid`, `highest_sn`, `deleted_to`                                                   | `(pid)`                     |
| `<e>_snaps`                        | `pid`, `sn`, `snapshot JSONB`, `lifecycle`, `written_at`                            | `(pid, sn, written_at)`     |
| `connections_encryption_migration` | `id`, `progress JSONB`, `updated_at`                                                | `(id)`                      |

- `pid` is `COLLATE "C"` in the three per-entity tables (the progress table has no `pid`; the catalog verifier checks
  every table that has one). `seq` is the monotonic offset for `eventsByTag`; `tags` carry Ditto's journal tags
  (`always-alive`, `priority-<n>`).
- `<e>_journal_seq` keeps the highest sequence number independent of journal rows, so it survives cleanup.
- The snapshot key includes `written_at` so a re-snapshot at the same `sn` is allowed; `DELETED` snapshots are
  filtered in the application after the newest-per-pid pick (schema v2 dropped the `(pid, sn DESC)` index, v3 the
  partial `lifecycle` index — neither served a query).
- Autovacuum storage parameters are set in the bootstrap transaction: scale factor 0.01 on every table (since v2);
  schema **v3** (`MIN_COMPATIBLE_VERSION = 2`) adds cost limit 2000 on the two pruned tables `<e>_journal` and
  `<e>_snaps` (§8).
- Schema **v4** (`MIN_COMPATIBLE_VERSION` stays 2) appends the `connection_remember` table set after the four entity
  sets: v3's statements stay byte-identical, and v2/v3 code never touches the new tables, so a rolling upgrade is safe.
- Schema **v5** (`MIN_COMPATIBLE_VERSION` stays 2) appends `connections_encryption_migration`, the one row
  (`id = 'current'`) in which connectivity's encryption migration keeps its progress record (what MongoDB keeps in the
  `connection_encryption_migration` collection); no `pid`, no autovacuum storage parameters. v4's 45 statements stay
  byte-identical, and v2–v4 code never touches the table.

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
  the pod; `bootstrap.exit-on-failure = false` keeps the JVM up without its root actor instead. A failed
  plugin-wiring check (§2.1) and an unreadable `ditto.postgresql` block stop the service by the same rule and with
  the same reason.
- **Self-heal at runtime** (persistence services only, not search): SQLSTATE `42P01` triggers a schema check, DDL
  where needed, one retry; counted by `ditto_persistence_schema_self_heal`; `POSTGRES_SCHEMA_SELF_HEAL=false` fails
  loud instead. The heal follows the boot's rules (no DDL against a newer stored version, the same 500 ms lock
  timeout) but skips the catalog verification and the version upsert. Documented as masking data loss, exactly like
  MongoDB's implicit collection creation.
- **Two roles:** DDL owner (`ditto.postgresql.pool.ddl-credentials`, `POSTGRES_DDL_USER`) vs DML-only runtime role;
  when no DDL credentials are set the runtime role runs the DDL. The bootstrap and heals open one short-lived
  connection outside the pool as the DDL role; a search index build holds its own non-pooled DDL connection for the
  whole build.
- **Provisioning for managed services:** `deployment/postgres/provisioning/01-roles-and-grants.sql` runs as the owner
  of the database, which on Amazon RDS, Azure Database for PostgreSQL or Cloud SQL is the master user, no superuser.
  Since PostgreSQL 16 a `CREATEROLE` user gets only `ADMIN OPTION` on the roles it creates and can neither `SET ROLE`
  to them nor hand them ownership, so the script grants itself membership (`GRANT ditto_ddl TO CURRENT_USER`) right
  after the `CREATE ROLE`s; the schema handover and the default privileges then work through that membership. The DDL
  role gets `GRANT CREATE ON DATABASE`, because creating the trusted extension `pg_trgm` needs `CREATE` on the
  database, not on the schema; the extension then lands in the first schema of the role's `search_path`, `ditto`.
  Every text that tells an operator to pre-install the extension by hand says `CREATE EXTENSION IF NOT EXISTS pg_trgm
  SCHEMA ditto`, because a superuser's plain `CREATE EXTENSION` lands in `public`, where the roles' `search_path`
  cannot resolve `gin_trgm_ops`; the search service's boot error names that case and its remedy (§4.2). Confirmed on
  Amazon RDS for PostgreSQL 16.15 (2026-11-05) and reproduced in CI by `PostgresManagedServiceProvisioningIT`, which
  runs the script as a `CREATEROLE` non-superuser that owns the database.

### 3.3 Events and snapshots as JSONB

- Service-specific adapters `ThingPostgresEventAdapter`, `PolicyPostgresEventAdapter`,
  `ConnectivityPostgresEventAdapter`, `WotValidationConfigPostgresEventAdapter` extend
  `AbstractPostgresEventAdapter` in `internal/utils/persistence` (JSON only, no R2DBC). Connection secrets keep the
  AES-GCM field encryption: events and snapshots decrypt with the current key and fall back to `old-symmetrical-key`,
  also when only the old key is set (the shared `ConnectionMongoSnapshotAdapter` mirrors the event adapters), so
  disabling encryption is the MongoDB procedure on both backends: encryption off, no current key, the last key kept
  as `old-symmetrical-key`; reads decrypt, writes are plaintext. Without any key the fields stay encrypted (the
  snapshot adapter logs a WARN, the event adapters are silent).
- **Encryption migration.** The three workflows of the DevOps command `migrateEncryption` (initial encryption, key
  rotation, decryption when disabling, plus `migrateEncryptionStatus` and `migrateEncryptionAbort`) are backend-neutral:
  `EncryptionMigrationActor`, a cluster singleton that connectivity's root actor starts whenever encryption is enabled
  or an old key is set, works on a `PersistedPayloadStore` obtained from the provider's `payloadStore(entity)` accessor
  (`persistence-api`), which streams snapshots and journal entries as JSON behind an opaque cursor, writes rewritten
  entries back and keeps a flat JSON progress record. The MongoDB store (`MongoPersistedPayloadStore`, Mongo zone) runs
  the statements the migration used to run itself. The PostgreSQL store (`PostgresPersistedPayloadStore`,
  `persistence-r2dbc`) pages `connections_snaps` by `(pid, sn, written_at)` and `connections_journal` by `seq` with a
  fresh keyset statement per page (no portal across the throttle's pauses), filters with
  `col #> '{…}'::text[] IS NOT NULL` per secret pointer (MongoDB's `$exists`: a JSON `null` is present, a missing path
  is not), rewrites a batch in one transaction (`UPDATE … WHERE pid = … AND sn = … [AND written_at = …]`; a failed
  batch writes nothing) and keeps the progress row in `connections_encryption_migration` (§3.1). It runs on the shared
  pool, so its `close()` is a no-op. Dry run, throttling, abort, resume and status behave as on MongoDB;
  `EncryptionMigrationPostgresIT` (§7) proves the three workflows end to end on PostgreSQL.
- `jsonb` normalises: key order is not preserved after reload, `1E10` is re-rendered, U+0000 cannot be stored (the
  journal rejects such an event with a class-22 WARN; the API already rejects it with 400 for thing/policy payloads).
- Each journal and snapshot plugin block stores its payload through a `payload-codec`. The default, `json-text`,
  accepts a Ditto `JsonValue` or JSON text and stores it unchanged; every entity plugin uses it. Connectivity's
  remember-entities store is the one producer of Pekko-serialized bytes: Pekko's `eventsourced` store persists each
  shard's `EntitiesStarted`/`EntitiesStopped` events and `State` snapshots, and the coordinator's remembered shard
  ids, through the shard region's journal and snapshot plugins — on MongoDB through the `connection_remember_*`
  collections. The profile gives it the peer blocks `ditto-postgres-connection-remember-journal`/`-snapshots` on the
  `connection_remember` table set with `payload-codec = pekko-serialization`: the payload is wrapped in a JSON
  envelope `{serializerId, manifest, bytes (base64)}` on write and deserialized again on read, so no event adapter
  is involved. `ConnectivityRootActor` sets the two plugin ids on the shard region's `ClusterShardingSettings` from
  the provider's plugin config, and `remember-entities-store` stays `eventsourced` on both backends; the blocks are
  not auto-started, as on MongoDB. The dedicated tables keep the persistence ping and the cleanup away from Pekko's
  `/sharding/…` persistence ids. `PostgresRememberEntitiesIT` restarts a single-node cluster on the same database
  and sees the remembered entities return without a message.
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
  backends and gets a bug-fix PR of its own on master after this change (§5, §9).
- **Cleanup pages are payload-free** on PostgreSQL too: a page requesting no fields selects only pid, sn, lifecycle
  and write time of the newest snapshot (§8: formerly the one scenario where PostgreSQL was an order of magnitude
  slower). MongoDB already returned no payload here, so the change is PostgreSQL-only; `Cleanup` itself changed in
  types only.
- **Streaming is bound to one entity type** per service (`read-journal.entity`, required with no default: `things`,
  `policies` or `connections`; WoT validation config lives in the things JVM and is no read-journal consumer); only
  things streams snapshots in Ditto. Known gap: the snapshot stream returns whole snapshots although background sync
  asks for three fields.
- **Boot order** in a persistence service: provider construction → `verifyPluginWiring()` (the PostgreSQL provider's
  check, §2.1) → `bootstrapSchema()` (DDL under the advisory lock) → `getReadJournal()` (lazy, first use) → shard
  regions and health route.

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
  base types first, so the symptom is `NoClassDefFoundError … PostgresConfig`, which the install page documents with
  its fix (`installation-postgresql.md`, "A thin JAR without its base").

### 3.6 Differences from MongoDB (persistence)

JSON normalisation and U+0000 (§3.3); live journal queries complete (§3.4); snapshot stream reads whole snapshots;
streaming bound to the service's entity; ping walks windows and counts slow failures; pool fails fast (4 s) instead of
waiting 2 min; late commits after the caller's deadline remain possible on both backends (Ditto request handling, not
the backend).

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
  first DDL statement; two misprovisionings there are re-classified into the same kind of message, each naming its
  remedy: the DDL role lacks `CREATE` on the database (SQLSTATE `42501`; a trusted extension needs it, schema
  ownership does not give it; the provisioning script grants it) and an extension a superuser installed with a plain
  `CREATE EXTENSION`, which lands in `public`, off the roles' `search_path = ditto`, so the trigram index build fails
  on `gin_trgm_ops` (SQLSTATE `42704`; remedy `ALTER EXTENSION pg_trgm SET SCHEMA ditto`).
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
- **`null` operands:** `in(…, null)`, `ge(null)` and `le(null)` match a JSON `null` and a missing value, `gt(null)`
  and `lt(null)` match nothing, like MongoDB (`{f: null}` has no `exists` clause there); `eq(null)` keeps requiring
  the value to exist, as on MongoDB. A placeholder that resolves to null is a null operand; in `eq` without the
  existence requirement. A null-including operand is a per-thing probe (the same plan class as
  `empty()`'s absent branch), never an index-driven leg — put a selective conjunct next to it. Pinned by the
  null-operand parity battery of `parity/queries.json`.
- **`empty()` on a wildcard feature path** (`features/*/properties`, `features/*/properties/<p>`,
  `features/*/desiredProperties`, `features/*/desiredProperties/<p>`, `features/*/definition`) is MongoDB's
  `elemMatch(f, and(empty, auth))`: a thing matches when at least one feature visible to the subjects holds `null`,
  `[]`, `{}` or `""` at the path or has no such key; a thing without features never matches, and `not(empty(…))` is
  the plain negation (a thing without features matches it; a thing with one feature lacking the key and another
  holding it does not). The translation anchors on the feature boundary row (`wpath = '/features/*'`, one per
  feature, auth on its `f_id`) and probes the rows of the same feature — an empty marker, with the deeper-path
  anti-probe scoped to that feature (a non-empty object is not empty, on the bare containers too), or none at
  all — a per-thing probe like the null operands above, never an index-driven leg: 1 + up to 3N PK-prefix probes
  per candidate thing (N = its features) where the old form ran one, the same class as the null operands' missing
  branch; not measured. Pinned by the `empty()` wildcard parity battery of `parity/queries.json`.
- **`exists()` / `empty()` on the complete `attributes` or `features` object** need no translation of their own: as
  on MongoDB the bare name is a slash-mapped simple field, answered from the container row the flattener stores at
  `/attributes` or `/features` (an empty object has one, a missing object has none), with the thing-level
  authorization of that path. Pinned by `PostgresExistsIT`, the counterpart of the MongoDB `ExistsIT` with the same
  things and expectations, and by the complete-object battery of `parity/queries.json`.
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
where nobody starts from empty. The cutover procedure (documented in `operating-postgresql.md`, section "Cutover to
the PostgreSQL search index") uses the `force-update` devops piggyback, namespace by namespace: it bypasses the
inconsistency-filter throttle but stays paced by the snapshot-stream batch and the updater's write rate, has no
backpressure, and needs background sync enabled. The throttle cannot be raised
at runtime: the devops config modification re-parses the block (so `enabled` and the intervals take effect), but the
metadata source was built with the throttle values when the actor was created, so new throttle values apply only
after a restart.

### 4.7 Differences from MongoDB (search)

A `like` without a run of three literal letters/digits (or prefix-only) stays on the `wpath`-bounded `sf_text`
B-tree instead of the trigram index — a per-path scan, not a table scan; `ilike` folds case character by character
under `C.utf8` (`ß`≠`ss`, dotless `ı` matches neither `i` nor `I`); sorting by any field other than
`thingId`/`namespace` costs in proportion to the matching visible things; MongoDB-only knobs (index hints, custom
Mongo indexes) are ignored with a WARN; purged namespaces stay findable until the reaper removes them (MongoDB:
until TTL); no runtime self-heal in search. `empty()` also matches an attribute or property whose value is an array
of arrays (the flattener stores one valueless array stub per inner array, indistinguishable from an empty array);
MongoDB does not, and RQL cannot address the inner arrays on either backend. The other recorded divergences concern
sorting and the sudo wildcard, each pinned per backend in `parity/divergence-allowlist.json`: object-valued sort keys
tie and fall through to the `thingId` tiebreak (MongoDB orders them by content); an array holding a scalar next to a
nested array sorts by the scalar ascending but as missing descending (MongoDB sorts by its min/max element, inner
arrays included); the cursor encodes `null` for a null, object, empty-array or array-of-array boundary value where
MongoDB encodes the whole value; a `features/*/…` sort key is rejected (MongoDB silently builds a never-matching path
and returns the results unsorted); and under sudo, `features/*` value predicates are answered per feature where MongoDB
degrades to a dotted path that matches nothing — or, for a null-including operand, everything.

---

## 5. Changes to shared code

These change behaviour for MongoDB deployments too, or are the places where the seams cut through existing code.
The goal in §1 allows no MongoDB behaviour change; the rows that have one are collected in §9 with the ways to
resolve them.

| Area                                                                                                   | Change                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | Why / MongoDB impact                                                                                                                                                                                                                                                                                                                                                                                                                               |
|--------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `internal/utils/persistence` (Mongo module)                                                            | Neutral types moved out to `persistence-api`; `MongoPersistenceBackendProvider`, `MongoSnapshotCodec`, `MongoPersistencePluginConfig`, ops factory and a `reference.conf` (Mongo provider default + `plugin-ids`) added; `MongoPersistedPayloadStore` (the MongoDB `PersistedPayloadStore`) runs the statements of the connection encryption migration, moved from connectivity unchanged                                                                                                                                                              | Package move (internal modules, not japicmp-guarded; out-of-tree users recompile) of the config interfaces `ActivityCheckConfig`, `SnapshotConfig`, `EventConfig`, the streaming actors, `PidWithSeqNr`, `SnapshotFilter`, `SnapshotAdapter` and the ops interfaces                                                                                                                                                                                |
| `internal/utils/persistent-actors`                                                                     | `AbstractPersistenceActor` snapshot envelope via codec; `AbstractPersistenceSupervisor` and `Cleanup` re-typed to `DittoReadJournal` (error mapping unchanged); `PersistencePingActor`: an iteration now ends on stream failure too (before, a failed stream left `pingInProgress` set forever), an ID-order walk resumes after the last pid reached, malformed pids are skipped                                                                                                                                                                       | Behavioural on MongoDB: the ping changes only (failure cases); they get a bug-fix PR of their own on master after this change (§9); the supervisor answers errors as before                                                                                                                                                                                                                                                                        |
| `things/service` enforcement (+ `internal/utils/config`, `policies/enforcement`)                       | `CreatedPolicyRollback`; `AskWithRetryConfig` block `created-policy-rollback` in `internal/utils/config/…/ditto-enforcement.conf` (ask 3 s, 3 retries, back-off 500 ms–4 s, ≈22.5 s worst case; env `ENFORCEMENT_CREATED_POLICY_ROLLBACK_*`, not wired in Helm), accessor in `policies/enforcement`; `ThingSupervisorActor` forgets the created policy once the twin signal is answered                                                                                                                                                                | A failed thing create under overload retries the rollback of its implicit policy with bounded retries instead of the former unbounded loop; the caller keeps the original error; a later failing `CreateThing` can no longer delete an unrelated policy. Behavioural on MongoDB, with a new config block; gets a bug-fix PR of its own on master, Helm wiring included, after this change (§9)                                                     |
| `things/service` WoT validation config                                                                 | Snapshots are serialized by `WotValidationConfigSnapshotSerializer`, chosen through a `resolveSnapshotSerializer` hook of the persistence actor                                                                                                                                                                                                                                                                                                                                                                                                        | Before, the WoT actor obtained the things snapshot adapter through `SnapshotAdapter.get`, whose `isDeleted(Thing)` throws a `ClassCastException` at the first WoT snapshot (the actor restarts; no WoT snapshot is ever saved). A bug fix on MongoDB; gets a bug-fix PR of its own on master after this one (§9)                                                                                                                                   |
| `things/policies/connectivity` `*RootActor`, `*OperationsActor`, `*StreamingActorCreator`, supervisors | Resolve plugin IDs, read journal, ops and streaming through the provider; call the boot-time plugin-wiring hook (§2.1)                                                                                                                                                                                                                                                                                                                                                                                                                                 | Pure wiring; `PersistenceBoundaryArchTest` in each service bans `com.mongodb..`, `org.bson..`, the Mongo persistence plugin, `MongoReadJournal` and every Postgres type in main code (whitelist: the `…mongo.config` classes and `AbstractMongoEventAdapter`). The plugin-wiring hook is a no-op on the MongoDB provider, so a custom journal or snapshot `class` or an auto-start entry without `class` boots there as on master (§2.1)           |
| `connectivity/service`                                                                                 | `EncryptionMigrationActor` works on the provider's `PersistedPayloadStore` (JSON only, no driver, no backend check; the root actor starts the singleton on every backend as before), `ConnectivityPostgresEventAdapter`; `ConnectionMongoSnapshotAdapter` re-typed to `SnapshotSerializer` and decrypting with `old-symmetrical-key` alone when no current key is set (mirrors the event adapters); `ConnectivityRootActor` sets the shard region's remember-store plugin ids from the provider (on MongoDB the same `*-remember-*` ids the HOCON set) | The migration's MongoDB statements, query, writes, progress document and DevOps path are unchanged (they moved into the Mongo zone); the old-key-alone decryption is shared code, applies to both backends and is MongoDB-visible change (4) of §9                                                                                                                                                                                                 |
| `thingsearch/service`                                                                                  | `Mongo*` implementations now implement the neutral `thingsearch/persistence-api` contracts; `ThingsSearchPersistence`, `ThingsSearchUpdaterPersistence`, `ThingsAggregationPersistence`, `ResultList`, `TimestampedThingId`, `Metadata`, `EvaluatedPolicy`, `IndexLengthRestrictionEnforcer` moved there; neutral `AbstractWriteModel`/`ThingWriteModel`/`ThingDeleteModel` ADDED next to the Mongo-specific classes of the same name, which stay in `thingsearch/service`                                                                             | **Breaking for extension authors**: new extension point `ditto.extensions.search-persistence-provider`; every out-of-tree implementation of the three moved persistence interfaces breaks on the package move; `SearchUpdateMapper` re-typed to the neutral `AbstractWriteModel` and lost `maxWireVersion`, its `(ActorSystem, Integer)` constructor and `toIncrementalMongo`; custom `SearchUpdateObserver`s break on the `Metadata` package move |
| `rql/query`                                                                                            | `Like*PredicateImpl` now pass the raw wildcard pattern to new `PredicateVisitor` defaults `visitLikeWithWildcards` / `visitILikeWithWildcards`; the defaults keep the regex-translated behaviour for existing visitors                                                                                                                                                                                                                                                                                                                                 | Additive; Java 8 module, compiles under `--release 8`                                                                                                                                                                                                                                                                                                                                                                                              |
| `things/model` `WotValidationConfig`                                                                   | Public `JsonFields` constants (`CREATED`, `REVISION`, `MODIFIED`, `DELETED`, `METADATA`) for the neutral snapshot serializer; tagged `@since 4.0.0`                                                                                                                                                                                                                                                                                                                                                                                                    | Additive; Java 8 module                                                                                                                                                                                                                                                                                                                                                                                                                            |
| Service `*.conf`                                                                                       | `snapshot-adapter` keeps its key and MongoDB default and now selects only the backend-neutral serializer; `persistence-backend-provider.extension-config.read-journal.entity` per service; `search.conf` gains `search-persistence-provider` (Mongo default); the Mongo persistence provider default and its `plugin-ids` live in the `reference.conf` of `internal/utils/persistence`                                                                                                                                                                 | No MongoDB setting changes                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `bom/pom.xml`, the four service poms, root `pom.xml`, `build-images.sh`, `dockerfile-snapshot*`        | R2DBC/reactor versions and `enforce-no-pekko-r2dbc-plugin` (bom); `enforce-no-r2dbc-in-service` in `things`/`policies`/`connectivity`/`thingsearch` `service/pom.xml`; root pom only adds the `ide-postgres-launcher` module; `BAKE_POSTGRES_EXTENSIONS=true` bakes the JARs into the snapshot images (both snapshot dockerfiles) after refusing any shaded extension JAR whose classes differ from its module JAR (length + CRC-32 of every class; `CHECK_ONLY=1` runs only that check); `dockerfile-release` ignores the staged JARs by design       | —                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `deployment/helm`                                                                                      | `postgres.tls.enabled` CA/cert mounts and `extraInitContainers` hooks in the things/policies/connectivity/things-search deployments (§2.3); `Chart.yaml` 4.11.0 with a `CHANGELOG.md` `[4.11.0]` entry that covers every chart change                                                                                                                                                                                                                                                                                                                  | Off by default: a MongoDB deployment renders the same manifests as before. No `POSTGRES_*` variable is wired by design (activation stays the file-based overlay; chart-native values are a later step) and `ENFORCEMENT_CREATED_POLICY_ROLLBACK_*` comes with the rollback's own bug-fix PR (§9); both reach a Helm deployment today only via raw `extraEnv` or the overlay                                                                        |
| `.github/workflows/maven.yml`                                                                          | Job `postgres-its`: Testcontainers ITs of the four PostgreSQL modules (the classloading IT among them) and connectivity's `EncryptionMigrationPostgresIT` on PostgreSQL 16, 17, 18; fails if any IT was skipped                                                                                                                                                                                                                                                                                                                                        | —                                                                                                                                                                                                                                                                                                                                                                                                                                                  |

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
  IT (needs none) and `SearchBackendParityIT` (no skip guard on purpose: it fails without Docker) — the `postgres-its`
  job checks that none skipped, for the PostgreSQL modules and for connectivity's PostgreSQL IT alike. The database
  image is selected with `-Dditto.test.postgres.image` (CI matrix 16/17/18).
- **HOCON lint tests** (`PostgresProfileHoconLintTest`, `PostgresSearchProfileHoconLintTest`) assert the shipped
  profiles: provider class, plugin blocks per entity, auto-start lists, no Mongo leftovers; plus
  `PostgresPluginWiringCheckTest`, the two `*ProviderPluginWiringTest`s, `*PersistencePluginConfigTest`.
- **Classloading IT** (`PostgresExtensionClassloadingIT`, needs no Docker) copies the real shaded JARs and loads them
  in child `URLClassLoader`s whose parent is the test classpath (standing in for the service allinone, SPI only); it
  also proves that a thin JAR without the base fails with an error naming the missing base JAR. It runs in the
  `postgres-its` CI job as the fourth of its `POSTGRES_IT_MODULES`: the job's install step builds the three shaded
  JARs (`-am`), the verify step runs the IT, and the no-skip check reads its `failsafe-summary.xml`.
- **Service-level tests** (things/policies/connectivity/search) cover the wiring, the created-policy rollback,
  the encryption migration end to end on PostgreSQL (`EncryptionMigrationPostgresIT` in `connectivity/service`: the
  real actor, provider, event adapter and snapshot serializer over a Testcontainers PostgreSQL — the three workflows,
  dry run, abort and resume with a new actor instance, status after a restart; the PostgreSQL modules are test-scoped
  there; it runs in the `postgres-its` CI job in a step of its own, once per PostgreSQL version of the matrix
  (`mvn verify -pl connectivity/service -Dit.test=EncryptionMigrationPostgresIT -Dfailsafe.failIfNoSpecifiedTests=true`:
  `-Dit.test` is reactor-wide, and a plain module entry would also run the module's MongoDB
  `ConnectionPersistenceOperationsActorIT`); the no-skip check covers `connectivity/service` too, so the IT's Docker
  `Assume` skip turns the job red), moved search types.
- **Search parity corpus** (`internal/utils/search-r2dbc/src/test/resources/parity/`): the same queries run against
  both backends over one corpus of things and policies; every known divergence is listed in
  `divergence-allowlist.json`, so a new divergence fails the build.
- **System tests** (eclipse-ditto/ditto-testing) must pass on both databases through the same CI entry points; the
  pull request names the runs. The `database` workflow input that selects the backend is a
  ditto-testing change not yet merged there.
- **Benchmarks**: the harnesses (`internal/utils/persistence-bench`, the `search-r2dbc` bench package and its
  `benchmark/` scripts), their committed results, READMEs and EXPLAINs are development tooling and no part of the
  upstream change set. They stay in this branch's history up to `83dcad63e3`, the last commit that contains
  `internal/utils/persistence-bench/benchmark/README.md`,
  `internal/utils/search-r2dbc/benchmark/README.md` and
  `internal/utils/search-r2dbc/src/test/resources/bench/README.md` (§10).

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
| Ping walk window, 1 M entities × 2 events        | ~1 ms per window, 2–3 s per walk (warm cache)        | —                    | ID order, 500 ids per window; flat across the id space, every entity tagged or 1 % tagged clustered at the end  |
| Ping walk window, 250 k things × ~140 events     | 572 ms median / 1.04 s max (87 % untagged, 8 GiB)    | —                    | a window reads every journal row of its untagged entities; basis of the runbook's batch-size advice             |
| Priority-order ping walk, 1 M ids × 2 events     | 3.3 s per walk (2,001 statements)                    | —                    | windows of 500 ids, then an in-memory sort; before, one statement over all tagged rows hit statement_timeout    |
| Priority-order ping walk, 250 k × ~140 events    | 183 s per walk (501 statements, slowest 799 ms)      | —                    | per-window cost grows with the untagged entities' event history, as for the ID-order walk                       |

Search (no container limits), 1 M things / 68.5 M flat rows, PostgreSQL 16 stock settings (128 MB `shared_buffers`,
JIT on), final re-run 2026-10-22: 8 of 20 query variants meet the 200 ms p95 gate; equality + range and count shapes
1.4–1.6 s → 267 / 205 ms p95 (below 200 ms in warm rounds); low-cardinality `ilike` 53 ms; infix `ilike` on a path
most things carry 357 ms, and 298 → 120 ms in the per-path-index A/B (51 MB index vs 304 MB table-wide in the
Phase-0 bench, built online in 43 s); sorting by `thingId` or `namespace` 0.3 ms p95 page 1, other sort fields
375–720 ms per page. The numbers are trends on one laptop, not plateau claims, and driver-level (no Pekko overhead).
Not claimed: "faster than MongoDB" in general, "production-proven at scale" (the RDS soak in §4.5 did not hold
200 upd/s through every window of its three hours at 10 M things), "zero-downtime migration".

Method, harnesses, raw results and EXPLAIN plans: commit `83dcad63e3` of this branch's history, see
`internal/utils/persistence-bench/benchmark/README.md` (persistence),
`internal/utils/search-r2dbc/benchmark/README.md` and
`internal/utils/search-r2dbc/src/test/resources/bench/README.md` (search) at that commit. Nothing of it is in the
tree after `83dcad63e3`.

---

## 9. What the implementation still owes this design

§1–§8 describe the branch as it is. This section lists what stays open on the day the pull request opens — each item
with the design it is measured against, why it stays open, and what the branch does today — and, below the table, the
known limitations that stay by design. A row is closed when the code matches its "Design" column; a closed row
disappears (its history lives in the commits).

| Topic                                  | Design and why                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | Branch today                                                                                                                                                                                    |
|----------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| MongoDB-visible changes in shared code | §1 promises MongoDB deployments unchanged behaviour. The four MongoDB-visible changes of §5 are bug fixes of master that this change set carries because PostgreSQL runs into the same defects; each gets a bug-fix PR of its own on master, which follows this pull request (investigation first) and does not block it; this change keeps its copy of the fix meanwhile, and once a fix is merged on master the branch keeps only its re-typing (`DittoReadJournal`, the neutral snapshot-serializer hook). No toggle for any of them: a toggle for a bug fix keeps the bug as the default and costs three config places each. (1) Persistence ping — an iteration ends on stream failure too, an ID-order walk resumes after the last pid reached, malformed pids are skipped, each with a test. Why: on master a failed stream leaves `pingInProgress` set until a restart and one malformed pid fails every walk at the same position, on either backend. (2) Created-policy rollback — bounded retries through the `created-policy-rollback` block (§5); the bug-fix PR wires the block in all four places (conf default + env var, Helm `values.yaml`, Helm template), this change set wires the conf default and the env var only. Why: master retries the compensating `DeletePolicy` without bound on the general ask-with-retry settings and `ThingSupervisorActor` keeps the created policy, so a later failing create could delete an unrelated policy — under MongoDB overload as under PostgreSQL. (3) WoT validation config snapshots — the WoT actor resolves a snapshot serializer of its own through the overridable `resolveSnapshotSerializer` hook of `AbstractPersistenceActor`, with a test that saves and recovers a WoT snapshot. Why: on master every persistence actor takes the JVM-wide `ThingMongoSnapshotAdapter`, whose `isDeleted(Thing)` throws `ClassCastException` for a `WotValidationConfig`, so the first WoT snapshot (15 min after a change, or 500 changes) restarts the actor and no WoT snapshot is ever saved. (4) Connection snapshot decryption with only an old key — `ConnectionMongoSnapshotAdapter` mirrors the event adapters (current key primary, old key as fallback, old key alone when no current key is set), so disabling encryption follows the MongoDB procedure on both backends and a snapshot older than the key switch no longer comes back with ciphertext in its secrets. Why: master has the same defect while its event adapter already decrypts with the old key alone. A former fifth change, the boot self-check, is closed: it became the provider hook `verifyPluginWiring` that only the PostgreSQL provider implements, with no MongoDB-side code (§2.1) | The four fixes ship in this change set without a toggle; none of their bug-fix PRs on master is open yet; the `created-policy-rollback` block is not wired in Helm                              |
| Release notes                          | The 4.0.0 release-notes page carries the PostgreSQL items in the house style (one line per item with a link) and a breaking-changes section for extension authors (§5: the moved search types, `SearchUpdateMapper`'s write model, `SearchUpdateObserver` / `Metadata`, the two `PredicateVisitor` defaults `visitLikeWithWildcards` / `visitILikeWithWildcards`); no standalone page; the unrelated changes listed at the end of §5 are not attributed to PostgreSQL. Why open: the maintainers' 4.0.0 page does not exist yet; the notes follow the pull request                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | No release-notes page on the branch; `operating-postgresql.md` names the two extension-point breaks meanwhile                                                                                   |
| Search parity: array elements          | Array elements follow MongoDB: a `null` element inside an array is a `type_rank = 1` row like any other null leaf, so `eq(path,null)`, `ne` and `in` on `[1, null]` answer as on MongoDB; and `empty(p)` matches `p: [{"a":1}, {}]` as MongoDB does (array-element equality with `{}`). Each shape first gets a parity corpus thing and query (MongoDB result = truth), then either a fix — the flattener change needs a re-index of existing indexes (§4.6), the deeper-object anti-probe a per-element scope — or a documented difference in §4.7 and the user docs. Why open: found in review after the parity corpus was frozen; neither shape has a parity case or an allowlist entry yet                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | the array loop tests `element.isArray()` before recursing — and `JsonNull.isArray()` is true — so the element becomes a rank-5 stub; the anti-probe is scoped per thing/feature; no parity case |

Known limitations that stay (documented): the snapshot stream reads whole snapshots (§3.4); search has no runtime
self-heal (§3.2); `ilike` folding, sort-cost and the sort-order differences of §4.7; U+0000 rejected by the journal
(§3.3); the plugin-wiring check does not detect the reverse overlay and nothing checks the auto-start narrowing at
boot (§2.1, §2.3); no persistence data migration tooling in this repository (§1). One recorded `empty()` divergence
stays: `empty-overmatch-array-of-array` (`parity/divergence-allowlist.json`: PostgreSQL matches an attribute or
property whose value is an array of arrays, MongoDB does not) — closing it means changing what the flattener stores
(a stub distinguishable from an empty array) and the rank-5 folding of the sort and cursor code, with a re-index of
existing indexes (§4.6), for a shape RQL cannot query into on either backend.

---

## 10. Where the implementation lives

| Topic                                     | Location                                                                                                                                                                                                                                                                                                                                                                                                                        |
|-------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| User-facing setup and operations          | `documentation/.../installation-postgresql.md` (JARs, activation config, provisioning, env vars, Compose, Helm), `documentation/.../operating-postgresql.md` (schema lifecycle, self-heal, pool, PgBouncer, TLS, ping, encryption, search tunables and indexes, cutover, differences, vacuum); `installation-extending.md` keeps a `### PostgreSQL backends` pointer; `installation-operating.md`, `operating-configuration.md` |
| Operator runbook, provisioning, PgBouncer | `deployment/postgres/README.md`, `deployment/postgres/provisioning/01-roles-and-grants.sql`                                                                                                                                                                                                                                                                                                                                     |
| Local development                         | `deployment/postgres-local/README.md`, `.run/*-Postgres.run.xml`, the `ide-postgres-launcher` Maven module (pom-only classpath aggregator for the run configs)                                                                                                                                                                                                                                                                  |
| Persistence schema                        | `…/persistence/postgres/schema/PostgresSchema.java` (Javadoc explains every table decision)                                                                                                                                                                                                                                                                                                                                     |
| Search schema and translator              | `…/postgres/search/PostgresSearchSchema.java`, `…/search/query/`, `…/search/pathindex/`                                                                                                                                                                                                                                                                                                                                         |
| Search parity corpus                      | `internal/utils/search-r2dbc/src/test/resources/parity/` (`queries.json`, `divergence-allowlist.json`, the corpus of things and policies)                                                                                                                                                                                                                                                                                       |
| Benchmark method and evidence             | commit `83dcad63e3` of this branch's history, the last commit that holds the three benchmark READMEs (§7, §8)                                                                                                                                                                                                                                                                                                                   |
| Release-note material, breaking changes   | none on the branch yet; the 4.0.0 release-notes page (§9) will carry them; until then `operating-postgresql.md`, note under "Prerequisites, migration and known limitations", names the two extension-point breaks                                                                                                                                                                                                              |
