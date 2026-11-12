# Search Service Deep Dive

## Overview

This deep dive explains the Things-Search service, which provides query capabilities over Things based on their content. 
Unlike the direct Things API (which requires knowing Thing IDs), Search allows finding Things by their attributes, features, and metadata.

## Goals

- Understand search service architecture
- Learn search index structure and schema
- Understand how search index is updated
- Learn how queries are executed against the search index

## Search vs Direct Access

**Things API**:
- Direct access to Things by ID
- Requires knowing the Thing ID(s) upfront
- Fast, immediate consistency

**Search API**:
- Find Things based on their values/content
- Query using RQL (Resource Query Language)
- Requires separate indexed representation
- Eventually consistent

## Search Index Structure

### Indexed Fields

Operators can statically configure which fields are searchable ("indexed fields"):
- Configured in "search.conf" at key `ditto.search.namespace-indexed-fields`
- Configurable per namespace

### Search Persistence

The backend is chosen by the `ditto.extensions.search-persistence-provider` extension point
(`SearchPersistenceProvider`): `MongoSearchPersistenceProvider` by default, `PostgresSearchPersistenceProvider` opt-in
(see [PostgreSQL Search Backend](#postgresql-search-backend)).

**MongoDB collections**:
- `search` - Main search index documents
- `searchSync` - Background sync progress tracking
- `test` - Health check collection (MongoDB read/write verification)

**PostgreSQL tables**:
- `search_things` - One row per Thing: the enforced Thing document as JSONB, `global_read`, policy ids, `delete_at`
- `search_flat` - One row per JSON node of each Thing, two under `features` (the flattened side table queries run on)
- `search_sync` - Background sync bookmark

### Index Document Structure (MongoDB)

**One document per Thing** containing:

```
{
  "_id": "thing-id",
  "gr": ["subject:..."],  // Global read subjects
  "t/": { ... },          // Thing data
  "p/": { ... },          // Policy data
  "f/": { ... },          // Features
  "if/": [ ... ]          // List of indexed fields
}
```

**Special notation**:
- Middle dot (·) used in paths to avoid conflicts with attributes/properties
- Example: `attributes·temperature` instead of `attributes.temperature`

**Embedded authorization**:
- `gr` field stores "global read subjects" (subjects with READ at Thing root level)
- Enables **one-step search** combining user filter + authorization filter
- MongoDB performs both filtering and authorization in single query

## Updating the Search Index

### Update Triggers

Three mechanisms trigger search index updates:

#### 1. Thing Update Events
- Thing modifications emit events
- Search service subscribes to these events
- Triggers immediate index update

#### 2. Policy Update Events
- Policy modifications affect authorization
- Updates `gr` field in search documents
- Triggers re-indexing of affected Things

#### 3. Background Sync (BackgroundSyncActor, BackgroundSyncStream)
- Periodically compares Thing persistence with search persistence
- Detects and fixes inconsistencies
- **Throttled**: 600 Things per iteration
- **Idle period**: 5 minutes between iterations
- **Stateful**: Progress persisted, each iteration continues from previous end
- **Purpose**: Eventually consistency guarantee

### Update Architecture

#### ThingUpdater (Sharded Actor - State Machine)

**Purpose**: Per-Thing search index update coordinator

**Characteristics**:
- One sharded actor instance per Thing
- Maintains current DB view (ThingWriteModel) of search document
- State recovered when actor starts
- Converts Thing JSON to DB model
- Performs DB write operations

**Lifecycle**:
- Triggered when search index must be modified
- Caches current search document state
- Processes updates with batching and retries

#### ThingsUpdater (Singleton Actor)

**Purpose**: Central coordinator for all search updates

**Responsibilities**:
- Subscribes to Thing and Policy change events
- Forwards events to appropriate ThingUpdater actors
- Provides statistics and monitoring

### Search Update Flow (Detailed)

1. **Event reception**: Thing/Policy event received
2. **Metadata storage**: Store event metadata
   - Thing ID, Thing revision
   - Policy ID, Policy revision
   - Update reason, timers, helpers
3. **Event merging**: Wait for tick (default: 1s) and merge multiple events
   - Multiple rapid updates batched together
   - Revision number increased
4. **Processing**: On next tick, process merged metadata
5. **EnforcementFlow**:
   - Retrieve current Thing from cache
   - Convert Thing JSON to DB model (ThingWriteModel)
   - Mix in policy data (EnforcedThingMapper)
   - Calculate usage for reporting (UsageReportingSearchUpdateObserver)
   - Remove non-indexed fields (SearchUpdateIndexTrimFlowFactory)
   - Calculate diff vs stored DB model (BsonDiff)
6. **SearchUpdaterFlow** (`MongoSearchUpdaterFlow` or `PostgresSearchUpdaterFlow`):
   - Write calculated diff to MongoDB; on PostgreSQL re-flatten the whole document and write the row diff
   - Send search-persisted acknowledgement (if requested)
   - Return success/failure to ThingUpdater
7. **Wait for next change** or retry on error

**Event stashing**:
- Events received during processing are stashed
- Processed on next tick
- Ticks during processing are discarded/skipped

## Querying the Search Index

### Query Capabilities

**Request methods**:
- HTTP API (`GET /search/things`)
- WebSocket API (subscriptions)
- Connections API (programmatic queries)

**RQL (Resource Query Language)**: Filter expressions using operators like `eq()`, `ne()`, `gt()`, `lt()`, `in()`, `like()` combined with logical operators `and()`, `or()`, `not()`. Example: `and(eq(attributes/manufacturer,"ACME"),gt(attributes/temperature,20))`. See [RQL.md](RQL.md) for complete syntax reference.

**Sorting**:
- Sort on arbitrary indexed fields
- Ascending or descending
- Multiple sort criteria

**Field projection**:
- Select specific fields to return
- Reduces response size

**Pagination**:
- Cursor-based paging for large result sets
- Efficient scrolling through results

**Namespace filtering**:
- Limit search to specific namespace(s)
- Default: All namespaces in solution

### Query Execution Flow

1. **Accept request**: Gateway receives search/count request via HTTP/WebSocket/Connections
2. **Start query actor**: Spawn QueryThingsPerRequestActor instance
3. **Forward to the search service**: `EdgeCommandForwarderActor` publishes the `ThingSearchCommand` via Pekko distributed pub/sub to
   the `SearchActor` (a search command belongs to no single entity, so it is not sharded)
4. **Query translation**: Convert RQL to the backend's query
   - MongoDB: `CreateBsonVisitor`, `GetFilterBsonVisitor` in `org.eclipse.ditto.thingsearch.service.persistence.read`
   - PostgreSQL: `CreateSqlPredicateVisitor`, `SearchQueryAssembler` in `internal/utils/search-r2dbc`
   - **User filter** + **Authorization filter** combined
5. **Execution**: Run the query against the search collection, or `search_flat` + `search_things`
6. **Return Thing IDs**: Search returns list of matching Thing IDs
7. **Retrieve Things**: QueryThingsPerRequestActor fetches Things
   - Via ThingsAggregatorProxyActor → ThingsAggregatorActor
   - Reads from ThingPersistenceActor instances
8. **Filter by permissions**: the things service enforces READ on each retrieved Thing
   - Removes fields user cannot read
   - Per-subject permission checking
9. **Aggregate and return**: QueryThingsPerRequestActor sends response to requester

## PostgreSQL Search Backend

Opt-in through `ditto.extensions.search-persistence-provider` (`SearchPersistenceProvider` in
`thingsearch/persistence-api`): `PostgresSearchPersistenceProvider` lives in `internal/utils/search-r2dbc` and ships as the
`ditto-postgres-search-extension` JAR on top of `ditto-postgres-client-extension` (`.claude/context/persistence-backends.md`).
The provider hands out the read, updater and aggregation persistence, the `SearchUpdaterFlow`, the background-sync
bookmark, the health check and the `QueryBuilderFactory`; `ThingUpdater`, `EnforcementFlow`, background sync and all
`ditto.search.updater.*` settings are shared and behave the same on both backends.

**Data model** (`PostgresSearchSchema`): PostgreSQL's GIN index on `jsonb` cannot serve ranges, `like` or typed sorting on
arbitrary paths, so each Thing is stored twice. `search_things` holds the enforced document (results, authorization via
`global_read`, sorting); `search_flat` holds one row per JSON node (two under `features`) — `(thing_id, path, wpath,
f_id, ord, type_rank, val_bool, val_num, val_text)`, built by `ThingFlattener`. `wpath` is the path with feature ids
replaced by `*`, so `features/*/properties/x` hits one index key; objects, empty containers and `null` get rows too (they
serve `exists()`). `search_sync` is the background-sync bookmark. `delete_at` plus a reaper (`PostgresDeleteAtReaper`) replaces MongoDB's TTL index for
namespace purges; per-path trigram indexes (`ditto.postgresql.search.path-indexes`) are the counterpart of custom MongoDB
search indexes.

**Query translation** (`search/query/`): `CreateSqlPredicateVisitor` turns each RQL predicate into an `EXISTS` over
`search_flat`; `SearchQueryAssembler` adds the authorization check per candidate row and, for `and(eq, …)` / `and(in, …)`,
a materialised selective leg (`SelectiveLegCte`). `like`/`ilike` reach the SQL side as raw wildcard patterns through
`PredicateVisitor.visitLikeWithWildcards` / `visitILikeWithWildcards` ([RQL.md](RQL.md)) and are served by a trigram
index. `ThingWriteModel`, `AbstractWriteModel` and `ThingDeleteModel` exist twice — the neutral ones in
`thingsearch/persistence-api`, the MongoDB ones of the same name in `thingsearch/service` — so check the import.

**Parity is the arbiter.** `SearchBackendParityIT` (needs Docker and never skips) writes one corpus of Things and
policies (`internal/utils/search-r2dbc/src/test/resources/parity/`) through both production write paths and runs
`queries.json` — every
operator × path kind × auth situation, sorts, counts and cursor walks — against both backends; result sets (and order
where the sort is total) must match, and MongoDB's result is the truth. A divergence is allowed only when
`divergence-allowlist.json` has an entry, a query case names it as `divergence` and pins both backends' observed results;
`expectNonEmptySudo` guards against cases that pass on empty results. Allow-listed divergences: `empty()` also matches an
array of arrays; content nested inside a direct array of arrays is flattened to a single valueless stub on PostgreSQL
(inner elements are not enumerated); object-valued sort keys tie and fall through to `thingId`; a scalar next to a nested
array sorts differently ascending and descending; the cursor encodes null/object/array boundary values differently; a
`features/*/…` sort key is rejected where MongoDB silently ignores it; case-insensitive matching of U+00DF LATIN SMALL
LETTER SHARP S (`ß`) and U+0130 LATIN CAPITAL LETTER I WITH DOT ABOVE (`İ`) differs between MongoDB's case-insensitive
regex and PostgreSQL's `lower()`-based `ILIKE`; under sudo, `features/*` value predicates are answered per feature where
MongoDB matches nothing (or everything, with a null operand). Null operands (`in(…, null)`, `ge(null)`, `le(null)` match null and missing; `gt(null)`/`lt(null)` match
nothing; `eq(null)` needs the value to exist) and `empty()` on wildcard feature paths follow MongoDB and are pinned by the
same battery.

The system tests in [ditto-testing](https://github.com/eclipse-ditto/ditto-testing)
(`system/.../search/things/QueryThingsWithFilterBy*IT`) run against both databases; `docker/README-postgres.md` there
describes the PostgreSQL stack.

## Known Issues and Limitations

### Concurrent Modification During Query

**Issue**: Thing modified while query is executing

**Impacts**:
- **Value inconsistency**: Returned Things may have different values than when query started
- **Missing deleted Things**: Things deleted during query are omitted
  - **Note**: Deleted Things cannot be returned from index even if they were in result set
  - Not all fields are in index
  - Authorization issues with deleted Things

**Status**: Accepted limitation of eventually consistent system

## Search Arrays

**Status**: Implemented
- **Basic search**: Supports `like()` operator in arrays
- **Use case**: Search definitions (including definitions without version)

## Monitoring and Debugging

### Metrics

- Search update rate
- Query latency
- Index size
- Background sync progress

### Manual Operations

**Check Thing in search**:
- Query search persistence directly via MongoDB or `psql` (`search_things`, `search_flat`)
- Compare with Things persistence

**Re-index single Thing**:
- Send piggyback command to force re-index
- Useful for debugging inconsistencies

**Re-index namespace**:
- Trigger namespace re-indexing via DevOps API
- Nuclear option for fixing widespread issues

## References

- RQL syntax documentation
- MongoDB index optimization guidelines
- `.claude/concepts/postgresql-persistence-design.md`, section 4, for the PostgreSQL design decisions
- `documentation/src/main/resources/pages/ditto/operating-postgresql.md`, section "Search backend", for operators
