---
title: Release notes — Things-Search on PostgreSQL
tags: [release_notes]
published: false
keywords: release notes, announcements, changelog, postgresql, search
summary: "PENDING snippet — Things-Search on PostgreSQL. Fold into the next release_notes_<version>.md once the version is cut."
permalink: release_notes_postgres_search.html
---

<!--
  PENDING RELEASE-NOTES SNIPPET (published: false — not linked from the site nav).

  This page holds the ready-to-ship release notes for the OPTIONAL PostgreSQL thing-search backend. The Ditto
  version that will carry this feature is not decided yet (latest cut release is 3.9.0 — release_notes_390.md),
  so this content is staged here, next to the versioned release-notes pages, rather than in a versioned file.
  When the version is decided, fold the sections below into that release's release_notes_<version>.md and either
  delete this page or flip published:true if a standalone landing page is wanted.

  @since tags across the new surface assume the next release is 3.10.0 — adjust together with this note if the
  version differs.
-->

Eclipse Ditto's **thing-search** service can now **optionally** run its search index on **PostgreSQL** instead of
MongoDB. MongoDB remains the default search backend — this is strictly opt-in and independent of which backend the
persistence services (things / policies / connectivity) use. See
[Installation / extending — PostgreSQL search backend](installation-extending.html#postgresql-search-backend) for the
setup steps.

## Deployment — three-JAR extension matrix

The PostgreSQL backend now ships as **layered drop-in JARs** rather than a single shaded JAR. A shared **base** JAR
carries all third-party runtime; **thin** JARs carry only Ditto implementation classes:

| JAR | Contents | Mounted by |
|-----|----------|------------|
| `ditto-postgres-client-extension` (base) | shared `postgres-client` infra + ALL third-party (r2dbc-postgresql, r2dbc-pool, reactor, netty incl. resolver-dns, scram) | every Postgres service |
| `ditto-postgres-persistence-extension` (thin) | event-sourcing persistence classes only | things / policies / connectivity |
| `ditto-postgres-search-extension` (thin) | thing-search classes only | thing-search (only if search runs on Postgres) |

Deployment matrix:

* **things / policies / connectivity on Postgres:** mount **base + persistence** (2 JARs). **This is a change from the
  earlier single `ditto-internal-utils-persistence-r2dbc-extension` JAR** — a persistence service on Postgres now
  mounts two JARs instead of one.
* **thing-search on Postgres:** mount **base + search** (2 JARs). A search service left on MongoDB mounts nothing.
* All mounted Postgres extension JARs **must come from the same Ditto release** — a boot self-check (a version marker
  in each JAR) fails fast on a mismatch, and a thin JAR mounted without its base fails fast with an actionable
  "requires base `ditto-postgres-client-extension`" error.

## Prerequisite — the `pg_trgm` extension

The PostgreSQL search schema requires the `pg_trgm` extension (used for the `like`/`ilike` trigram indexes). It is a
*trusted* extension on PostgreSQL ≥ 13, so the DDL role creates it itself without superuser privileges once it has
`CREATE` on the target database (the provisioning script grants it). The schema bootstrap fails with an actionable error
if the role is not permitted to create it. If you pre-install the extension by hand, pin its schema:
`CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA ditto;`.

## Prerequisite — a UTF8 database with a UTF-8 `LC_CTYPE`

The search database must be UTF8-encoded, with a UTF-8-based `LC_CTYPE` (e.g. `C.UTF-8` or `en_US.UTF-8`; the official
`postgres` image's default qualifies) and the libc collation `C.utf8`. The search service checks this at every start
and refuses to start otherwise, with an error that names what is missing and how to create a qualifying database.
CloudNativePG's `initdb` default `LC_CTYPE` is `C`: set `bootstrap.initdb.localeCType: C.UTF-8`. Details and a query
to check a database:
[Installation / extending — PostgreSQL search backend](installation-extending.html#prerequisites-migration-and-known-limitations).

## Migration — re-index cutover (no copy tooling)

The search index is a **rebuildable projection** of the things data, so there is no data-migration step and no copy
tooling. The cutover to PostgreSQL is: point the search service at an empty PostgreSQL database and let the
**background-sync** stream regenerate the index. Search results are **incomplete until the initial re-index finishes**
— plan this re-index window into the cutover. The same mechanism is the recovery path if the index is ever lost.

## `like`/`ilike` performance — known limitation

Sub-3-character `like`/`ilike` patterns cannot be served by a trigram index and degrade to a scan — this mirrors
MongoDB's own unanchored-regex degradation and is caught by the existing slow-query log. Separately, `ilike` on a path
that many things carry is slow under the single table-wide trigram index: on the 1M-thing benchmark corpus, infix
`ilike` on `features/*/properties/prop0` took 305.8–391.5 ms p95 in three runs. For such paths, operators can now
configure **per-path search indexes** (`ditto.postgresql.search.path-indexes`, the PostgreSQL counterpart of MongoDB
custom search indexes): a partial trigram index restricted to one path, built in the background with
`CREATE INDEX CONCURRENTLY` after the schema bootstrap, repaired if a build fails, and dropped when its path is removed
from the list (an unset list manages nothing; an explicitly empty one drops them all). With it, the same queries took
111.1–111.2 ms p95. Building the indexes needs a direct (not transaction-pooled) database connection. Search instances
can be started and restarted while an index is built: a start whose schema is up to date runs no schema DDL, and one
that has to run DDL gives up after 500 ms instead of stalling index writes. See the PostgreSQL search section of the
installation guide.

## Equality + range filters

A filter that combines an equality with a two-sided numeric range on one field (`ge`/`gt` and `le`/`lt` on the same
field) no longer always starts from the equality: the search counts the index entries of both in the same statement
and starts from the smaller one; a two-sided range alone keeps the previous plan, because starting from it was measured
slower (about 50× to 2,000× for a range over most things). The candidates of that start are handed to the rest of the
filter once per thing, which keeps the planner from reading every row of the other condition (582 → 162 ms for an
equality + range filter on the 1M-thing benchmark corpus). Results never change. Because two range conditions may be met by
different array elements (as on MongoDB), the range start also uses the new system index `sf_multi` (search schema
version 3), which the search service builds in the background with
`CREATE INDEX CONCURRENTLY` after the schema bootstrap, like the per-path indexes (it needs the same direct database
connection). Until an instance sees the index valid, range filters keep the previous plan. Version-2 images run against
the version-3 schema unchanged. See "Range filters and the system index sf_multi" in the installation guide.

## Mongo-only search knobs ignored on PostgreSQL

Search configuration specific to MongoDB — for example per-metric MongoDB index hints and configurable custom MongoDB
search indexes — is **ignored (logged with a WARN)** when the PostgreSQL search backend is active. These knobs have no
direct PostgreSQL analog and are safely left in place for Mongo-search deployments; per-path PostgreSQL search indexes
are configured separately under `ditto.postgresql.search.path-indexes` (see above).

## Background-sync and updater-stream settings — unchanged on PostgreSQL

The `ditto.search.updater.*` settings listed here (`event-processing-active`, `background-sync.enabled` /
`quiet-period` / `tolerance-window`, `stream.write-interval`, the `policy-cache` / `thing-cache` `retry-delay`s) are
consumed by the backend-neutral updater layer and behave identically on PostgreSQL; the `ditto-postgres-search`
profile defines no `ditto.search.*` key. The background-sync bookmark is persisted as a single row in the
`search_sync` table and the tolerance-window comparison uses the thing's `_modified` timestamp stored in
`search_things.t_modified`. `stream.persistence.with-acks-writeConcern` is a MongoDB write concern and has no effect
on PostgreSQL.

Independent of the backend, `search.conf` now binds `THINGS_SEARCH_UPDATER_STREAM_THING_CACHE_RETRY_DELAY` to
`ditto.search.updater.stream.thing-cache.retry-delay`; previously that environment variable was silently ignored
(the `policy-cache` counterpart was already bound). Note that `stream.policy-cache.retry-delay` is parsed but not
consumed by the enforcement flow (only the thing-cache delay is), and `event-processing-active=false` only logs a
warning — both are long-standing service-level behaviours, unrelated to the storage backend.

## Write-acknowledgement divergence for no-change re-writes

One behavioral divergence is scoped to **write acknowledgements only and never affects query results**: when a thing
update maps to no actual storage change, MongoDB computes an incremental diff and — finding it empty — skips the write
and dispatches a **weak** acknowledgement, whereas PostgreSQL performs its unconditional doc-row upsert and returns a
**normal (strong) success** acknowledgement instead; both backends persist identical index state, so search results are
unaffected.

## Breaking change — `SearchUpdateMapper` re-typed to the neutral write model

`SearchUpdateMapper` (the `search-update-mapper` extension point) no longer works on the MongoDB-specific
`MongoWriteModel`. It is now typed against the **backend-neutral**
`org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel`. This change is **binary- and
source-breaking** for custom implementations, but it is **japicmp-invisible**: `ditto-thingsearch-service` is not a
japicmp-gated module (services are not binary-compatibility-checked), so no build-breaking japicmp diff will surface
it — these release notes are the only record of the break for downstream custom-mapper authors.

New signature:

```java
public abstract class SearchUpdateMapper implements DittoExtensionPoint {
    protected final ActorSystem actorSystem;
    protected SearchUpdateMapper(final ActorSystem actorSystem, final Config config);
    public abstract Source<org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel, NotUsed>
        processWriteModel(org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel writeModel,
                          org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel lastWriteModel);
    public static SearchUpdateMapper get(final ActorSystem actorSystem, final Config config);
}
```

Removed from the old (Mongo-typed) API: the `protected final int maxWireVersion` field, the
`SearchUpdateMapper(ActorSystem, Integer)` constructor, the private `getMaxWireVersion(ActorSystem)` helper (which
probed `MongoClientExtension.get(...).getUpdaterClient().getMaxWireVersion()`), and the
`protected Source<MongoWriteModel, NotUsed> toIncrementalMongo(...)` helper. The abstract method's element type
changed from `MongoWriteModel` to the neutral `AbstractWriteModel`. A custom mapper is now simply a neutral
write-model → write-model transform; the bundled default (`DefaultSearchUpdateMapper.processWriteModel(...)`) is the
identity function (`return Source.single(writeModel);`).

**Migration for custom `search-update-mapper` implementations:** re-type the override to the neutral
`AbstractWriteModel`; any Mongo-specific manipulation (BSON diffing, wire-version probing) must move behind the
(Mongo-only) `SearchUpdaterFlow` seam — it is no longer available at the mapper extension point.

## `SearchUpdateObserver` — documented, but no code change required

`SearchUpdateObserver` (the `search-update-observer` extension point) was flagged in the design as a breaking change,
but its public surface was **already backend-neutral** and required **no code change**:

```java
public interface SearchUpdateObserver extends DittoExtensionPoint {
    void process(final Metadata metadata, @Nullable final JsonObject thingJson);
    static SearchUpdateObserver get(final ActorSystem actorSystem, final Config config);
}
```

(`Metadata` here is `org.eclipse.ditto.thingsearch.persistence.api.model.Metadata`.) No migration needed for custom
observers.

## New extension point — `ditto.extensions.search-persistence-provider`

Thing-search's persistence backend is now pluggable via a new `DittoExtensionPoint`, `SearchPersistenceProvider`
(`org.eclipse.ditto.thingsearch.persistence.api`). The bundled default (`MongoSearchPersistenceProvider`) is unchanged
behavior — MongoDB remains the out-of-the-box thing-search backend. The PostgreSQL search extension JAR swaps this
FQCN to its own provider via the opt-in `ditto-postgres-search` config profile; no action is required for existing
Mongo deployments.

## Non-breaking addition — `PredicateVisitor.visitLikeWithWildcards`

`org.eclipse.ditto.rql.query.criteria.visitors.PredicateVisitor<T>` gained new **default** methods giving raw wildcard
access to `like`/`ilike` predicates, for backends (such as the SQL/trigram-index PostgreSQL backend) that need the
un-translated wildcard pattern rather than the Mongo-regex-translated form. Because the additions are `default`
methods, this is japicmp-safe (source- and binary-compatible). No migration needed for existing `PredicateVisitor`
implementers; they inherit the default behavior automatically.
