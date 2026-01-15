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
| `ditto:ts-tags` | array of strings | Additional tags/dimensions | `[]` |

### 4.2 Example ThingModel

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
      "ditto:ts-resolution": "PT1S"
    },
    "humidity": {
      "type": "number",
      "unit": "percent",
      "ditto:category": "status",
      "ditto:ts-enabled": true,
      "ditto:ts-retention": "P30D"
    },
    "serialNumber": {
      "type": "string",
      "ditto:category": "configuration",
      "ditto:ts-enabled": false
    }
  }
}
```

### 4.3 Runtime Behavior

When a Thing is created/modified with a WoT ThingModel reference (`definition`):

1. Things service resolves the ThingModel
2. Things service extracts `ditto:ts-enabled` properties
3. Things service caches the TS-enabled property paths per Thing
4. On property changes, Things service checks cache and publishes if enabled

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
    "namespace": "org.eclipse.ditto",
    "location": "building-a"
  },
  "retention": "P90D"
}
```

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

### 7.1 Endpoints

#### Single Property Timeseries

```
GET /api/2/things/{thingId}/features/{featureId}/properties/{propertyPath}/timeseries
```

#### Feature Timeseries (Multiple Properties)

```
GET /api/2/things/{thingId}/features/{featureId}/timeseries
```

#### Thing Attribute Timeseries

```
GET /api/2/things/{thingId}/attributes/{attributePath}/timeseries
```

#### Batch Query (Multiple Properties/Aggregations)

```
POST /api/2/things/{thingId}/features/{featureId}/timeseries/query
```

### 7.2 Query Parameters

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

### 7.3 Response Format

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
POST /api/2/things/org.eclipse.ditto:sensor-1/features/environment/timeseries/query
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

### 7.4 Error Responses

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

    CompletionStage<TimeseriesQueryResult> query(TimeseriesQuery query);

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

public enum Aggregation {
    AVG, MIN, MAX, SUM, COUNT, FIRST, LAST
}

public enum FillStrategy {
    NULL, PREVIOUS, LINEAR, ZERO
}

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
