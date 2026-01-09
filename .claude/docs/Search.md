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

### MongoDB Search Database

**Collections**:
- `search` - Main search index documents
- `searchSync` - Background sync progress tracking
- `test` - Health check collection (MongoDB read/write verification)

### Index Document Structure

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
6. **MongoSearchUpdaterFlow**:
   - Write calculated diff to MongoDB
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
3. **Forward to Concierge**: Via DispatcherActor and ConciergeForwarderActor
   - Special handling: QueryThing command not associated with single entity
4. **Forward to SearchActor**: Concierge routes to Search service
5. **RQL translation**: Convert RQL to MongoDB filter syntax
   - Package: `org.eclipse.ditto.thingsearch.service.persistence.read`
   - Classes: `CreateBsonVisitor`, `GetFilterBsonVisitor`
   - **User filter** + **Authorization filter** combined
6. **MongoDB execution**: Run query against search collection
7. **Return Thing IDs**: Search returns list of matching Thing IDs
8. **Retrieve Things**: QueryThingsPerRequestActor fetches Things
   - Via ThingsAggregatorActorProxy → ThingsAggregatorActor
   - Reads from ThingPersistenceActor instances
9. **Filter by permissions**: Concierge enforcement filters Thing JSON
   - Removes fields user cannot read
   - Per-subject permission checking
10. **Aggregate and return**: QueryThingsPerRequestActor sends response to requester

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
- Query search persistence directly via MongoDB
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
