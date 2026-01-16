# Timeseries Facade Service (ts-facade) Design Document

**Related Issue**: [GitHub #2291 - Provide timeseries facade for Ditto feature properties](https://github.com/eclipse-ditto/ditto/issues/2291)

**Status**: Draft
**Last Updated**: 2026-01-15

---

## 1. Overview

This document describes the architecture and design for a new Ditto service called **ts-facade** (Timeseries Facade). The service acts as a facade to external timeseries databases, providing:

- **Automatic ingestion** of Thing property changes into a timeseries database
- **Query API** for retrieving historical timeseries data
- **Abstraction layer** supporting multiple timeseries database backends

Ditto does **not** store timeseries data in MongoDB. Instead, ts-facade delegates storage to specialized timeseries databases (Apache IoTDB, TimescaleDB, InfluxDB, etc.).

---

## 2. Goals & Non-Goals

### Goals

1. Provide a unified API for timeseries data across different TS database backends
2. Enable declarative configuration of which properties to ingest via WoT ThingModel annotations
3. Integrate with Ditto's policy model for access control (`READ_TS` permission)
4. Support common timeseries query operations (time ranges, aggregations, downsampling)
5. Minimize latency impact on the main Thing update path

### Non-Goals

1. Replacing Ditto's event sourcing in MongoDB (historical revisions remain separate)
2. Supporting arbitrary SQL/query passthrough to TS databases
3. Real-time streaming of timeseries data (use existing SSE/WebSocket for events)
4. Complex analytics or ML pipelines (TS databases can be queried directly for that)

---

## 3. Architecture

### 3.1 High-Level Design

```
                                 Ditto Cluster
┌──────────────────────────────────────────────────────────────────────────┐
│                                                                          │
│  ┌─────────────┐         ┌─────────────┐         ┌─────────────────────┐│
│  │   Gateway   │────────▶│  ts-facade  │◀────────│    Connectivity     ││
│  │   Service   │ Query   │   Service   │  Query  │      Service        ││
│  │             │         │             │         │                     ││
│  │ [TS Routes] │         │ [Adapters]  │         │ [TS Message Handler]││
│  └──────┬──────┘         └──────┬──────┘         └──────────┬──────────┘│
│         │                       │                           │           │
│         │                       │ Pub/Sub                   │           │
│         │                       │ (ts-ingest topic)         │           │
│         │                       │                           │           │
│         │                ┌──────┴──────┐                    │           │
│         │                │   Things    │                    │           │
│         │                │   Service   │                    │           │
│         │                │             │                    │           │
│         │                │[TS Publisher]                    │           │
│         │                └──────┬──────┘                    │           │
│         │                       │                           │           │
│         │    ┌──────────────────┼───────────────────┐       │           │
│         │    │                  │                   │       │           │
│         │    ▼                  ▼                   ▼       │           │
│         │ ┌───────┐      ┌───────────┐      ┌───────────┐   │           │
│         └▶│Policies│◀────│  MongoDB  │      │ ts-facade │◀──┘           │
│           │Service │     │           │      │  Service  │               │
│           └───────┘      └───────────┘      └─────┬─────┘               │
│                                                   │                     │
└───────────────────────────────────────────────────┼─────────────────────┘
                                                    │
                                                    ▼
                                          ┌─────────────────┐
                                          │  TS Database    │
                                          │ (IoTDB, etc.)   │
                                          └─────────────────┘
```

### 3.2 Data Flow

#### Ingestion Flow

```
1. Device sends property update
2. Gateway/Connectivity routes to Things service
3. Things service persists event to MongoDB
4. Things service checks WoT model for TS-enabled properties
5. If TS-enabled: publish to "ts-ingest" pub/sub topic
6. ts-facade receives message, writes to TS database
```

#### Query Flow

```
1. Client sends TS query to Gateway (or via Connectivity message)
2. Edge service routes directly to ts-facade (no Things service hop)
3. ts-facade loads/caches Policy from Policies service
4. ts-facade checks READ_TS permission for requested properties
5. ts-facade queries TS database
6. ts-facade formats and returns response
```

### 3.3 Service Responsibilities

| Service | Responsibility |
|---------|----------------|
| **Things** | Publish TS-enabled property changes to `ts-ingest` topic |
| **ts-facade** | Ingest data, execute queries, manage TS adapters, enforce READ_TS |
| **Gateway** | HTTP routes for TS queries, forward to ts-facade |
| **Connectivity** | Handle TS query messages, forward to ts-facade |
| **Policies** | Provide policy data (ts-facade caches enforcers) |

---

## 4. WoT ThingModel Extension

### 4.1 Ditto WoT Extension Ontology

Extend the existing `ditto:` ontology (IRI: `https://ditto.eclipseprojects.io/wot/ditto-extension#`) with timeseries declarations:

| Property | Type | Description | Default |
|----------|------|-------------|---------|
| `ditto:ts-enabled` | boolean | Enable TS ingestion for this property | `false` |
| `ditto:ts-retention` | string (ISO 8601 duration) | How long to retain data | Service default |
| `ditto:ts-resolution` | string (ISO 8601 duration) | Minimum sampling interval | No limit |
| `ditto:ts-tags` | object | Tags/dimensions for grouping (see below) | `{}` |

### 4.2 Tag Declaration with Placeholders

The `ditto:ts-tags` object defines dimensions that are stored with each data point in the TS database, enabling efficient cross-thing grouping and filtering **without querying Ditto**.

**Tag values support two formats:**

| Format | Example | Description |
|--------|---------|-------------|
| **Placeholder** | `"{{ thing-json:attributes/building }}"` | Resolved dynamically from Thing JSON |
| **Constant** | `"production"` | Static value for all data points |

**Placeholder syntax**: `{{ thing-json:<json-pointer> }}`
- Uses Ditto's existing placeholder mechanism
- `<json-pointer>` is a JSON pointer into the Thing's JSON structure
- Resolved at ingestion time from the current Thing state

**Tag key naming convention:**

To ensure consistency with Ditto search RQL syntax, tag keys follow these rules:

| Tag Type | Key Format | Example |
|----------|------------|---------|
| **Placeholder-resolved** | Full Thing JSON path | `"attributes/building"`, `"features/env/properties/type"` |
| **Static/constant** | Custom name (restricted) | `"environment"`, `"region"` |

**Example tag declarations:**
```json
"ditto:ts-tags": {
  "attributes/building": "{{ thing-json:attributes/building }}",
  "attributes/floor": "{{ thing-json:attributes/floor }}",
  "features/environment/properties/type": "{{ thing-json:features/environment/properties/type }}",
  "environment": "production",
  "region": "eu-west"
}
```

**Static tag name restrictions:**

Static tag names (constant values) must **NOT** use these reserved prefixes to avoid confusion with Thing structure:

| Reserved Prefix | Reason |
|-----------------|--------|
| `attributes` or `attributes/` | Conflicts with Thing attributes path |
| `features` or `features/` | Conflicts with Thing features path |
| `definition` | Reserved Thing field |
| `policyId` | Reserved Thing field |
| `thingId` | Reserved Thing field |
| `_` (underscore prefix) | Reserved for internal use |

```json
// ✅ Valid static tags
"ditto:ts-tags": {
  "environment": "production",
  "region": "eu-west",
  "deploymentId": "dep-123"
}

// ❌ Invalid static tags (will be rejected)
"ditto:ts-tags": {
  "attributes": "something",        // Reserved prefix
  "features/custom": "value",       // Looks like Thing path
  "thingId": "override",            // Reserved field
  "_internal": "value"              // Reserved prefix
}
```

**Important considerations:**
- Tags are resolved and stored at **ingestion time** (point-in-time snapshot)
- If a Thing's attributes change, historical data retains the old tag values
- Tags are indexed in the TS database for efficient filtering and grouping
- Keep tag cardinality reasonable (avoid high-cardinality values like timestamps)
- RQL filters use the same paths: `eq(attributes/building,'A')` matches tag key `attributes/building`

### 4.3 Example ThingModel

```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    {"ditto": "https://ditto.eclipseprojects.io/wot/ditto-extension#"}
  ],
  "@type": "tm:ThingModel",
  "title": "Environmental Sensor",
  "properties": {
    "temperature": {
      "type": "number",
      "unit": "cel",
      "ditto:category": "status",
      "ditto:ts-enabled": true,
      "ditto:ts-retention": "P90D",
      "ditto:ts-resolution": "PT1S",
      "ditto:ts-tags": {
        "attributes/building": "{{ thing-json:attributes/building }}",
        "attributes/floor": "{{ thing-json:attributes/floor }}",
        "sensorType": "environmental"
      }
    },
    "humidity": {
      "type": "number",
      "unit": "percent",
      "ditto:category": "status",
      "ditto:ts-enabled": true,
      "ditto:ts-retention": "P30D",
      "ditto:ts-tags": {
        "attributes/building": "{{ thing-json:attributes/building }}",
        "attributes/floor": "{{ thing-json:attributes/floor }}",
        "sensorType": "environmental"
      }
    },
    "serialNumber": {
      "type": "string",
      "ditto:category": "configuration",
      "ditto:ts-enabled": false
    }
  }
}
```

### 4.4 Runtime Behavior

When a Thing is created/modified with a WoT ThingModel reference (`definition`):

1. Things service resolves the ThingModel
2. Things service extracts `ditto:ts-enabled` properties and their `ditto:ts-tags`
3. Things service caches the TS configuration per Thing
4. On property changes:
   - Things service checks cache for TS-enabled properties
   - Resolves `ditto:ts-tags` placeholders against current Thing JSON
   - Publishes data point with resolved tags to `ts-ingest` topic

---

## 5. Pub/Sub Integration

### 5.1 Topic Structure

**Topic**: `ditto.ts-ingest`

**Message Format** (TimeseriesDataPoint):

```json
{
  "thingId": "org.eclipse.ditto:sensor-1",
  "featureId": "environment",
  "propertyPath": "/temperature",
  "timestamp": "2026-01-15T10:30:00.000Z",
  "value": 23.5,
  "revision": 42,
  "tags": {
    "attributes/building": "A",
    "attributes/floor": "2",
    "sensorType": "environmental"
  },
  "retention": "P90D"
}
```

**Note**: The `tags` object contains **resolved values** with full Thing paths as keys. For the Thing:
```json
{
  "thingId": "org.eclipse.ditto:sensor-1",
  "attributes": {
    "building": "A",
    "floor": "2"
  },
  ...
}
```

With WoT declaration:
```json
"ditto:ts-tags": {
  "attributes/building": "{{ thing-json:attributes/building }}",
  "attributes/floor": "{{ thing-json:attributes/floor }}",
  "sensorType": "environmental"
}
```

The resolved tags become: `{"attributes/building": "A", "attributes/floor": "2", "sensorType": "environmental"}`

This naming convention ensures RQL filters like `eq(attributes/building,'A')` directly match tag keys.

### 5.2 Publishing Logic in Things Service

```java
// In ThingPersistenceActor or dedicated publisher
private void publishTimeseriesDataIfEnabled(
        final ThingEvent<?> event,
        final Thing thing) {

    final Optional<ThingDefinition> definition = thing.getDefinition();
    if (definition.isEmpty()) {
        return; // No WoT model, no TS declarations
    }

    // Get TS-enabled paths from cached WoT model analysis
    final Set<JsonPointer> tsEnabledPaths =
        timeseriesConfigCache.getTsEnabledPaths(thing.getEntityId());

    // Extract changed property paths from event
    final Set<JsonPointer> changedPaths = extractChangedPaths(event);

    // Publish for each TS-enabled changed path
    changedPaths.stream()
        .filter(tsEnabledPaths::contains)
        .forEach(path -> {
            final TimeseriesDataPoint dataPoint = TimeseriesDataPoint.of(
                thing.getEntityId(),
                extractFeatureId(path),
                extractPropertyPath(path),
                event.getTimestamp().orElseGet(Instant::now),
                extractValue(thing, path),
                event.getRevision(),
                buildTags(thing, path)
            );
            timeseriesPublisher.publish(dataPoint);
        });
}
```

### 5.3 Subscriber in ts-facade

```java
// In TimeseriesFacadeRootActor
private void subscribeToTimeseriesIngest() {
    final DistributedSub distributedSub = DistributedSub.of(
        actorSystem,
        "ts-ingest",
        this::handleTimeseriesDataPoint
    );
    distributedSub.subscribeWithAck(Set.of("ts-ingest"), getSelf());
}

private void handleTimeseriesDataPoint(final TimeseriesDataPoint dataPoint) {
    // Batch writes for efficiency
    writeBuffer.add(dataPoint);
    if (writeBuffer.size() >= batchSize || timeSinceLastFlush() > flushInterval) {
        flushToAdapter();
    }
}
```

---

## 6. Policy Integration

### 6.1 New Permission: READ_TS

Extend the existing permission model with `READ_TS`:

| Permission | Description |
|------------|-------------|
| `READ` | Read current Thing state |
| `WRITE` | Modify Thing state |
| `READ_TS` | Read timeseries data for property |

### 6.2 Policy Example

```json
{
  "policyId": "org.eclipse.ditto:sensor-policy",
  "entries": {
    "owner": {
      "subjects": {
        "integration:owner-app": {"type": "generated"}
      },
      "resources": {
        "thing:/": {
          "grant": ["READ", "WRITE", "READ_TS"],
          "revoke": []
        }
      }
    },
    "viewer": {
      "subjects": {
        "integration:dashboard": {"type": "generated"}
      },
      "resources": {
        "thing:/features/environment/properties/temperature": {
          "grant": ["READ", "READ_TS"],
          "revoke": []
        },
        "thing:/features/environment/properties/humidity": {
          "grant": ["READ"],
          "revoke": []
        }
      }
    },
    "live-only": {
      "subjects": {
        "integration:realtime-monitor": {"type": "generated"}
      },
      "resources": {
        "thing:/features/environment/properties/temperature": {
          "grant": ["READ"],
          "revoke": ["READ_TS"]
        }
      }
    }
  }
}
```

**Semantics**:
- `owner`: Full access including timeseries for all properties
- `viewer`: Can read current temperature + its timeseries, but only current humidity (no TS)
- `live-only`: Can read current temperature but explicitly denied timeseries access

### 6.3 Enforcement in ts-facade

```java
public CompletionStage<TimeseriesResult> executeQuery(
        final TimeseriesQuery query,
        final DittoHeaders dittoHeaders) {

    // 1. Load policy enforcer (cached)
    return policyEnforcerProvider.getPolicyEnforcer(query.getThingId())
        .thenCompose(enforcer -> {
            // 2. Check READ_TS permission for each requested property
            final AuthorizationContext authContext =
                dittoHeaders.getAuthorizationContext();

            final List<JsonPointer> authorizedPaths = query.getPropertyPaths().stream()
                .filter(path -> enforcer.hasPermission(
                    ResourceKey.newInstance("thing", toResourcePath(query, path)),
                    authContext,
                    Permission.READ_TS))
                .toList();

            if (authorizedPaths.isEmpty()) {
                throw TimeseriesNotAccessibleException.newBuilder(query.getThingId())
                    .dittoHeaders(dittoHeaders)
                    .build();
            }

            // 3. Execute query for authorized paths only
            final TimeseriesQuery filteredQuery = query.withPropertyPaths(authorizedPaths);
            return timeseriesAdapter.query(filteredQuery);
        });
}
```

### 6.4 Policy Caching

ts-facade caches policy enforcers similar to Things service:

```java
public class TimeseriesPolicyEnforcerProvider {

    private final Cache<PolicyId, PolicyEnforcer> enforcerCache;
    private final ActorRef policiesShardRegion;

    public CompletionStage<PolicyEnforcer> getPolicyEnforcer(final ThingId thingId) {
        // 1. Get policyId for thing (cached mapping)
        return getPolicyIdForThing(thingId)
            .thenCompose(policyId -> {
                // 2. Check cache
                final PolicyEnforcer cached = enforcerCache.getIfPresent(policyId);
                if (cached != null) {
                    return CompletableFuture.completedFuture(cached);
                }

                // 3. Load from Policies service
                return loadPolicyEnforcer(policyId);
            });
    }

    // Subscribe to policy change events for cache invalidation
    private void onPolicyModified(final PolicyModified event) {
        enforcerCache.invalidate(event.getEntityId());
    }
}
```

---

## 7. HTTP API Design

### 7.1 Design Rationale

Ditto's HTTP API follows a strict convention: **API paths directly map to the JSON structure** of resources. For example:
- `GET /api/2/things/{thingId}` returns the complete Thing JSON
- `GET /api/2/things/{thingId}/features/{featureId}/properties/temperature` returns the value at `features.{featureId}.properties.temperature`

Appending `/timeseries` to property paths (e.g., `.../properties/temperature/timeseries`) would incorrectly imply a `timeseries` field exists in the JSON structure. It would also create collisions if a property is actually named `timeseries`.

Therefore, timeseries uses a **separate API root** (`/api/2/timeseries/...`), following the precedent of `/api/2/search/things` which also provides a different view of Things data.

### 7.2 Endpoints

#### Single Property Timeseries

```
GET /api/2/timeseries/things/{thingId}/features/{featureId}/properties/{propertyPath}
```

#### Feature Timeseries (Multiple Properties)

```
GET /api/2/timeseries/things/{thingId}/features/{featureId}/properties
```

#### Thing Attribute Timeseries

```
GET /api/2/timeseries/things/{thingId}/attributes/{attributePath}
```

#### Batch Query (Multiple Properties/Aggregations)

```
POST /api/2/timeseries/things/{thingId}/features/{featureId}/query
```

#### Full Endpoint Overview

| Endpoint | Description |
|----------|-------------|
| `GET /api/2/timeseries/things/{thingId}` | All TS data for a Thing |
| `GET /api/2/timeseries/things/{thingId}/features/{featureId}` | All TS data for a Feature |
| `GET /api/2/timeseries/things/{thingId}/features/{featureId}/properties` | All TS-enabled properties |
| `GET /api/2/timeseries/things/{thingId}/features/{featureId}/properties/{path}` | Single property TS |
| `GET /api/2/timeseries/things/{thingId}/attributes/{path}` | Attribute TS (if supported) |
| `POST /api/2/timeseries/things/{thingId}/features/{featureId}/query` | Batch query |
| `POST /api/2/timeseries/query` | **Cross-thing aggregation query** |

#### Cross-Thing Aggregation Query

```
POST /api/2/timeseries/query
```

This endpoint enables fleet-level analytics by querying **directly on the TS database** using tags that were denormalized at ingestion time. No Ditto search is involved, making it highly efficient.

**Filter uses RQL syntax** - the same query language used in Ditto search (`/api/2/search/things`). This ensures consistency and allows users to leverage existing RQL knowledge.

**Request Body**:
```json
{
  "filter": "and(eq(attributes/building,'A'),eq(sensorType,'environmental'))",
  "feature": "environment",
  "properties": ["temperature", "humidity"],
  "from": "now-24h",
  "to": "now",
  "step": "1h",
  "agg": "avg",
  "groupBy": ["attributes/floor"]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `filter` | string (RQL) | No | RQL filter expression on declared tags |
| `feature` | string | Yes | Feature ID to query |
| `properties` | array | Yes | Property names to query |
| `from` | string | No | Start time (ISO 8601 or relative) |
| `to` | string | No | End time (default: `now`) |
| `step` | string | No | Downsampling interval |
| `agg` | string | No | Aggregation function |
| `groupBy` | array | No | Tag keys to group by (use full paths) |

**Supported RQL operators**:

| Operator | Example | Description |
|----------|---------|-------------|
| `eq` | `eq(attributes/building,'A')` | Equals |
| `ne` | `ne(attributes/floor,'0')` | Not equals |
| `gt` | `gt(attributes/floor,2)` | Greater than |
| `ge` | `ge(attributes/floor,2)` | Greater than or equal |
| `lt` | `lt(attributes/floor,10)` | Less than |
| `le` | `le(attributes/floor,10)` | Less than or equal |
| `in` | `in(attributes/floor,'1','2','3')` | In list |
| `like` | `like(attributes/building,'Building-*')` | Pattern match (`*` = any chars) |
| `and` | `and(expr1,expr2,...)` | Logical AND |
| `or` | `or(expr1,expr2)` | Logical OR |
| `not` | `not(eq(sensorType,'test'))` | Logical NOT |

**Response**:
```json
{
  "query": {
    "filter": "and(eq(attributes/building,'A'),eq(sensorType,'environmental'))",
    "feature": "environment",
    "properties": ["temperature"],
    "from": "2026-01-14T10:00:00Z",
    "to": "2026-01-15T10:00:00Z",
    "step": "1h",
    "aggregation": "avg",
    "groupBy": ["attributes/floor"]
  },
  "groups": [
    {
      "tags": {"attributes/floor": "1"},
      "thingCount": 5,
      "series": {
        "temperature": {
          "unit": "cel",
          "data": [
            {"t": "2026-01-14T10:00:00Z", "v": 22.3},
            {"t": "2026-01-14T11:00:00Z", "v": 22.1}
          ]
        }
      }
    },
    {
      "tags": {"attributes/floor": "2"},
      "thingCount": 3,
      "series": {
        "temperature": {
          "unit": "cel",
          "data": [
            {"t": "2026-01-14T10:00:00Z", "v": 23.1},
            {"t": "2026-01-14T11:00:00Z", "v": 23.4}
          ]
        }
      }
    }
  ]
}
```

**Key points**:
- Filter uses **RQL syntax** - same as Ditto search
- Filters only work on **declared tags** (from `ditto:ts-tags` in WoT model)
- Use full Thing paths in RQL: `attributes/building`, not just `building`
- RQL is translated to TS-database-specific query syntax by the adapter
- Query goes directly to TS database - no Ditto search round-trip
- `thingCount` shows how many Things contributed to each group
- Authorization: User must have `READ_TS` permission on the queried property

**RQL Filter Translation**:

The ts-facade service translates RQL to TS-database-specific queries:

| RQL | IoTDB SQL | TimescaleDB SQL | InfluxDB Flux |
|-----|-----------|-----------------|---------------|
| `eq(attributes/building,'A')` | `attributes_building = 'A'` | `"attributes/building" = 'A'` | `r["attributes/building"] == "A"` |
| `gt(attributes/floor,2)` | `attributes_floor > 2` | `"attributes/floor" > 2` | `r["attributes/floor"] > 2` |
| `like(attributes/building,'A*')` | `attributes_building LIKE 'A%'` | `"attributes/building" LIKE 'A%'` | `r["attributes/building"] =~ /^A.*/` |
| `and(eq(a,'x'),gt(b,5))` | `a = 'x' AND b > 5` | `a = 'x' AND b > 5` | Combined filters |

**Example use cases**:

| Use Case | RQL Filter | GroupBy |
|----------|------------|---------|
| Avg temp per floor in Building A | `eq(attributes/building,'A')` | `["attributes/floor"]` |
| Compare buildings | *(empty)* | `["attributes/building"]` |
| Floors 2+ in Building A | `and(eq(attributes/building,'A'),ge(attributes/floor,2))` | `["attributes/floor"]` |
| Buildings A or B | `or(eq(attributes/building,'A'),eq(attributes/building,'B'))` | `["attributes/building"]` |
| Exclude test sensors | `not(eq(sensorType,'test'))` | `["attributes/building"]` |
| Pattern match | `like(attributes/building,'Building-*')` | `["attributes/building"]` |
| Multiple floors | `and(eq(attributes/building,'A'),in(attributes/floor,'1','2','3'))` | `["attributes/floor"]` |

### 7.3 Query Parameters

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `from` | string | No | Start time (ISO 8601 or relative) | `2026-01-14T00:00:00Z`, `now-24h` |
| `to` | string | No | End time (default: `now`) | `2026-01-15T00:00:00Z`, `now` |
| `step` | string | No | Downsampling interval | `1m`, `5m`, `1h`, `1d` |
| `agg` | string | No | Aggregation function | `avg`, `min`, `max`, `sum`, `count`, `first`, `last` |
| `fill` | string | No | Gap filling strategy | `null`, `previous`, `linear`, `zero` |
| `limit` | int | No | Max data points (raw queries) | `1000` |
| `tz` | string | No | Timezone for step alignment | `Europe/Berlin`, `UTC` |
| `properties` | string | No | Comma-separated property filter | `temperature,humidity` |

### 7.4 Response Format

#### Single Property Response

```json
{
  "thingId": "org.eclipse.ditto:sensor-1",
  "featureId": "environment",
  "property": "properties/temperature",
  "query": {
    "from": "2026-01-14T00:00:00Z",
    "to": "2026-01-15T00:00:00Z",
    "step": "1h",
    "aggregation": "avg"
  },
  "result": {
    "count": 24,
    "unit": "cel",
    "dataType": "number"
  },
  "data": [
    {"t": "2026-01-14T00:00:00Z", "v": 22.3},
    {"t": "2026-01-14T01:00:00Z", "v": 22.1},
    {"t": "2026-01-14T02:00:00Z", "v": null, "_gap": true},
    {"t": "2026-01-14T03:00:00Z", "v": 21.8}
  ]
}
```

#### Multiple Properties Response

```json
{
  "thingId": "org.eclipse.ditto:sensor-1",
  "featureId": "environment",
  "query": {
    "from": "2026-01-14T00:00:00Z",
    "to": "2026-01-15T00:00:00Z",
    "step": "1h",
    "aggregation": "avg"
  },
  "series": [
    {
      "property": "properties/temperature",
      "result": {
        "count": 24,
        "unit": "cel",
        "dataType": "number"
      },
      "data": [
        {"t": "2026-01-14T00:00:00Z", "v": 22.3},
        {"t": "2026-01-14T01:00:00Z", "v": 22.1}
      ]
    },
    {
      "property": "properties/humidity",
      "result": {
        "count": 24,
        "unit": "percent",
        "dataType": "number"
      },
      "data": [
        {"t": "2026-01-14T00:00:00Z", "v": 65.2},
        {"t": "2026-01-14T01:00:00Z", "v": 64.8}
      ]
    }
  ]
}
```

#### Batch Query Request/Response

**Request**:
```json
POST /api/2/timeseries/things/org.eclipse.ditto:sensor-1/features/environment/query
{
  "properties": ["temperature", "humidity"],
  "from": "now-24h",
  "to": "now",
  "step": "1h",
  "aggregations": ["avg", "max"]
}
```

**Response**:
```json
{
  "thingId": "org.eclipse.ditto:sensor-1",
  "featureId": "environment",
  "query": {
    "from": "2026-01-14T10:30:00Z",
    "to": "2026-01-15T10:30:00Z",
    "step": "1h"
  },
  "results": [
    {
      "property": "properties/temperature",
      "aggregation": "avg",
      "result": {"count": 24, "unit": "cel", "dataType": "number"},
      "data": [{"t": "2026-01-14T11:00:00Z", "v": 22.3}, ...]
    },
    {
      "property": "properties/temperature",
      "aggregation": "max",
      "result": {"count": 24, "unit": "cel", "dataType": "number"},
      "data": [{"t": "2026-01-14T11:00:00Z", "v": 24.1}, ...]
    },
    {
      "property": "properties/humidity",
      "aggregation": "avg",
      "result": {"count": 24, "unit": "percent", "dataType": "number"},
      "data": [{"t": "2026-01-14T11:00:00Z", "v": 65.2}, ...]
    },
    {
      "property": "properties/humidity",
      "aggregation": "max",
      "result": {"count": 24, "unit": "percent", "dataType": "number"},
      "data": [{"t": "2026-01-14T11:00:00Z", "v": 72.0}, ...]
    }
  ]
}
```

### 7.5 Error Responses

| Status | Error Code | Description |
|--------|-----------|-------------|
| 400 | `timeseries:query.invalid` | Invalid query parameters |
| 403 | `timeseries:timeseries.notallowed` | No READ_TS permission |
| 404 | `timeseries:thing.notfound` | Thing does not exist |
| 404 | `timeseries:data.notfound` | No data in requested range |
| 503 | `timeseries:backend.unavailable` | TS database unavailable |

---

## 8. TS Database Abstraction

### 8.1 Adapter Interface

```java
/**
 * Abstraction for timeseries database backends.
 */
public interface TimeseriesAdapter {

    // --- Lifecycle ---

    CompletionStage<Void> initialize(TimeseriesAdapterConfig config);
    CompletionStage<Void> shutdown();
    HealthStatus getHealth();

    // --- Schema Management ---

    /**
     * Ensure the schema exists for a Thing's TS-enabled properties.
     * Called when a Thing with TS declarations is created/modified.
     */
    CompletionStage<Void> ensureSchema(
        ThingId thingId,
        Map<JsonPointer, TimeseriesPropertySchema> properties);

    // --- Ingestion ---

    CompletionStage<Void> write(TimeseriesDataPoint dataPoint);

    CompletionStage<Void> writeBatch(List<TimeseriesDataPoint> dataPoints);

    // --- Query ---

    /** Query timeseries data for a single Thing. */
    CompletionStage<TimeseriesQueryResult> query(TimeseriesQuery query);

    /** Cross-thing aggregation query using tags. */
    CompletionStage<TimeseriesAggregationResult> queryAggregation(TimeseriesAggregationQuery query);

    // --- Management ---

    CompletionStage<Void> deleteData(ThingId thingId, Instant before);

    CompletionStage<Void> applyRetention(Duration retention);

    CompletionStage<TimeseriesStats> getStatistics(ThingId thingId);
}
```

### 8.2 Data Models

```java
public record TimeseriesDataPoint(
    ThingId thingId,
    @Nullable FeatureId featureId,
    JsonPointer propertyPath,
    Instant timestamp,
    JsonValue value,
    long revision,
    Map<String, String> tags
) {}

/** Single-thing query. */
public record TimeseriesQuery(
    ThingId thingId,
    @Nullable FeatureId featureId,
    List<JsonPointer> propertyPaths,
    Instant from,
    Instant to,
    @Nullable Duration step,
    @Nullable Aggregation aggregation,
    @Nullable FillStrategy fillStrategy,
    @Nullable Integer limit,
    @Nullable ZoneId timezone
) {}

/** Cross-thing aggregation query using tags with RQL filter. */
public record TimeseriesAggregationQuery(
    @Nullable RqlFilter filter,         // RQL filter on declared tags
    FeatureId featureId,
    List<JsonPointer> propertyPaths,
    Instant from,
    Instant to,
    @Nullable Duration step,
    Aggregation aggregation,            // Required for aggregation
    List<String> groupByTags,           // Tag keys to group by (full paths)
    @Nullable ZoneId timezone
) {}

/**
 * RQL filter translated from query string.
 * Reuses Ditto's existing RQL parser infrastructure.
 */
public interface RqlFilter {
    /** Get all tag keys referenced in the filter. */
    Set<String> getReferencedFields();

    /** Accept a translator visitor to generate DB-specific query. */
    <T> T accept(RqlFilterTranslator<T> translator);
}

/**
 * Translates RQL filter to TS-database-specific query syntax.
 */
public interface RqlFilterTranslator<T> {
    T translateEq(String field, Object value);
    T translateNe(String field, Object value);
    T translateGt(String field, Object value);
    T translateGe(String field, Object value);
    T translateLt(String field, Object value);
    T translateLe(String field, Object value);
    T translateIn(String field, List<Object> values);
    T translateLike(String field, String pattern);
    T translateAnd(List<T> expressions);
    T translateOr(List<T> expressions);
    T translateNot(T expression);
}

public enum Aggregation {
    AVG, MIN, MAX, SUM, COUNT, FIRST, LAST
}

public enum FillStrategy {
    NULL, PREVIOUS, LINEAR, ZERO
}

/** Result of cross-thing aggregation query. */
public record TimeseriesAggregationResult(
    TimeseriesAggregationQuery query,
    List<TimeseriesAggregationGroup> groups
) {}

public record TimeseriesAggregationGroup(
    Map<String, String> tags,           // Group key (tag values)
    int thingCount,                     // Number of Things in group
    Map<JsonPointer, TimeseriesSeries> series  // Property -> data
) {}

public record TimeseriesSeries(
    String unit,
    List<TimeseriesDataValue> data
) {}

public record TimeseriesQueryResult(
    ThingId thingId,
    @Nullable FeatureId featureId,
    JsonPointer propertyPath,
    TimeseriesQuery query,
    TimeseriesResultMeta meta,
    List<TimeseriesDataValue> data
) {}

public record TimeseriesResultMeta(
    int count,
    @Nullable String unit,
    String dataType
) {}

public record TimeseriesDataValue(
    Instant timestamp,
    @Nullable JsonValue value,
    boolean isGap
) {}
```

### 8.3 Planned Adapter Implementations

| Database | Module | Status | Notes |
|----------|--------|--------|-------|
| Apache IoTDB | `ts-facade-iotdb` | Planned | Native Java client, tree-based schema |
| TimescaleDB | `ts-facade-timescale` | Planned | JDBC, PostgreSQL extension |
| InfluxDB 2.x | `ts-facade-influx` | Future | HTTP API, Flux queries |
| QuestDB | `ts-facade-questdb` | Future | PostgreSQL wire protocol |

### 8.4 Schema Mapping

**Ditto Concept → TS Database Concept**:

| Ditto | IoTDB | TimescaleDB |
|-------|-------|-------------|
| Namespace | Storage Group | Schema |
| ThingId | Device | Table prefix |
| FeatureId | Entity (level 1) | Table suffix |
| PropertyPath | Measurement | Column |
| Value | Data point | Row value |
| Tags | Tags/Attributes | Additional columns |

**IoTDB Schema Example**:
```
root.{namespace}.{thingName}.{featureId}.{propertyPath}

root.org_eclipse_ditto.sensor_1.environment.temperature
root.org_eclipse_ditto.sensor_1.environment.humidity
```

**TimescaleDB Schema Example**:
```sql
CREATE TABLE ts_org_eclipse_ditto_sensor_1_environment (
    time TIMESTAMPTZ NOT NULL,
    temperature DOUBLE PRECISION,
    humidity DOUBLE PRECISION,
    revision BIGINT,
    PRIMARY KEY (time)
);

SELECT create_hypertable('ts_org_eclipse_ditto_sensor_1_environment', 'time');
```

---

## 9. Configuration

### 9.1 ts-facade Service Configuration

**File**: `ts-facade/service/src/main/resources/ts-facade.conf`

```hocon
ditto {
  ts-facade {
    # Timeseries adapter configuration
    adapter {
      # Which adapter to use: "iotdb", "timescale", "influx"
      type = "iotdb"
      type = ${?TS_FACADE_ADAPTER_TYPE}

      # Connection settings (adapter-specific)
      iotdb {
        host = "localhost"
        host = ${?TS_FACADE_IOTDB_HOST}
        port = 6667
        port = ${?TS_FACADE_IOTDB_PORT}
        username = "root"
        username = ${?TS_FACADE_IOTDB_USERNAME}
        password = "root"
        password = ${?TS_FACADE_IOTDB_PASSWORD}

        # Storage group prefix
        storage-group-prefix = "root.ditto"
        storage-group-prefix = ${?TS_FACADE_IOTDB_STORAGE_GROUP_PREFIX}
      }

      timescale {
        jdbc-url = "jdbc:postgresql://localhost:5432/ditto_ts"
        jdbc-url = ${?TS_FACADE_TIMESCALE_JDBC_URL}
        username = "ditto"
        username = ${?TS_FACADE_TIMESCALE_USERNAME}
        password = "ditto"
        password = ${?TS_FACADE_TIMESCALE_PASSWORD}

        # Connection pool
        pool-size = 10
        pool-size = ${?TS_FACADE_TIMESCALE_POOL_SIZE}
      }
    }

    # Ingestion settings
    ingestion {
      # Batch size for writes
      batch-size = 100
      batch-size = ${?TS_FACADE_INGESTION_BATCH_SIZE}

      # Max time to buffer before flush
      flush-interval = 5s
      flush-interval = ${?TS_FACADE_INGESTION_FLUSH_INTERVAL}

      # Parallelism for write operations
      parallelism = 4
      parallelism = ${?TS_FACADE_INGESTION_PARALLELISM}
    }

    # Query settings
    query {
      # Default time range if 'from' not specified
      default-lookback = 24h
      default-lookback = ${?TS_FACADE_QUERY_DEFAULT_LOOKBACK}

      # Maximum time range for a single query
      max-time-range = 365d
      max-time-range = ${?TS_FACADE_QUERY_MAX_TIME_RANGE}

      # Maximum data points per query
      max-data-points = 10000
      max-data-points = ${?TS_FACADE_QUERY_MAX_DATA_POINTS}

      # Query timeout
      timeout = 30s
      timeout = ${?TS_FACADE_QUERY_TIMEOUT}
    }

    # Policy enforcer cache
    policy-cache {
      maximum-size = 10000
      maximum-size = ${?TS_FACADE_POLICY_CACHE_MAX_SIZE}

      expire-after-write = 5m
      expire-after-write = ${?TS_FACADE_POLICY_CACHE_EXPIRE}
    }

    # Default retention (can be overridden per property in WoT model)
    default-retention = P90D
    default-retention = ${?TS_FACADE_DEFAULT_RETENTION}
  }
}
```

### 9.2 Things Service Configuration Addition

**File**: `things/service/src/main/resources/things.conf` (additions)

```hocon
ditto {
  things {
    # Timeseries publishing configuration
    timeseries {
      # Enable/disable TS publishing
      enabled = true
      enabled = ${?THINGS_TIMESERIES_ENABLED}

      # Pub/Sub topic for TS data points
      topic = "ts-ingest"
      topic = ${?THINGS_TIMESERIES_TOPIC}

      # Cache TTL for WoT model TS declarations
      wot-cache-ttl = 5m
      wot-cache-ttl = ${?THINGS_TIMESERIES_WOT_CACHE_TTL}
    }
  }
}
```

### 9.3 Helm Configuration

**File**: `deployment/helm/ditto/values.yaml` (additions)

```yaml
tsFacade:
  enabled: true
  replicaCount: 1

  image:
    repository: eclipse/ditto-ts-facade
    tag: ""  # defaults to chart appVersion
    pullPolicy: IfNotPresent

  resources:
    requests:
      cpu: 250m
      memory: 512Mi
    limits:
      cpu: 1000m
      memory: 1024Mi

  config:
    adapter:
      type: "iotdb"  # or "timescale"

    iotdb:
      host: "iotdb"
      port: 6667
      # username/password via secrets

    timescale:
      jdbcUrl: "jdbc:postgresql://timescaledb:5432/ditto_ts"
      poolSize: 10
      # username/password via secrets

    ingestion:
      batchSize: 100
      flushInterval: "5s"

    query:
      defaultLookback: "24h"
      maxTimeRange: "365d"
      maxDataPoints: 10000

    defaultRetention: "P90D"

# Add to things service config
things:
  config:
    timeseries:
      enabled: true
```

---

## 10. Module Structure

```
ditto/
├── ts-facade/
│   ├── model/                    # TS-specific signals, models (Java 8)
│   │   └── src/main/java/org/eclipse/ditto/tsfacade/model/
│   │       ├── signals/
│   │       │   ├── commands/     # RetrieveTimeseries, etc.
│   │       │   └── events/       # TimeseriesDataIngested, etc.
│   │       ├── TimeseriesDataPoint.java
│   │       ├── TimeseriesQuery.java
│   │       └── TimeseriesQueryResult.java
│   │
│   ├── api/                      # Adapter interfaces (Java 8)
│   │   └── src/main/java/org/eclipse/ditto/tsfacade/api/
│   │       ├── TimeseriesAdapter.java
│   │       ├── TimeseriesAdapterConfig.java
│   │       └── TimeseriesAdapterFactory.java
│   │
│   ├── service/                  # Service implementation (Java 21)
│   │   └── src/main/java/org/eclipse/ditto/tsfacade/service/
│   │       ├── starter/
│   │       │   └── TimeseriesFacadeService.java
│   │       ├── actors/
│   │       │   ├── TimeseriesFacadeRootActor.java
│   │       │   ├── TimeseriesQueryActor.java
│   │       │   └── TimeseriesIngestionActor.java
│   │       ├── enforcement/
│   │       │   └── TimeseriesPolicyEnforcerProvider.java
│   │       └── routes/
│   │           └── TimeseriesRoute.java
│   │
│   ├── iotdb/                    # IoTDB adapter (Java 21)
│   │   └── src/main/java/org/eclipse/ditto/tsfacade/iotdb/
│   │       └── IoTDBTimeseriesAdapter.java
│   │
│   └── timescale/                # TimescaleDB adapter (Java 21)
│       └── src/main/java/org/eclipse/ditto/tsfacade/timescale/
│           └── TimescaleTimeseriesAdapter.java
│
├── things/
│   └── service/
│       └── src/main/java/org/eclipse/ditto/things/service/
│           └── timeseries/
│               ├── TimeseriesPublisher.java
│               └── TimeseriesWotConfigCache.java
│
├── gateway/
│   └── service/
│       └── src/main/java/org/eclipse/ditto/gateway/service/
│           └── endpoints/routes/
│               └── timeseries/
│                   └── TimeseriesRouteBuilder.java
│
└── policies/
    └── model/
        └── src/main/java/org/eclipse/ditto/policies/model/
            └── Permission.java   # Add READ_TS
```

---

## 11. Implementation Phases

### Phase 1: Foundation (MVP)

**Scope**:
- ts-facade service skeleton with Pekko cluster integration
- Single adapter (Apache IoTDB)
- Basic ingestion via pub/sub
- Simple query API (single property, time range, no aggregation)
- READ_TS permission in policy model

**Deliverables**:
- `ts-facade/model`, `ts-facade/api`, `ts-facade/service` modules
- `ts-facade/iotdb` adapter
- WoT extension: `ditto:ts-enabled` only
- Basic HTTP route in Gateway

### Phase 2: Full Query API

**Scope**:
- Aggregation functions (avg, min, max, sum, count, first, last)
- Downsampling with step parameter
- Multiple properties query
- Batch query endpoint
- Relative time expressions (`now-24h`)

**Deliverables**:
- Complete query parameter support
- Rich response format with metadata
- Fill strategies for gaps

### Phase 3: Additional Adapters

**Scope**:
- TimescaleDB adapter
- Adapter selection/configuration
- Schema management across adapters

**Deliverables**:
- `ts-facade/timescale` module
- Adapter factory with dynamic selection
- Documentation for adding custom adapters

### Phase 4: Advanced Features

**Scope**:
- Full WoT extension (`ditto:ts-retention`, `ditto:ts-resolution`, `ditto:ts-tags`)
- Retention policy enforcement (background cleanup)
- Connectivity integration for TS queries via messages
- Helm chart updates

**Deliverables**:
- Complete WoT ontology support
- Retention management actors
- Message-based query support
- Production-ready Helm configuration

---

## 12. Open Questions

1. **Attribute timeseries**: Should Thing-level attributes also support TS tracking, or only Feature properties?

2. **Multi-tenancy**: How to handle TS data isolation in multi-tenant deployments? Separate TS databases per tenant, or shared with namespace prefixes?

3. **Backfill**: Should there be an API to bulk-import historical data into the TS database?

4. **Cross-thing queries**: Should ts-facade support queries across multiple Things (e.g., "average temperature of all sensors in building A")?

5. **Streaming responses**: For very large result sets, should we support streaming (SSE) responses?

6. **TS database management**: Who creates/manages the TS database itself? Is it out of scope for Ditto?

7. **Migration**: How to handle schema changes when a WoT model's TS declarations change?

---

## 13. References

- [GitHub Issue #2291](https://github.com/eclipse-ditto/ditto/issues/2291) - Original proposal
- [Ditto WoT Integration](https://www.eclipse.dev/ditto/basic-wot-integration.html) - Existing WoT support
- [Apache IoTDB Documentation](https://iotdb.apache.org/UserGuide/latest/) - Primary TS backend
- [TimescaleDB Documentation](https://docs.timescale.com/) - Alternative TS backend
- [Ditto Architecture](.claude/context/architecture.md) - Service architecture patterns
