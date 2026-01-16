# Timeseries Facade Service (ts-facade) Design Document

**Related Issue**: [GitHub #2291 - Provide timeseries facade for Ditto feature properties](https://github.com/eclipse-ditto/ditto/issues/2291)

**Status**: Draft
**Last Updated**: 2026-01-16

---

## TL;DR

A new Ditto microservice that **automatically captures Thing property changes over time** and provides a **unified query API for historical timeseries data**.

### What It Does

| Capability | Description |
|------------|-------------|
| **Automatic Ingestion** | Property changes are automatically written to a timeseries database |
| **Declarative Configuration** | WoT ThingModel annotations define which properties to track (`ditto:ts-enabled`) |
| **Query API** | RESTful API for retrieving historical data with aggregations (avg, min, max, etc.) |
| **Access Control** | New `READ_TS` policy permission controls who can access timeseries data |
| **Pluggable Backend** | Default MongoDB Time Series adapter; extensible for IoTDB, TimescaleDB, etc. |

### Architecture Overview

```
                          ┌─────────────────────────────────────────────────┐
                          │               Ditto Cluster                     │
                          │                                                 │
   ┌──────────┐           │   ┌──────────┐         ┌─────────────┐         │
   │  Device  │ property  │   │ Things   │ pub/sub │ ts-facade   │         │
   │          │─updates──▶│   │ Service  │────────▶│ Service     │         │
   └──────────┘           │   └──────────┘         └──────┬──────┘         │
                          │                               │                 │
   ┌──────────┐           │   ┌──────────┐               │                 │
   │ Client/  │ TS query  │   │ Gateway  │───────────────┘                 │
   │ Dashboard│──────────▶│   │ Service  │         (direct routing)        │
   └──────────┘           │   └──────────┘                                 │
                          │                                                 │
                          └──────────────────────────────┬──────────────────┘
                                                         │
                                                         ▼
                                               ┌─────────────────┐
                                               │ Timeseries DB   │
                                               │ (MongoDB TS,    │
                                               │  IoTDB, etc.)   │
                                               └─────────────────┘
```

**Data flow:**
1. Device updates a Thing property → Things service persists the event
2. If property has `ditto:ts-enabled: true` in WoT model → publish to ts-facade via pub/sub
3. ts-facade writes data point to timeseries database
4. Clients query historical data via Gateway → ts-facade → TS database

### HTTP API Overview

| Endpoint | Description |
|----------|-------------|
| `GET /api/2/timeseries/things/{thingId}/features/{featureId}/properties/{property}` | Get timeseries for a single property |
| `GET /api/2/timeseries/things/{thingId}/features/{featureId}/properties` | Get timeseries for all TS-enabled properties |
| `GET /api/2/timeseries/things/{thingId}/attributes/{attribute}` | Get timeseries for an attribute |
| `POST /api/2/timeseries/things/{thingId}/features/{featureId}/query` | Batch query with multiple properties/aggregations |
| `POST /api/2/timeseries/query` | Cross-thing aggregation query (fleet analytics) |

**Common query parameters:**
- `from`, `to` — Time range (ISO 8601 or relative like `now-24h`)
- `step` — Downsampling interval (`1m`, `5m`, `1h`, `1d`)
- `agg` — Aggregation function (`avg`, `min`, `max`, `sum`, `count`, `first`, `last`)

### WoT Configuration Example

```json
{
  "properties": {
    "temperature": {
      "type": "number",
      "ditto:ts-enabled": true,
      "ditto:ts-retention": "P90D",
      "ditto:ts-tags": {
        "attributes/building": "{{ thing-json:attributes/building }}"
      }
    }
  }
}
```

### Key Design Decisions

- **Separate from event sourcing** — TS data is optimized for time-range queries, not revision history
- **Policy-controlled** — `READ_TS` permission independent of `READ` permission
- **MongoDB default** — Zero additional infrastructure using existing Ditto MongoDB
- **Extensible** — Adapter interface for dedicated TS databases when needed

---

## 1. Overview

This document describes the architecture and design for a new Ditto service called **ts-facade** (Timeseries Facade). The service acts as a facade to timeseries databases, providing:

- **Automatic ingestion** of Thing property changes into a timeseries database
- **Query API** for retrieving historical timeseries data
- **Pluggable adapter interface** for integrating different timeseries database backends

Timeseries data is stored **separately from Ditto's event-sourced Thing revisions**. The ts-facade service defines a well-specified adapter API (see [Section 8.1: Adapter Interface](#81-adapter-interface)) that allows integration with various timeseries databases.

**Default implementation**: Ditto ships with a **MongoDB Time Series** adapter as the default. This uses Ditto's existing MongoDB infrastructure with [Time Series Collections](https://www.mongodb.com/docs/manual/core/timeseries-collections/), requiring no additional database setup.

**Custom implementations**: Organizations can implement the adapter interface to integrate other timeseries databases (Apache IoTDB, TimescaleDB, InfluxDB, etc.) based on their specific requirements.

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

| Property | Type | Required | Description | Default |
|----------|------|----------|-------------|---------|
| `ditto:ts-enabled` | boolean | Yes | Enable TS ingestion for this property | `false` |
| `ditto:ts-retention` | string (ISO 8601 duration) | No | How long to retain data | ts-facade config |
| `ditto:ts-resolution` | string (ISO 8601 duration) | No | Minimum sampling interval | ts-facade config |
| `ditto:ts-tags` | object | No | Tags/dimensions for grouping (see below) | `{}` |

**Note**: `ditto:ts-retention` and `ditto:ts-resolution` are optional. When not specified in the WoT model, the ts-facade service defaults are used (see [Section 9: Configuration](#9-configuration)).

**Scope**: These TS declarations can be applied to:
- **Thing-level properties** (mapped to Ditto attributes)
- **Feature properties** (mapped to Ditto feature properties)

This enables timeseries tracking for both slowly-changing metadata (e.g., battery level as attribute) and high-frequency sensor data (e.g., temperature as feature property).

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

### 4.3 Example ThingModels

WoT ThingModels use `links` with `rel: "tm:submodel"` to compose features. Below are examples showing TS declarations at both Thing-level (attributes) and Feature-level (feature properties).

#### Thing-Level ThingModel (sensor-device-1.0.0.tm.jsonld)

```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    {"ditto": "https://ditto.eclipseprojects.io/wot/ditto-extension#"}
  ],
  "@type": "tm:ThingModel",
  "title": "Environmental Sensor Device",
  "version": { "model": "1.0.0" },
  "links": [
    {
      "rel": "tm:submodel",
      "href": "https://models.2.ditto.eclipseprojects.io/environment-sensor-1.0.0.tm.jsonld",
      "type": "application/tm+json",
      "instanceName": "environment"
    }
  ],
  "properties": {
    "batteryExchangeDate": {
      "title": "Battery Exchange Date",
      "type": "string",
      "format": "date",
      "ditto:ts-enabled": true,
      "ditto:ts-retention": "P5Y",
      "ditto:ts-tags": {
        "attributes/building": "{{ thing-json:attributes/building }}"
      }
    },
    "serialNumber": {
      "title": "Serial Number",
      "type": "string",
      "readOnly": true
    }
  }
}
```

#### Feature-Level ThingModel (environment-sensor-1.0.0.tm.jsonld)

```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    {"ditto": "https://ditto.eclipseprojects.io/wot/ditto-extension#"}
  ],
  "@type": "tm:ThingModel",
  "title": "Environment Sensor",
  "version": { "model": "1.0.0" },
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
      "ditto:ts-tags": {
        "attributes/building": "{{ thing-json:attributes/building }}",
        "attributes/floor": "{{ thing-json:attributes/floor }}",
        "sensorType": "environmental"
      }
    }
  }
}
```

#### Resulting Ditto Thing Structure

```json
{
  "thingId": "org.eclipse.ditto:sensor-1",
  "definition": "https://models.2.ditto.eclipseprojects.io/sensor-device-1.0.0.tm.jsonld",
  "attributes": {
    "batteryExchangeDate": "2025-06-15",
    "serialNumber": "SN-12345",
    "building": "A",
    "floor": "2"
  },
  "features": {
    "environment": {
      "definition": ["https://models.2.ditto.eclipseprojects.io/environment-sensor-1.0.0.tm.jsonld"],
      "properties": {
        "temperature": 23.5,
        "humidity": 65.2
      }
    }
  }
}
```

**Note on the examples above**:

| Property | ThingModel Location | Ditto Location | TS Enabled | Notes |
|----------|---------------------|----------------|------------|-------|
| `batteryExchangeDate` | Thing-level `properties` | `attributes/batteryExchangeDate` | ✅ | Infrequent updates, 5-year retention |
| `serialNumber` | Thing-level `properties` | `attributes/serialNumber` | ❌ | Static, no `ditto:ts-enabled` |
| `temperature` | Feature-level `properties` | `features/environment/properties/temperature` | ✅ | High-frequency, 90-day retention |
| `humidity` | Feature-level `properties` | `features/environment/properties/humidity` | ✅ | Uses ts-facade defaults |

**Use cases for attribute timeseries:**
- Maintenance dates (battery exchange, calibration, service visits)
- Firmware versions (track upgrade history)
- Location changes (asset tracking over time)
- Configuration changes (audit trail)

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

| RQL | MongoDB MQL | IoTDB SQL | TimescaleDB SQL |
|-----|-------------|-----------|-----------------|
| `eq(attributes/building,'A')` | `{"meta.tags.attributes/building": "A"}` | `attributes_building = 'A'` | `"attributes/building" = 'A'` |
| `gt(attributes/floor,2)` | `{"meta.tags.attributes/floor": {$gt: 2}}` | `attributes_floor > 2` | `"attributes/floor" > 2` |
| `like(attributes/building,'A*')` | `{"meta.tags.attributes/building": /^A.*/}` | `attributes_building LIKE 'A%'` | `"attributes/building" LIKE 'A%'` |
| `and(eq(a,'x'),gt(b,5))` | `{$and: [{...}, {...}]}` | `a = 'x' AND b > 5` | `a = 'x' AND b > 5` |

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

### 8.3 Adapter Implementations

#### Default Adapter (Shipped with Ditto)

| Database | Module | Status | Notes |
|----------|--------|--------|-------|
| **MongoDB Time Series** | `ts-facade-mongodb` | **Default** | Uses existing Ditto MongoDB, no additional infrastructure |

#### Custom Adapter Examples

Organizations can implement the `TimeseriesAdapter` interface to integrate other timeseries databases. Example implementations that could be developed:

| Database | Potential Module | Notes |
|----------|------------------|-------|
| Apache IoTDB | `ts-facade-iotdb` | Native Java client, tree-based schema |
| TimescaleDB | `ts-facade-timescale` | JDBC, PostgreSQL extension |
| InfluxDB 2.x | `ts-facade-influx` | HTTP API, Flux queries |

**Note**: These are examples of possible custom implementations, not commitments. The adapter interface (Section 8.1) is designed to be generic enough to support various TS databases.

### 8.3.1 MongoDB Time Series Collections

MongoDB 5.0+ includes native [Time Series Collections](https://www.mongodb.com/docs/manual/core/timeseries-collections/) that provide an attractive option for Ditto deployments:

**Key Advantages:**
- **Zero additional infrastructure** - Ditto already requires MongoDB
- **Unified operations** - Same backup, monitoring, and authentication
- **Simplified deployment** - No additional database to manage
- **Sufficient performance** - Adequate for many IoT use cases

**MongoDB TS Collection Structure:**

```javascript
// Time Series Collection creation
db.createCollection("ts_data", {
  timeseries: {
    timeField: "timestamp",           // Required: timestamp field
    metaField: "meta",                // Optional: metadata for grouping
    granularity: "seconds"            // "seconds" | "minutes" | "hours"
  },
  expireAfterSeconds: 7776000         // 90 days retention via TTL
})

// Document structure
{
  "timestamp": ISODate("2026-01-15T10:30:00.000Z"),
  "meta": {
    "thingId": "org.eclipse.ditto:sensor-1",
    "featureId": "environment",
    "propertyPath": "/temperature",
    // Denormalized tags for efficient filtering
    "tags": {
      "attributes/building": "A",
      "attributes/floor": "2",
      "sensorType": "environmental"
    }
  },
  "value": 23.5,
  "revision": 42
}
```

**Aggregation Pipeline for Queries:**

```javascript
// Example: Average temperature per hour, grouped by floor
db.ts_data.aggregate([
  // Filter by tags (RQL translation)
  { $match: {
    "meta.tags.attributes/building": "A",
    "timestamp": {
      $gte: ISODate("2026-01-14T00:00:00Z"),
      $lt: ISODate("2026-01-15T00:00:00Z")
    }
  }},
  // Group by time bucket and floor
  { $group: {
    _id: {
      timeBucket: { $dateTrunc: { date: "$timestamp", unit: "hour" } },
      floor: "$meta.tags.attributes/floor"
    },
    avg: { $avg: "$value" },
    min: { $min: "$value" },
    max: { $max: "$value" },
    count: { $count: {} }
  }},
  { $sort: { "_id.timeBucket": 1 } }
])
```

**Supported Operations:**

| Operation | MongoDB Support | Implementation |
|-----------|-----------------|----------------|
| avg, min, max, sum, count | ✅ Native | `$avg`, `$min`, `$max`, `$sum`, `$count` |
| first, last | ✅ Native | `$first`, `$last` |
| Time bucketing | ✅ Native | `$dateTrunc` with `binSize` and `unit` |
| Gap filling | ✅ Native | `$densify` + `$fill` |
| Window functions | ✅ Native | `$setWindowFields` |
| Tag filtering | ✅ Native | `$match` on `meta.tags.*` |
| Retention | ✅ TTL Index | `expireAfterSeconds` on collection |

**Limitations:**

| Limitation | Impact | Mitigation |
|------------|--------|------------|
| No change streams | Cannot watch TS data changes | Use Thing events instead |
| Updates restricted | Can only update `metaField` | TS data is append-only anyway |
| No `distinct` command | Use `$group` instead | Already using aggregation pipeline |
| No transactions | Cannot write in transactions | Acceptable for TS ingestion |
| Performance vs dedicated TS DB | ~5x slower than InfluxDB | Sufficient for most IoT workloads |

**When to Choose MongoDB Time Series:**

| Scenario | Recommendation |
|----------|----------------|
| Simple deployment, moderate throughput | ✅ MongoDB TS |
| Existing MongoDB expertise | ✅ MongoDB TS |
| Very high write throughput (>100k/sec) | ❌ Use dedicated TS DB |
| Complex analytical queries | ❌ Consider TimescaleDB |
| Minimal operational overhead priority | ✅ MongoDB TS |

### 8.4 Schema Mapping

**Ditto Concept → TS Database Concept**:

| Ditto | MongoDB TS | IoTDB | TimescaleDB |
|-------|------------|-------|-------------|
| Namespace | Collection prefix | Storage Group | Schema |
| ThingId | `meta.thingId` | Device | Table prefix |
| FeatureId | `meta.featureId` | Entity (level 1) | Table suffix |
| PropertyPath | `meta.propertyPath` | Measurement | Column |
| Value | `value` field | Data point | Row value |
| Tags | `meta.tags.*` | Tags/Attributes | Additional columns |
| Retention | TTL index | TTL config | Retention policy |

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

**MongoDB Time Series Schema Example**:
```javascript
// One collection per namespace (or shared collection with namespace in meta)
db.createCollection("ts_org_eclipse_ditto", {
  timeseries: {
    timeField: "timestamp",
    metaField: "meta",
    granularity: "seconds"
  },
  expireAfterSeconds: 7776000  // 90 days
})

// Compound index on meta fields for efficient queries
db.ts_org_eclipse_ditto.createIndex({
  "meta.tags.attributes/building": 1,
  "meta.tags.attributes/floor": 1
})

// Example document
{
  "timestamp": ISODate("2026-01-15T10:30:00.000Z"),
  "meta": {
    "thingId": "org.eclipse.ditto:sensor-1",
    "featureId": "environment",
    "propertyPath": "/temperature",
    "tags": {
      "attributes/building": "A",
      "attributes/floor": "2",
      "sensorType": "environmental"
    }
  },
  "value": 23.5,
  "revision": 42
}
```

### 8.5 TS Database Comparison

This comparison helps users decide whether the default MongoDB adapter is sufficient or whether a custom adapter implementation for another TS database might be beneficial.

| Feature | MongoDB TS (Default) | IoTDB | TimescaleDB | InfluxDB |
|---------|----------------------|-------|-------------|----------|
| **Deployment** |
| Additional infrastructure | ❌ None (uses existing) | ✅ Required | ✅ Required | ✅ Required |
| Operational complexity | Low | Medium | Medium | Medium |
| **Query Capabilities** |
| Aggregations (avg/min/max/sum/count) | ✅ | ✅ | ✅ | ✅ |
| first/last | ✅ | ✅ | ✅ | ✅ |
| Time bucketing | ✅ `$dateTrunc` | ✅ `GROUP BY TIME` | ✅ `time_bucket` | ✅ `aggregateWindow` |
| Gap filling | ✅ `$densify`+`$fill` | ✅ `FILL` | ✅ `time_bucket_gapfill` | ✅ `fill()` |
| Window functions | ✅ `$setWindowFields` | ✅ | ✅ | ✅ |
| Complex joins | ✅ `$lookup` | Limited | ✅ Full SQL | Limited |
| **Performance** |
| Write throughput | Medium | High | High | Very High |
| Simple query speed | Medium | High | High | Very High |
| Complex query speed | Medium | Medium | Very High | Medium |
| Compression | Good | Excellent | Good | Excellent |
| **RQL Translation** |
| Translation complexity | Medium (MQL) | Medium (SQL-like) | Low (SQL) | Medium (Flux) |
| **Integration** |
| Java client | ✅ MongoDB Driver | ✅ Native | ✅ JDBC | ✅ HTTP/Client |
| Authentication | Shared with Ditto | Separate | Separate | Separate |

**When to Consider a Custom Adapter:**

| Scenario | Recommendation |
|----------|----------------|
| **Getting started / PoC** | Use default MongoDB TS adapter |
| **Small-medium deployment** (<10k writes/sec) | Use default MongoDB TS adapter |
| **High-volume IoT** (>100k writes/sec) | Consider custom IoTDB or InfluxDB adapter |
| **Complex analytics requirements** | Consider custom TimescaleDB adapter |
| **Edge deployment** (resource constrained) | Use default MongoDB TS adapter |

**Note**: IoTDB, TimescaleDB, and InfluxDB adapters require custom implementation of the `TimeseriesAdapter` interface. The comparison above is provided as guidance for organizations evaluating custom adapter development.

---

## 9. Configuration

### 9.1 ts-facade Service Configuration

**File**: `ts-facade/service/src/main/resources/ts-facade.conf`

```hocon
ditto {
  ts-facade {
    # Timeseries adapter configuration
    adapter {
      # Which adapter to use. Default: "mongodb"
      # Custom adapters can register additional types via SPI
      type = "mongodb"
      type = ${?TS_FACADE_ADAPTER_TYPE}

      # MongoDB Time Series adapter configuration (default adapter)
      mongodb {
        # Use Ditto's existing MongoDB URI (recommended)
        # If not set, uses ditto.mongodb.uri from common config
        uri = ${?TS_FACADE_MONGODB_URI}

        # Database name for timeseries collections
        database = "ditto_ts"
        database = ${?TS_FACADE_MONGODB_DATABASE}

        # Collection name prefix (namespace appended)
        collection-prefix = "ts_"
        collection-prefix = ${?TS_FACADE_MONGODB_COLLECTION_PREFIX}

        # Time series granularity: "seconds", "minutes", "hours"
        # Use "seconds" for high-frequency data, "hours" for daily metrics
        granularity = "seconds"
        granularity = ${?TS_FACADE_MONGODB_GRANULARITY}

        # Write concern for ingestion
        write-concern = "majority"
        write-concern = ${?TS_FACADE_MONGODB_WRITE_CONCERN}
      }

      # Custom adapters would add their own configuration sections here
      # Example for a hypothetical IoTDB adapter:
      # iotdb {
      #   host = "localhost"
      #   port = 6667
      #   ...
      # }
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

    # =================================================================
    # Default values for WoT ditto:ts-* properties
    # Used when not specified in the WoT ThingModel
    # =================================================================

    # Default data retention period (ISO 8601 duration)
    # Can be overridden per property via ditto:ts-retention in WoT model
    default-retention = P90D
    default-retention = ${?TS_FACADE_DEFAULT_RETENTION}

    # Default minimum sampling resolution (ISO 8601 duration)
    # Data points arriving faster than this interval may be dropped/aggregated
    # Can be overridden per property via ditto:ts-resolution in WoT model
    # "0" or "PT0S" means no minimum resolution (accept all data points)
    default-resolution = PT0S
    default-resolution = ${?TS_FACADE_DEFAULT_RESOLUTION}
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
      # Default adapter type. Custom adapters can be added via SPI.
      type: "mongodb"

    # MongoDB adapter configuration (default, uses existing Ditto MongoDB)
    mongodb:
      # Uses Ditto's MongoDB URI by default (no config needed)
      # Override only if using separate MongoDB for timeseries:
      # uri: "mongodb://mongodb:27017"
      database: "ditto_ts"
      collectionPrefix: "ts_"
      granularity: "seconds"  # "seconds", "minutes", or "hours"
      writeConcern: "majority"

    # Custom adapter configurations would be added here
    # See documentation for implementing custom adapters

    ingestion:
      batchSize: 100
      flushInterval: "5s"

    query:
      defaultLookback: "24h"
      maxTimeRange: "365d"
      maxDataPoints: 10000

    # Defaults for WoT ditto:ts-* properties (when not specified in model)
    defaults:
      retention: "P90D"      # ditto:ts-retention default
      resolution: "PT0S"     # ditto:ts-resolution default (0 = no limit)

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
│   └── mongodb/                  # MongoDB TS adapter (Java 21) - DEFAULT
│       └── src/main/java/org/eclipse/ditto/tsfacade/mongodb/
│           ├── MongoDbTimeseriesAdapter.java
│           └── MongoDbRqlTranslator.java
│
│   # Custom adapters (e.g., IoTDB, TimescaleDB) would be implemented
│   # externally using the ts-facade/api interfaces
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
- **MongoDB Time Series adapter** (default, uses existing infrastructure)
- Basic ingestion via pub/sub
- Simple query API (single property, time range, no aggregation)
- READ_TS permission in policy model

**Deliverables**:
- `ts-facade/model`, `ts-facade/api`, `ts-facade/service` modules
- `ts-facade/mongodb` adapter (default)
- WoT extension: `ditto:ts-enabled` only
- Basic HTTP route in Gateway

**Why MongoDB for MVP:**
- Zero additional infrastructure (Ditto already requires MongoDB 5.0+)
- Faster time-to-value for users
- Simplifies initial testing and adoption
- Users can migrate to dedicated TS DB later if needed

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

### Phase 3: Adapter Extensibility

**Scope**:
- Finalize and document the `TimeseriesAdapter` interface for custom implementations
- Adapter selection/configuration mechanism
- Schema management documentation

**Deliverables**:
- Stable adapter API with semantic versioning
- Adapter factory with dynamic selection
- Documentation: "Implementing a Custom TS Adapter"
- Example adapter implementation guide
- Migration guide: MongoDB → custom TS database

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

### Resolved

1. ~~**Attribute timeseries**: Should Thing-level attributes also support TS tracking, or only Feature properties?~~
   **Decision**: Yes, attributes are supported. The HTTP API already includes attribute endpoints (`/api/2/timeseries/things/{thingId}/attributes/{path}`). WoT ThingModels can declare `ditto:ts-enabled` on Thing-level properties (attributes), not just Feature properties.

4. ~~**Cross-thing queries**: Should ts-facade support queries across multiple Things?~~
   **Decision**: Yes, via tag denormalization. The `POST /api/2/timeseries/query` endpoint supports cross-thing aggregation using tags stored natively in the TS database. See Section 7.2.

6. ~~**TS database management**: Who creates/manages the TS database itself?~~
   **Decision**: Out of scope for Ditto. The TS database (MongoDB, IoTDB, TimescaleDB, etc.) must be provisioned and managed externally. Ditto's ts-facade only connects to an existing database. This follows the same pattern as Ditto's MongoDB requirement.

### Open

2. **Multi-tenancy**: How to handle TS data isolation in multi-tenant deployments? Separate TS databases per tenant, or shared with namespace prefixes?

3. **Backfill**: Should there be an API to bulk-import historical data into the TS database?

5. **Streaming responses**: For very large result sets, should we support streaming (SSE) responses?

7. **Migration**: How to handle schema changes when a WoT model's TS declarations change?

---

## 13. References

- [GitHub Issue #2291](https://github.com/eclipse-ditto/ditto/issues/2291) - Original proposal
- [Ditto WoT Integration](https://www.eclipse.dev/ditto/basic-wot-integration.html) - Existing WoT support
- [MongoDB Time Series Collections](https://www.mongodb.com/docs/manual/core/timeseries-collections/) - Default TS backend
- [MongoDB Time Series Limitations](https://www.mongodb.com/docs/manual/core/timeseries/timeseries-limitations/) - Important restrictions
- [MongoDB Aggregation Pipeline](https://www.mongodb.com/docs/manual/core/aggregation-pipeline/) - Query implementation
- [Apache IoTDB Documentation](https://iotdb.apache.org/UserGuide/latest/) - High-volume TS backend
- [TimescaleDB Documentation](https://docs.timescale.com/) - Analytics-focused TS backend
- [Ditto Architecture](.claude/context/architecture.md) - Service architecture patterns
