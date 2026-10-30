# Signal Enrichment Deep Dive

## Overview

Signal Enrichment allows adding additional Thing fields to outbound signals (events, messages) that wouldn't normally contain this information.  
This is particularly useful for providing context to subscribers without requiring separate queries.

## Goals

Understand:
- What signal enrichment is
- Which signals can be enriched
- When enrichment happens in signal processing
- How enrichment respects access rights
- Event filtering combined with enrichment
- Caching implementation

## What is Signal Enrichment?

**Definition**: The ability to add fields from a Thing to signals that wouldn't naturally include this information

**User-facing term**: "Extra fields"
- Configuration parameter from user perspective
- Defines which Thing fields should be added to signals

**Use case example**:
- Event: "temperature attribute changed to 25°C"
- Enrichment: Add Thing's location, deviceType, manufacturer
- Result: Subscriber gets context without querying Thing

## Which Signals Can Be Enriched?

### Supported Signal Types

**Events**:
- Thing events (created, modified, deleted)
- Attribute events
- Feature events
- Feature property events

**Messages**:
- Live messages
- Message responses

**Responses**:
- Command responses (in some contexts)

### Configuration

**Documentation**: https://www.eclipse.dev/ditto/basic-enrichment.html

**Configuration locations**:
- **WebSocket subscriptions**: Specify extra fields in subscription request
- **Connection targets**: Configure extra fields in target definition
- **HTTP API**: Query parameter for some endpoints

**Syntax**:
```json
{
  "extraFields": [
    "attributes/location",
    "attributes/manufacturer",
    "features/temperature/properties/unit"
  ]
}
```

## When Does Enrichment Happen?

### Timing in Signal Processing

**Location**: At the "edges" of Ditto/Things
- **Gateway service**: For WebSocket subscriptions, HTTP streaming
- **Connectivity service**: For connection targets

**Process**:
1. Signal (event/message) arrives at edge from internal Pub/Sub
2. Edge identifies subscriber wants enrichment
3. **Just before publishing** to subscriber:
   - Query additional fields from Thing
   - Add fields to signal payload
4. Publish enriched signal to subscriber

### Why Not Enrich Earlier?

Enrichment happens at edges (not in persistence actor) because the persistence actor doesn't know all subscribers and their access rights. Enriching early would require publishing once per subscriber instead of once per edge, increasing traffic. Current approach: publish once to each edge, which enriches per-subscriber based on access rights.

## Implementation Locations

### Gateway Service

**File**: `gateway/service/src/main/java/org/eclipse/ditto/gateway/service/streaming/actors/SessionedSignal.java#L85`

**Context**: WebSocket and SSE streaming

**Flow**:
1. Signal arrives from Pub/Sub
2. SessionedSignal identifies required enrichment
3. Queries Thing for extra fields
4. Appends to signal
5. Streams to client

### Connectivity Service

**File**: `connectivity/service/src/main/java/org/eclipse/ditto/connectivity/service/messaging/OutboundMappingProcessorActor.java#L418`

**Context**: Connection targets

**Flow**:
1. Signal arrives from Pub/Sub
2. OutboundMappingProcessorActor checks target configuration
3. Queries Thing for extra fields
4. Enriches signal
5. Applies payload mapping
6. Publishes to external system

## Authorization and Access Rights

### How is Enrichment Filtered?

**Implementation**: `internal/models/signalenrichment/src/main/java/org/eclipse/ditto/internal/models/signalenrichment/ByRoundTripSignalEnrichmentFacade.java#L65`

**Authorization model**:
- Enrichment query executed **in scope of the subscriber**
- Thing retrieved with subscriber's permissions
- Partial Thing returned based on READ permissions
- **Silent filtering**: Fields user can't access are omitted

**Example**:
```json
{
  "extraFields": [
    "attributes/location",      // User has READ access ✅
    "attributes/secretKey"      // User does NOT have READ access ❌
  ]
}
```

**Result**: Only `attributes/location` included, `secretKey` silently omitted

### Round-Trip Retrieval

**"ByRoundTrip" in class name** indicates:
- Separate query to Things service
- Not from cache or local state
- Ensures authorization enforced
- Latest data retrieved

## Event Filtering with Enrichment

**Documentation**: https://www.eclipse.dev/ditto/basic-enrichment.html#enrich-and-filter

**Capability**: Filter events based on enriched fields (not just event payload)

**Powerful feature**:
```json
{
  "filter": "exists(extra/attributes/criticalAlert)",
  "extraFields": ["attributes/criticalAlert"]
}
```

**Subscribes only to** events where Thing has `criticalAlert` attribute

### Semantic Change with Enrichment

**Important caveat**: `exists()` filter semantics change:

**Without enrichment**:
- `exists(attributes/temperature)` checks if **event contains** temperature
- Checks event payload scope

**With enrichment**:
- `exists(attributes/temperature)` checks if **Thing has** temperature
- Checks Thing scope (larger)

**Problem scenario**:
```json
{
  "filter": "exists(features/temperature/properties/value)",
  "extraFields": ["features/temperature/properties/value"]
}
```

**Intent**: Receive only events that modify temperature value

**Actual behavior**: Receive ALL events for Things that have temperature property
- Property exists in Thing, so `exists()` always true
- Event receives enriched temperature even if event didn't touch it

**Workaround**: Combine Thing-level and event-level filters carefully

## Caching Implementation

**File**: `internal/models/signalenrichment/src/main/java/org/eclipse/ditto/internal/models/signalenrichment/CachingSignalEnrichmentFacade.java`

### Cache Key

**Composite key** consisting of:
1. **Entity ID** (Thing ID)
2. **Selected fields** (extra fields list)
3. **Ditto headers** including **read subjects**

**Why include read subjects?**
- Different subscribers have different permissions
- Same Thing + same fields + different user = different result
- Prevents unauthorized data leakage

### Cache Wrapper

**Architecture**:
- `CachingSignalEnrichmentFacade` wraps actual enrichment facade
- Transparent caching layer
- Cache invalidation on Thing updates (via events)

**Benefits**:
- Reduce queries to Things service
- Improve latency for frequently-enriched Things
- Reduce load on Things persistence actors

## Missing Access Rights Handling

**Behavior**: Silent

**Process**:
- Fields restricted for subscriber are simply not included
- No error thrown
- No indication to subscriber that fields were requested but denied

**Rationale**:
- Avoids information leakage (knowing field exists but can't access it)
- Clean JSON output
- Subscriber should already know their permissions

## Use Cases

### Context-Rich Event Streams

**Scenario**: Backend needs events with device metadata

**Without enrichment**:
1. Receive event
2. Query Thing for metadata
3. Correlate and process

**With enrichment**:
1. Receive event with metadata already included
2. Process immediately

**Benefits**: Reduced latency, fewer queries, simpler code

### Conditional Subscriptions

**Scenario**: Only interested in events from devices in specific region

**Configuration**:
```json
{
  "filter": "eq(extra/attributes/region,'us-east')",
  "extraFields": ["attributes/region"]
}
```

**Result**: Only events from us-east devices delivered

### Dashboard Displays

**Scenario**: Real-time dashboard showing device status

**Configuration**:
```json
{
  "extraFields": [
    "attributes/deviceName",
    "attributes/location",
    "features/battery/properties/level"
  ]
}
```

**Result**: Each event includes display-friendly information

## Performance Considerations

### Round-Trip Cost

**Trade-off**:
- **Pro**: Always current, authorized data
- **Con**: Additional query per enrichment
- **Mitigation**: Caching

### When to Use Enrichment

**Good use cases**:
- Subscriber needs context frequently
- Reducing complexity in subscriber
- Data doesn't change often (benefits from caching)

**Avoid when**:
- Enriching large amounts of data
- Data changes constantly (cache misses)
- Subscriber already has Thing data

## References

- Eclipse Ditto enrichment documentation: https://www.eclipse.dev/ditto/basic-enrichment.html
- Enrich and filter: https://www.eclipse.dev/ditto/basic-enrichment.html#enrich-and-filter
- Gateway SessionedSignal.java implementation
- Connectivity OutboundMappingProcessorActor.java implementation
- ByRoundTripSignalEnrichmentFacade.java authorization
- CachingSignalEnrichmentFacade.java caching
