# Timeseries Service Design Document

**Related Issue**: [GitHub #2291 - Provide timeseries facade for Ditto feature properties](https://github.com/eclipse-ditto/ditto/issues/2291)

**Status**: Draft
**Last Updated**: 2026-01-19

---

## TL;DR

A new Ditto microservice that **automatically captures Thing property changes over time** and provides a **unified query API for historical timeseries data**.

### What It Does

| Capability                    | Description                                                                                         |
|-------------------------------|-----------------------------------------------------------------------------------------------------|
| **Automatic Ingestion**       | Property changes are automatically written to a timeseries database                                 |
| **Declarative Configuration** | WoT ThingModel `ditto:timeseries` annotation defines which properties to track                      |
| **Query API**                 | RESTful API for retrieving historical data with aggregations (avg, min, max, etc.)                  |
| **Access Control**            | Configurable policy permission (`READ_TS` or `READ`) controls who can access timeseries data        |
| **Pluggable Backend**         | Default MongoDB Time Series adapter; extensible for IoTDB, TimescaleDB, etc.                        |

### Architecture Overview

```
                          ┌─────────────────────────────────────────────────┐
                          │               Ditto Cluster                     │
                          │                                                 │
   ┌──────────┐           │   ┌──────────┐         ┌─────────────┐         │
   │  Device  │ property  │   │ Things   │ pub/sub │ Timeseries   │         │
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
2. If property has `ditto:timeseries` with `ingest: "ALL"` in WoT model → publish to Timeseries service via pub/sub
3. Timeseries service writes data point to timeseries database
4. Clients query historical data via Gateway → Timeseries service → TS database

### HTTP API Overview

| Endpoint                                                                             | Description                                          |
|--------------------------------------------------------------------------------------|------------------------------------------------------|
| `GET /api/2/timeseries/things/{thingId}/features/{featureId}/properties/{property}`  | Get timeseries for a single property                 |
| `GET /api/2/timeseries/things/{thingId}/features/{featureId}/properties`             | Get timeseries for all TS-enabled feature properties |
| `GET /api/2/timeseries/things/{thingId}/features`                                    | Get timeseries for all TS-enabled features           |
| `GET /api/2/timeseries/things/{thingId}/attributes/{attribute}`                      | Get timeseries for an attribute                      |
| `GET /api/2/timeseries/things/{thingId}/attributes`                                  | Get timeseries for all TS-enabled attributes         |
| `GET /api/2/timeseries/things/{thingId}`                                             | Get timeseries for all TS-enabled values             |
| `POST /api/2/timeseries/things/{thingId}/query`                                      | Batch query with multiple properties/aggregations    |
| `POST /api/2/timeseries/query`                                                       | Cross-thing aggregation query (fleet analytics)      |

**Common query parameters:**
- `from`, `to` — Time range (ISO 8601 or relative like `now-24h`)
- `step` — Downsampling interval (`1m`, `5m`, `1h`, `1d`)
- `agg` — Aggregation function (`avg`, `min`, `max`, `sum`, `count`, `first`, `last`, `derivative`)

### WoT Configuration Example

```json
{
  "properties": {
    "temperature": {
      "type": "number",
      "ditto:timeseries": {
        "ingest": "ALL",
        "tags": {
          "attributes/building": "{{ thing-json:attributes/building }}"
        }
      }
    }
  }
}
```

### Key Design Decisions

- **Separate from event sourcing** — TS data is optimized for time-range queries, not revision history
- **Policy-controlled** — configurable permission (`READ_TS` by default, or `READ`) controls timeseries access; operators can choose between fine-grained control or simplified permission management
- **MongoDB default** — Zero additional infrastructure using existing Ditto MongoDB as a default implementation
- **Extensible** — Adapter interface for dedicated TS databases when needed, e.g. when higher scalability needs are in place or an existing concrete TS DB should be used

---

## 1. Overview

This document describes the architecture and design for a new Ditto service called **Timeseries**.  
The service provides a unified interface to timeseries databases, providing:

- **Automatic ingestion** of Thing property changes into a timeseries database
- **Query API** for retrieving historical timeseries data
- **Pluggable adapter interface** for integrating different timeseries database backends

Timeseries data is stored **separately from Ditto's event-sourced Thing revisions**.  
The Timeseries service defines a well-specified adapter API (see [Section 8.1: Adapter Interface](#81-adapter-interface)) that allows integration with various timeseries databases.

**Default implementation**: Ditto ships with a **MongoDB Time Series** adapter as the default. 
This uses Ditto's existing MongoDB infrastructure with [Time Series Collections](https://www.mongodb.com/docs/manual/core/timeseries-collections/), requiring no additional database setup.

**Custom implementations**: Organizations can implement the adapter interface to integrate other timeseries databases (Apache IoTDB, TimescaleDB, InfluxDB, etc.) based on their specific requirements.

---

## 2. Goals & Non-Goals

### Goals

1. Provide a unified API for timeseries data across different TS database backends
2. Enable declarative configuration of which properties to ingest via WoT ThingModel annotations
3. Integrate with Ditto's policy model for access control (configurable permission: `READ_TS` or `READ`)
4. Support common timeseries query operations (time ranges, aggregations, downsampling)
5. Minimize latency impact on the main Thing update path

### Non-Goals

1. Replacing Ditto's event sourcing in MongoDB (historical revisions remain separate)
2. Supporting arbitrary SQL/query passthrough to TS databases
3. Real-time streaming of timeseries data (use existing SSE/WebSocket for Ditto's thing events)
4. Complex analytics or ML pipelines (TS databases can be queried directly for that)

---

## 3. Architecture

### 3.1 High-Level Design

```
                                      Ditto Cluster
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│   ┌─────────────┐                                   ┌─────────────────────┐ │
│   │   Gateway   │──────────┐              ┌─────────│    Connectivity     │ │
│   │   Service   │          │    Query     │         │      Service        │ │
│   │ [TS Routes] │          │              │         │ [TS Message Handler]│ │
│   └─────────────┘          │              │         └─────────────────────┘ │
│                            ▼              ▼                                 │
│   ┌──────────┐       ┌───────────────────────┐                              │
│   │ Policies │◀──────│     Timeseries        │                              │
│   │ Service  │ fetch │       Service         │                              │
│   │          │policy │                       │                              │
│   └──────────┘ for   │ - Subscribes to       │                              │
│                READ_TS  things.ts-events:    │                              │
│                      │ - Enforces READ_TS    │                              │
│                      │ - Writes to TS DB     │                              │
│                      └───────────▲───────────┘                              │
│                                  │                                          │
│                                  │ Pub/Sub (things.ts-events: topic)        │
│                                  │                                          │
│                           ┌──────┴──────┐                                   │
│                           │   Things    │                                   │
│                           │   Service   │                                   │
│                           │             │                                   │
│                           │[TS Publisher│                                   │
│                           └─────────────┘                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
                         ┌─────────────────┐
                         │  TS Database    │
                         │  (MongoDB TS,   │
                         │   IoTDB, etc.)  │
                         └─────────────────┘
```

### 3.2 Data Flow

#### Ingestion Flow

```
1. Device sends property update
2. Gateway/Connectivity routes to Things service
3. Things service persists event to MongoDB
4. Things service checks WoT model for TS-enabled properties
5. If TS-enabled: publish ThingEvent with resolved tags to "things.ts-events:" topic
6. Timeseries service receives event, transforms to data point, writes to TS database
```

#### Query Flow

```
1. Client sends TS query to Gateway (or via Connectivity message)
2. Edge service routes directly to Timeseries service (no Things service hop)
3. Timeseries service loads/caches Policy from Policies service
4. Timeseries service checks READ_TS permission for requested properties
5. Timeseries service queries TS database
6. Timeseries service formats and returns response
```

### 3.3 Service Responsibilities

| Service          | Responsibility                                                                 |
|------------------|--------------------------------------------------------------------------------|
| **Things**       | Evaluate WoT TS extensions, publish ThingEvents to `things.ts-events:` topic   |
| **Timeseries**   | Ingest data, execute queries, manage TS adapters, enforce `READ_TS` permission |
| **Gateway**      | HTTP routes for TS queries, forward to timeseries                              |
| **Connectivity** | Handle TS query messages, forward to timeseries                                |
| **Policies**     | Provide policy data                                                            |

---

## 4. WoT ThingModel Extension

### 4.1 Ditto WoT Extension Ontology

Extend the existing `ditto:` ontology (IRI: `https://ditto.eclipseprojects.io/wot/ditto-extension#`) with a single timeseries declaration:

| Property           | Type   | Required | Description                          |
|--------------------|--------|----------|--------------------------------------|
| `ditto:timeseries` | object | No       | Timeseries configuration (see below) |

**`ditto:timeseries` object structure:**

| Key      | Type   | Required | Description                                | Values/Default            |
|----------|--------|----------|--------------------------------------------|---------------------------|
| `ingest` | string | Yes      | Controls data ingestion                    | `"ALL"`, `"NONE"`         |
| `tags`   | object | No       | Tags/dimensions for grouping (see below)   | `{}` (empty object)       |

**`ingest` enumeration values:**

| Value    | Description                                                                    |
|----------|--------------------------------------------------------------------------------|
| `"ALL"`  | Ingest all value changes for this property into the timeseries database        |
| `"NONE"` | Disable timeseries ingestion (useful for temporarily pausing without removing) |

> **Future extension**: The `ingest` field may be extended with conditional ingestion options (e.g., ingest only when value exceeds threshold).

**Note on retention and resolution**: These are configured at the **timeseries database level** (e.g., MongoDB collection TTL, granularity), not per-property. This ensures consistency across different TS database backends where these settings are typically collection-wide.

**Scope**: These TS declarations can be applied to:
- **Thing-level properties** (mapped to Ditto attributes)
- **Feature properties** (mapped to Ditto feature properties)

This enables timeseries tracking for both slowly-changing metadata (e.g., battery level as attribute) and high-frequency sensor data (e.g., temperature as feature property).

### 4.2 Tag Declaration with Placeholders

The `tags` object within `ditto:timeseries` defines dimensions that are stored with each data point in the TS database, enabling efficient cross-thing grouping and filtering **without querying Ditto**.

**Tag values support two formats:**

| Format          | Example                                  | Description                          |
|-----------------|------------------------------------------|--------------------------------------|
| **Placeholder** | `"{{ thing-json:attributes/building }}"` | Resolved dynamically from Thing JSON |
| **Constant**    | `"production"`                           | Static value for all data points     |

**Placeholder syntax**: `{{ thing-json:<json-pointer> }}`
- Uses Ditto's existing placeholder mechanism
- `<json-pointer>` is a JSON pointer into the Thing's JSON structure
- Resolved at ingestion time from the current Thing state

**Tag key naming convention:**

To ensure consistency with Ditto search RQL syntax, tag keys follow these rules:

| Tag Type                 | Key Format               | Example                                                   |
|--------------------------|--------------------------|-----------------------------------------------------------|
| **Placeholder-resolved** | Full Thing JSON path     | `"attributes/building"`, `"features/env/properties/type"` |
| **Static/constant**      | Custom name (restricted) | `"environment"`, `"region"`                               |

**Example tag declarations:**
```json
"ditto:timeseries": {
  "ingest": "ALL",
  "tags": {
    "attributes/building": "{{ thing-json:attributes/building }}",
    "attributes/floor": "{{ thing-json:attributes/floor }}",
    "features/environment/properties/type": "{{ thing-json:features/environment/properties/type }}",
    "environment": "production",
    "region": "eu-west"
  }
}
```

**Static tag name restrictions:**

Static tag names (constant values) must **NOT** use these reserved prefixes to avoid confusion with Thing structure:

| Reserved Prefix               | Reason                               |
|-------------------------------|--------------------------------------|
| `attributes` or `attributes/` | Conflicts with Thing attributes path |
| `features` or `features/`     | Conflicts with Thing features path   |
| `definition`                  | Reserved Thing field                 |
| `policyId`                    | Reserved Thing field                 |
| `thingId`                     | Reserved Thing field                 |
| `_` (underscore prefix)       | Reserved for internal use            |

```json
// ✅ Valid static tags
"ditto:timeseries": {
  "ingest": "ALL",
  "tags": {
    "environment": "production",
    "region": "eu-west",
    "deploymentId": "dep-123"
  }
}

// ❌ Invalid static tags (will be rejected)
"ditto:timeseries": {
  "ingest": "ALL",
  "tags": {
    "attributes": "something",        // Reserved prefix
    "features/custom": "value",       // Looks like Thing path
    "thingId": "override",            // Reserved field
    "_internal": "value"              // Reserved prefix
  }
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
      "ditto:timeseries": {
        "ingest": "ALL",
        "tags": {
          "attributes/building": "{{ thing-json:attributes/building }}"
        }
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
      "ditto:timeseries": {
        "ingest": "ALL",
        "tags": {
          "attributes/building": "{{ thing-json:attributes/building }}",
          "attributes/floor": "{{ thing-json:attributes/floor }}",
          "sensorType": "environmental"
        }
      }
    },
    "humidity": {
      "type": "number",
      "unit": "percent",
      "ditto:category": "status",
      "ditto:timeseries": {
        "ingest": "ALL",
        "tags": {
          "attributes/building": "{{ thing-json:attributes/building }}",
          "attributes/floor": "{{ thing-json:attributes/floor }}",
          "sensorType": "environmental"
        }
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

| Property              | ThingModel Location        | Ditto Location                                | TS Enabled | Notes                           |
|-----------------------|----------------------------|-----------------------------------------------|------------|---------------------------------|
| `batteryExchangeDate` | Thing-level `properties`   | `attributes/batteryExchangeDate`              | ✅          | Infrequent updates (dates)      |
| `serialNumber`        | Thing-level `properties`   | `attributes/serialNumber`                     | ❌          | Static, no `ditto:timeseries`   |
| `temperature`         | Feature-level `properties` | `features/environment/properties/temperature` | ✅          | High-frequency sensor data      |
| `humidity`            | Feature-level `properties` | `features/environment/properties/humidity`    | ✅          | High-frequency sensor data      |

**Use cases for attribute timeseries:**
- Maintenance dates (battery exchange, calibration, service visits)
- Firmware versions (track upgrade history)
- Location changes (asset tracking over time)
- Configuration changes (audit trail)

### 4.4 Runtime Behavior

When a Thing is created/modified with a WoT ThingModel reference (`definition`):

1. Things service resolves the ThingModel (already cached)
2. On property changes:
   - Things service checks WoT model for `ditto:timeseries` with `ingest: "ALL"` on changed path
   - If TS-enabled: resolves `tags` placeholders against current Thing JSON
   - Publishes ThingEvent with resolved tags as extra fields to `things.ts-events:` topic

---

## 5. Pub/Sub Integration

The Timeseries service receives events via a **dedicated pub/sub topic**. The Things service publishes to this topic based on WoT extension declarations, but remains unaware of the Timeseries service's internal data format.

### 5.1 Design Principles

| Principle                 | Implementation                                                                                   |
|---------------------------|--------------------------------------------------------------------------------------------------|
| **Loose coupling**        | Things service publishes standard `ThingEvent` messages, not TS-specific formats                 |
| **Things owns WoT logic** | Things service evaluates `ditto:timeseries.ingest` and resolves `ditto:timeseries.tags` placeholders |
| **Timeseries transforms** | Timeseries service transforms received events into its internal data point format                |
| **Extra fields for tags** | Resolved tag values are included as extra fields in the published event                          |

### 5.2 Topic Structure

**Topic**: `things.ts-events:` (similar pattern to `things.events:`)

The Things service publishes `ThingEvent` messages to this topic when the changed path has `ditto:timeseries` with `ingest: "ALL"` in its WoT ThingModel.

### 5.3 Publishing Logic in Things Service

The Things service is responsible for:
1. Evaluating `ditto:timeseries.ingest` to decide **if** to publish
2. Resolving `ditto:timeseries.tags` placeholders to determine **extra fields**
3. Publishing the standard `ThingEvent` with extra fields attached

```java
/**
 * Publishes ThingEvents to the timeseries topic if the changed path is TS-enabled.
 * Part of Things service - no dependency on Timeseries service.
 */
public class TimeseriesEventPublisher {

    private static final String TS_TOPIC = "things.ts-events:";
    private final DistributedPub<ThingEvent<?>> distributedPub;

    /**
     * Called after a ThingEvent is persisted.
     * Publishes to TS topic if the changed path has ditto:timeseries with ingest: "ALL".
     */
    public void publishIfTimeseriesEnabled(
            final ThingEvent<?> event,
            final Thing thing,
            final ResolvedWotThingModel resolvedModel) {

        // 1. Check if changed path is TS-enabled (lookup in already-cached resolved model)
        final JsonPointer changedPath = event.getResourcePath();
        if (!resolvedModel.isTimeseriesIngestEnabled(changedPath)) {
            return; // This path has no ditto:timeseries or ingest is "NONE"
        }

        // 2. Resolve extra fields from WoT model (tags, unit)
        final JsonObject extraFields = resolveExtraFields(
            thing,
            resolvedModel.getTimeseriesTags(changedPath),
            resolvedModel.getUnit(changedPath)         // WoT property "unit"
        );

        // 3. Publish event with extra fields
        final ThingEvent<?> enrichedEvent = event.setDittoHeaders(
            event.getDittoHeaders().toBuilder()
                .putHeader("ts-extra", extraFields.toString())
                .build()
        );

        distributedPub.publish(TS_TOPIC, enrichedEvent);
    }

    private JsonObject resolveExtraFields(
            final Thing thing,
            final Map<String, String> tagDeclarations,
            @Nullable final String unit) {

        final JsonObjectBuilder builder = JsonObject.newBuilder();

        // Resolve tags
        final JsonObjectBuilder tagsBuilder = JsonObject.newBuilder();
        for (Map.Entry<String, String> entry : tagDeclarations.entrySet()) {
            final String tagKey = entry.getKey();
            final String tagValue = entry.getValue();

            if (isPlaceholder(tagValue)) {
                // Resolve "{{ thing-json:attributes/building }}" from Thing
                final Optional<JsonValue> resolved =
                    resolvePlaceholder(thing, tagValue);
                resolved.ifPresent(v -> tagsBuilder.set(tagKey, v));
            } else {
                // Static value
                tagsBuilder.set(tagKey, tagValue);
            }
        }
        builder.set("tags", tagsBuilder.build());

        // Include unit from WoT property (strip semantic prefix if present)
        if (unit != null) {
            builder.set("unit", stripSemanticPrefix(unit));
        }

        return builder.build();
    }

    /**
     * Strips semantic prefixes from WoT unit values.
     * E.g., "om2:kilowatt" -> "kilowatt", "qudt:DEG_C" -> "DEG_C"
     */
    private String stripSemanticPrefix(final String unit) {
        final int colonIndex = unit.indexOf(':');
        if (colonIndex > 0 && colonIndex < unit.length() - 1) {
            return unit.substring(colonIndex + 1);
        }
        return unit;
    }
}
```

### 5.4 Extra Fields Format

The `ts-extra` header contains resolved WoT TS extension values:

```json
{
  "tags": {
    "attributes/building": "A",
    "attributes/floor": "2",
    "sensorType": "environmental"
  },
  "unit": "cel"
}
```

**Unit extraction from WoT ThingModel**:

The `unit` is automatically extracted from the WoT property's `unit` field. Semantic prefixes are stripped:

| WoT Property `unit`   | Extracted `unit` | Notes                              |
|-----------------------|------------------|------------------------------------|
| `"cel"`               | `"cel"`          | No prefix                          |
| `"om2:kilowatt"`      | `"kilowatt"`     | Prefix `om2:` stripped             |
| `"qudt:DEG_C"`        | `"DEG_C"`        | Prefix `qudt:` stripped            |
| `"schema:Celsius"`    | `"Celsius"`      | Prefix `schema:` stripped          |
| *(not specified)*     | `null`           | Unit not included in extra fields  |

The unit is stored with each data point, enabling proper display and unit conversion in queries.

**Tag resolution example**:

| WoT Declaration                          | Thing State                       | Resolved Value |
|------------------------------------------|-----------------------------------|----------------|
| `"{{ thing-json:attributes/building }}"` | `{"attributes":{"building":"A"}}` | `"A"`          |
| `"{{ thing-json:attributes/floor }}"`    | `{"attributes":{"floor":"2"}}`    | `"2"`          |
| `"production"` (static)                  | —                                 | `"production"` |

### 5.5 Subscriber in Timeseries Service

The Timeseries service subscribes to the TS topic and transforms `ThingEvent` messages into its internal `TimeseriesDataPoint` format:

```java
/**
 * Subscribes to ThingEvents from the TS topic and transforms them to data points.
 */
public class TimeseriesEventSubscriber {

    private static final String TS_TOPIC = "things.ts-events:";
    private final TimeseriesAdapter adapter;

    public void subscribe() {
        distributedSub.subscribeWithAck(
            Set.of(TS_TOPIC),
            getSelf(),
            this::handleThingEvent
        );
    }

    private void handleThingEvent(final ThingEvent<?> event) {
        // 1. Extract extra fields from header
        final JsonObject extraFields = JsonObject.of(
            event.getDittoHeaders().getOrDefault("ts-extra", "{}")
        );

        // 2. Transform to TimeseriesDataPoint
        final TimeseriesDataPoint dataPoint = TimeseriesDataPoint.of(
            event.getEntityId(),
            event.getResourcePath(),
            event.getTimestamp().orElseGet(Instant::now),
            extractValue(event),
            event.getRevision(),
            extractTags(extraFields),
            extractUnit(extraFields)
        );

        // 3. Buffer and write to adapter
        writeBuffer.add(dataPoint);
        scheduleFlushIfNeeded();
    }

    private Map<String, String> extractTags(final JsonObject extraFields) {
        return extraFields.getValue("tags")
            .filter(JsonValue::isObject)
            .map(JsonValue::asObject)
            .map(this::jsonObjectToStringMap)
            .orElse(Map.of());
    }

    @Nullable
    private String extractUnit(final JsonObject extraFields) {
        return extraFields.getValue("unit")
            .filter(JsonValue::isString)
            .map(JsonValue::asString)
            .orElse(null);
    }
}
```

### 5.6 Event Flow Summary

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Things Service                                │
│                                                                         │
│  1. ThingEvent persisted (event sourcing)                               │
│                    │                                                    │
│                    ▼                                                    │
│  2. Check WoT model: ditto:timeseries.ingest == "ALL"?                  │
│                    │                                                    │
│          ┌─────────┴───────┐                                            │
│          │ No              │ Yes                                        │
│          ▼                 ▼                                            │
│       (skip)     3. Resolve ditto:timeseries.tags placeholders          │
│                            │                                            │
│                            ▼                                            │
│                  4. Publish ThingEvent + extra fields                   │
│                            │                                            │
└────────────────────────────┼────────────────────────────────────────────┘
                             │
                             ▼
                  ┌─────────────────────┐
                  │ "things.ts-events:" │
                  │    pub/sub topic    │
                  └─────────────────────┘
                             │
                             ▼
┌────────────────────────────┼────────────────────────────────────────────┐
│                            │         Timeseries Service                 │
│                            ▼                                            │
│  5. Receive ThingEvent + extra fields                                   │
│                            │                                            │
│                            ▼                                            │
│  6. Transform to TimeseriesDataPoint                                    │
│                            │                                            │
│                            ▼                                            │
│  7. Write to TS adapter (batched)                                       │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Policy Integration

### 6.1 New Permission: READ_TS

Extend the existing permission model with `READ_TS`:

| Permission | Description                       |
|------------|-----------------------------------|
| `READ`     | Read current Thing state          |
| `WRITE`    | Modify Thing state                |
| `READ_TS`  | Read timeseries data for property |

**Configurable Permission Requirement:**

The permission required to access timeseries data is **configurable** at the timeseries service level (see [Section 9.1](#91-timeseries-service-configuration)). This allows operators to choose between:

| Configuration Value | Behavior                                                                                          |
|---------------------|---------------------------------------------------------------------------------------------------|
| `READ_TS` (default) | Users need explicit `READ_TS` permission to access timeseries data (fine-grained access control)  |
| `READ`              | Users with `READ` permission can also read timeseries data (simplified permission management)     |

**Use cases:**
- **`READ_TS` (default)**: Organizations requiring strict separation between live data access and historical data access. Useful when timeseries data is more sensitive or when different user roles need different access levels.
- **`READ`**: Organizations preferring simpler permission management where "if you can read the current value, you can also read its history." Reduces policy complexity for straightforward deployments.

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

### 6.3 Enforcement in timeseries

The timeseries service enforces access control using the **configured permission** (see [Section 9.1](#91-timeseries-service-configuration)). This defaults to `READ_TS` but can be changed to `READ` for simpler permission management.

```java
public class TimeseriesQueryHandler {

    private final String requiredPermission;  // Loaded from config: "READ_TS" or "READ"

    public TimeseriesQueryHandler(final TimeseriesConfig config) {
        this.requiredPermission = config.getEnforcement().getRequiredPermission();
    }

    public CompletionStage<TimeseriesResult> executeQuery(
            final TimeseriesQuery query,
            final DittoHeaders dittoHeaders) {

        // 1. Load policy enforcer (cached)
        return policyEnforcerProvider.getPolicyEnforcer(query.getThingId())
            .thenCompose(enforcer -> {
                // 2. Check configured permission for each requested property
                final AuthorizationContext authContext =
                    dittoHeaders.getAuthorizationContext();

                final List<JsonPointer> authorizedPaths = query.getPropertyPaths().stream()
                    .filter(path -> enforcer.hasPermission(
                        ResourceKey.newInstance("thing", toResourcePath(query, path)),
                        authContext,
                        requiredPermission))  // Uses configured permission (READ_TS or READ)
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
}
```

### 6.4 Policy Caching

timeseries caches policy enforcers similar to Things service:

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

#### Thing Attributes Timeseries (Multiple Attributes)

```
GET /api/2/timeseries/things/{thingId}/attributes
```

#### Thing Timeseries (Multiple Attributes + Feature Properties)

```
GET /api/2/timeseries/things/{thingId}
```

#### Batch Query (Multiple Attributes/Properties/Aggregations) for Thing

```
POST /api/2/timeseries/things/{thingId}
```

#### Batch Query (Multiple Properties/Aggregations) for Feature

```
POST /api/2/timeseries/things/{thingId}/features/{featureId}/query
```

#### Full Endpoint Overview

| Endpoint                                                                        | Description                       |
|---------------------------------------------------------------------------------|-----------------------------------|
| `GET /api/2/timeseries/things/{thingId}`                                        | All TS data for a Thing           |
| `GET /api/2/timeseries/things/{thingId}/features`                               | All TS data for all Features      |
| `GET /api/2/timeseries/things/{thingId}/features/{featureId}`                   | All TS data for a Feature         |
| `GET /api/2/timeseries/things/{thingId}/features/{featureId}/properties`        | All TS-enabled properties         |
| `GET /api/2/timeseries/things/{thingId}/features/{featureId}/properties/{path}` | Single property TS                |
| `GET /api/2/timeseries/things/{thingId}/attributes`                             | All TS data for all attributes    |
| `GET /api/2/timeseries/things/{thingId}/attributes/{path}`                      | Single Attribute TS               |
| `POST /api/2/timeseries/things/{thingId}`                                       | Batch query for a Thing           |
| `POST /api/2/timeseries/things/{thingId}/features/{featureId}/query`            | Batch query for a Feature         |
| `POST /api/2/timeseries/query`                                                  | **Cross-thing aggregation query** |

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
  "paths": [
    "/features/environment/properties/temperature",
    "/features/environment/properties/humidity",
    "/attributes/batteryExchangeDate"
  ],
  "from": "now-24h",
  "to": "now",
  "step": "1h",
  "agg": "avg",
  "groupBy": ["attributes/floor"]
}
```

| Field     | Type         | Required | Description                                                    |
|-----------|--------------|----------|----------------------------------------------------------------|
| `filter`  | string (RQL) | No       | RQL filter expression on declared tags                         |
| `paths`   | array        | Yes      | JSON pointers to TS-enabled properties (relative to Thing root)|
| `from`    | string       | No       | Start time (ISO 8601 or relative)                              |
| `to`      | string       | No       | End time (default: `now`)                                      |
| `step`    | string       | No       | Downsampling interval                                          |
| `agg`     | string       | No       | Aggregation function                                           |
| `groupBy` | array        | No       | Tag keys to group by (use full paths)                          |

**Supported RQL operators**:

| Operator | Example                                  | Description                     |
|----------|------------------------------------------|---------------------------------|
| `eq`     | `eq(attributes/building,'A')`            | Equals                          |
| `ne`     | `ne(attributes/floor,'0')`               | Not equals                      |
| `gt`     | `gt(attributes/floor,2)`                 | Greater than                    |
| `ge`     | `ge(attributes/floor,2)`                 | Greater than or equal           |
| `lt`     | `lt(attributes/floor,10)`                | Less than                       |
| `le`     | `le(attributes/floor,10)`                | Less than or equal              |
| `in`     | `in(attributes/floor,'1','2','3')`       | In list                         |
| `like`   | `like(attributes/building,'Building-*')` | Pattern match (`*` = any chars) |
| `and`    | `and(expr1,expr2,...)`                   | Logical AND                     |
| `or`     | `or(expr1,expr2)`                        | Logical OR                      |
| `not`    | `not(eq(sensorType,'test'))`             | Logical NOT                     |

**Response**:
```json
{
  "query": {
    "filter": "and(eq(attributes/building,'A'),eq(sensorType,'environmental'))",
    "paths": ["/features/environment/properties/temperature"],
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
        "/features/environment/properties/temperature": {
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
        "/features/environment/properties/temperature": {
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
- Filters only work on **declared tags** (from `ditto:timeseries.tags` in WoT model)
- Use full Thing paths in RQL: `attributes/building`, not just `building`
- RQL is translated to TS-database-specific query syntax by the adapter
- Query goes directly to TS database - no Ditto search round-trip
- `thingCount` shows how many Things contributed to each group

**Authorization (two-layer enforcement via PolicyEnforcer)**:

Cross-thing queries enforce authorization at two levels using the locally cached PolicyEnforcer. This is **application-level enforcement**, not database-level — the timeseries service validates permissions before executing the query.

| Layer | Permission | Scope | Enforcement |
|-------|------------|-------|-------------|
| **Filter/groupBy fields** | `READ` | Fields referenced in RQL `filter` and `groupBy` | Application-level (PolicyEnforcer check before query) |
| **Timeseries data** | Configured (`READ_TS` or `READ`) | Properties in `paths` array | Application-level (PolicyEnforcer check before query) |

**Enforcement flow**:
1. User submits cross-thing query with `filter`, `groupBy`, and `paths`
2. Timeseries service extracts all field references from RQL filter and groupBy
3. For each referenced field, check `READ` permission via cached PolicyEnforcer
4. For each path in `paths`, check configured permission (`READ_TS` or `READ`) via cached PolicyEnforcer
5. If any permission check fails, return `403 Forbidden` with details of unauthorized fields
6. If all checks pass, execute query on TS database and return aggregated results

**Important**: Unlike Ditto's Things-Search service which performs MongoDB-level enforcement by storing policy information in the search index, the timeseries service uses application-level enforcement. This is acceptable because:
- Cross-thing queries return **aggregated** data, not individual Thing data
- The aggregation result doesn't expose which specific Things contributed
- Filter fields are limited to **declared tags** which are explicitly configured in WoT models

**Example**: A user querying with `filter: eq(attributes/building,'A')` and `paths: ["/features/env/properties/temperature"]`:
1. Must have `READ` permission on `attributes/building` to filter by it
2. Must have the configured permission (`READ_TS` by default) on `/features/env/properties/temperature` to retrieve timeseries data
3. Query is rejected with 403 if either permission is missing

**RQL Filter Translation**:

The timeseries service translates RQL to TS-database-specific queries:

| RQL                              | MongoDB MQL                                 | IoTDB SQL                       | TimescaleDB SQL                   |
|----------------------------------|---------------------------------------------|---------------------------------|-----------------------------------|
| `eq(attributes/building,'A')`    | `{"meta.tags.attributes/building": "A"}`    | `attributes_building = 'A'`     | `"attributes/building" = 'A'`     |
| `gt(attributes/floor,2)`         | `{"meta.tags.attributes/floor": {$gt: 2}}`  | `attributes_floor > 2`          | `"attributes/floor" > 2`          |
| `like(attributes/building,'A*')` | `{"meta.tags.attributes/building": /^A.*/}` | `attributes_building LIKE 'A%'` | `"attributes/building" LIKE 'A%'` |
| `and(eq(a,'x'),gt(b,5))`         | `{$and: [{...}, {...}]}`                    | `a = 'x' AND b > 5`             | `a = 'x' AND b > 5`               |

**Example use cases**:

| Use Case                         | RQL Filter                                                          | GroupBy                   |
|----------------------------------|---------------------------------------------------------------------|---------------------------|
| Avg temp per floor in Building A | `eq(attributes/building,'A')`                                       | `["attributes/floor"]`    |
| Compare buildings                | *(empty)*                                                           | `["attributes/building"]` |
| Floors 2+ in Building A          | `and(eq(attributes/building,'A'),ge(attributes/floor,2))`           | `["attributes/floor"]`    |
| Buildings A or B                 | `or(eq(attributes/building,'A'),eq(attributes/building,'B'))`       | `["attributes/building"]` |
| Exclude test sensors             | `not(eq(sensorType,'test'))`                                        | `["attributes/building"]` |
| Pattern match                    | `like(attributes/building,'Building-*')`                            | `["attributes/building"]` |
| Multiple floors                  | `and(eq(attributes/building,'A'),in(attributes/floor,'1','2','3'))` | `["attributes/floor"]`    |

### 7.3 Query Parameters

| Parameter    | Type   | Required | Description                         | Example                                                    |
|--------------|--------|----------|-------------------------------------|------------------------------------------------------------|
| `from`       | string | No       | Start time (ISO 8601 or relative)   | `2026-01-14T00:00:00Z`, `now-24h`                          |
| `to`         | string | No       | End time (default: `now`)           | `2026-01-15T00:00:00Z`, `now`                              |
| `step`       | string | No       | Downsampling interval               | `1m`, `5m`, `1h`, `1d`                                     |
| `agg`        | string | No       | Aggregation function                | `avg`, `min`, `max`, `sum`, `count`, `first`, `last`, `derivative`       |
| `fill`       | string | No       | Gap filling strategy                | `null`, `previous`, `linear`, `zero`                       |
| `limit`      | int    | No       | Max data points (raw queries)       | `1000`                                                     |
| `tz`         | string | No       | Timezone for step alignment         | `Europe/Berlin`, `UTC`                                     |
| `paths`      | string | No       | Comma-separated paths filter        | `/features/env/properties/temperature,/attributes/battery` |
| `timeFormat` | string | No       | Timestamp format in response        | `iso` (default), `ms`                                      |

#### Timestamp Format (`timeFormat`)

Controls how timestamps are represented in the response `data` array:

| Value | Format                      | Example                          | Description                                |
|-------|-----------------------------|---------------------------------|--------------------------------------------|
| `iso` | ISO 8601 string (default)   | `"2026-01-14T00:00:00Z"`        | Human-readable, verbose, standard format   |
| `ms`  | Milliseconds since epoch    | `1736812800000`                 | Compact numeric format, easier to parse    |

**Example with `timeFormat=iso` (default):**
```json
{
  "data": [
    {"t": "2026-01-14T00:00:00Z", "v": 22.3},
    {"t": "2026-01-14T01:00:00Z", "v": 22.1}
  ]
}
```

**Example with `timeFormat=ms`:**
```json
{
  "data": [
    {"t": 1736812800000, "v": 22.3},
    {"t": 1736816400000, "v": 22.1}
  ]
}
```

The milliseconds format is recommended for:
- High-frequency data with many data points (reduced payload size)
- Clients that process timestamps programmatically
- Integration with charting libraries that expect numeric timestamps

#### Aggregation Functions (`agg`)

| Function     | Description                                              | Use Case                                      |
|--------------|----------------------------------------------------------|-----------------------------------------------|
| `avg`        | Average of values in each time bucket                    | Smoothing noisy sensor data                   |
| `min`        | Minimum value in each time bucket                        | Finding low points                            |
| `max`        | Maximum value in each time bucket                        | Finding peaks                                 |
| `sum`        | Sum of values in each time bucket                        | Cumulative metrics (energy consumption)       |
| `count`      | Number of data points in each time bucket                | Data density analysis                         |
| `first`      | First value in each time bucket                          | Point-in-time snapshots                       |
| `last`       | Last value in each time bucket                           | Most recent value per bucket                  |
| `derivative` | Rate of change between consecutive values (per second)   | Flow rate from cumulative volume              |

##### Derivative Aggregation

The `derivative` aggregation calculates the **rate of change** between consecutive data points, returning the change per second. This is essential for converting cumulative measurements (like total volume) into rate measurements (like flow rate).

**Formula:**
```
derivative = (value[n] - value[n-1]) / (time[n] - time[n-1])
```

Where time difference is in seconds.

**Example: Converting cumulative volume to flow rate**

Raw data (cumulative water volume in m³):
```json
{"t": "2026-01-14T10:00:00Z", "v": 1000.0}
{"t": "2026-01-14T11:00:00Z", "v": 1005.0}
{"t": "2026-01-14T12:00:00Z", "v": 1012.0}
```

Query:
```
GET /api/2/timeseries/things/{id}/features/meter/properties/totalVolume?agg=derivative&from=...&to=...
```

Result (flow rate in m³/s, multiply by 3600 for m³/h):
```json
{
  "data": [
    {"t": "2026-01-14T11:00:00Z", "v": 0.00139},
    {"t": "2026-01-14T12:00:00Z", "v": 0.00194}
  ]
}
```

Calculation:
- 11:00: `(1005 - 1000) / 3600 = 0.00139 m³/s` (≈ 5 m³/h)
- 12:00: `(1012 - 1005) / 3600 = 0.00194 m³/s` (≈ 7 m³/h)

**Note:** The first data point has no predecessor, so derivative results have one fewer point than raw data.

**Combining with `step`:**

When used with a `step` parameter, `derivative` first downsamples using the `last` value per bucket, then calculates derivatives between buckets:

```
GET ...?agg=derivative&step=1h
```

This gives hourly rate-of-change values, useful for calculating hourly flow rates from cumulative meter readings.

### 7.4 Response Format

#### Single Path Response

```json
{
  "thingId": "org.eclipse.ditto:sensor-1",
  "path": "/features/environment/properties/temperature",
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

#### Multiple Paths Response

```json
{
  "thingId": "org.eclipse.ditto:sensor-1",
  "query": {
    "from": "2026-01-14T00:00:00Z",
    "to": "2026-01-15T00:00:00Z",
    "step": "1h",
    "aggregation": "avg"
  },
  "series": [
    {
      "path": "/features/environment/properties/temperature",
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
      "path": "/attributes/batteryExchangeDate",
      "result": {
        "count": 2,
        "dataType": "string"
      },
      "data": [
        {"t": "2026-01-14T00:00:00Z", "v": "2025-06-15"},
        {"t": "2026-01-14T12:00:00Z", "v": "2026-01-14"}
      ]
    }
  ]
}
```

#### Batch Query Request/Response

**Request**:
```
POST /api/2/timeseries/things/org.eclipse.ditto:sensor-1
```

```json
{
  "paths": [
    "/features/environment/properties/temperature",
    "/features/environment/properties/humidity"
  ],
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
  "query": {
    "from": "2026-01-14T10:30:00Z",
    "to": "2026-01-15T10:30:00Z",
    "step": "1h"
  },
  "results": [
    {
      "path": "/features/environment/properties/temperature",
      "aggregation": "avg",
      "result": {"count": 24, "unit": "cel", "dataType": "number"},
      "data": [{"t": "2026-01-14T11:00:00Z", "v": 22.3}, ...]
    },
    {
      "path": "/features/environment/properties/temperature",
      "aggregation": "max",
      "result": {"count": 24, "unit": "cel", "dataType": "number"},
      "data": [{"t": "2026-01-14T11:00:00Z", "v": 24.1}, ...]
    },
    {
      "path": "/features/environment/properties/humidity",
      "aggregation": "avg",
      "result": {"count": 24, "unit": "percent", "dataType": "number"},
      "data": [{"t": "2026-01-14T11:00:00Z", "v": 65.2}, ...]
    },
    {
      "path": "/features/environment/properties/humidity",
      "aggregation": "max",
      "result": {"count": 24, "unit": "percent", "dataType": "number"},
      "data": [{"t": "2026-01-14T11:00:00Z", "v": 72.0}, ...]
    }
  ]
}
```

### 7.5 Error Responses

| Status | Error Code                         | Description                |
|--------|------------------------------------|----------------------------|
| 400    | `timeseries:query.invalid`         | Invalid query parameters   |
| 403    | `timeseries:timeseries.notallowed` | No READ_TS permission      |
| 404    | `timeseries:thing.notfound`        | Thing does not exist       |
| 503    | `timeseries:backend.unavailable`   | TS database unavailable    |

**Empty results**: When no timeseries data exists in the requested time range, the API returns **HTTP 200** with an empty `data` array (not 404). This is consistent with Ditto's search API pattern where empty search results return 200 with an empty `items` array.

```json
{
  "thingId": "org.eclipse.ditto:sensor-1",
  "path": "/features/environment/properties/temperature",
  "query": {
    "from": "2026-01-14T00:00:00Z",
    "to": "2026-01-15T00:00:00Z"
  },
  "result": {
    "count": 0,
    "unit": "cel",
    "dataType": "number"
  },
  "data": []
}
```

### 7.6 Ditto Protocol Topic Paths

The timeseries API integrates with the [Ditto Protocol](https://www.eclipse.dev/ditto/protocol-specification.html) for access via WebSocket and Connectivity channels. The topic path format follows Ditto conventions.

#### Topic Path Structure

Timeseries uses the `timeseries` criterion under the `things` group:

```
<namespace>/<thingName>/things/<channel>/timeseries/<action>
```

| Component       | Description                                    | Values                                      |
|-----------------|------------------------------------------------|---------------------------------------------|
| `namespace`     | Thing namespace                                | e.g., `org.eclipse.ditto`                   |
| `thingName`     | Thing name (or `_` for cross-thing queries)    | e.g., `sensor-1` or `_`                     |
| `things`        | Group (always `things` for timeseries)         | `things`                                    |
| `channel`       | Channel (timeseries uses twin channel)         | `twin`                                      |
| `timeseries`    | Criterion                                      | `timeseries`                                |
| `action`        | Command or event action                        | See tables below                            |

#### Single-Thing Timeseries Query

For querying timeseries data of a specific Thing, use direct request-response pattern:

**Command (Client → Ditto):**

| Action       | Topic                                                        | Description                        |
|--------------|--------------------------------------------------------------|------------------------------------|
| `retrieve`   | `<ns>/<name>/things/twin/timeseries/retrieve`                | Query timeseries data              |

**Response (Ditto → Client):**

The response uses the same topic as the command, with added `status` field:

| Field      | Value                                                                   |
|------------|-------------------------------------------------------------------------|
| **topic**  | `<ns>/<name>/things/twin/timeseries/retrieve`                           |
| **status** | `200` for success, error codes as per Section 7.5                       |
| **value**  | Timeseries query result                                                 |

**Example - Retrieve Timeseries Command:**

```json
{
  "topic": "org.eclipse.ditto/sensor-1/things/twin/timeseries/retrieve",
  "path": "/features/environment/properties/temperature",
  "headers": {
    "correlation-id": "query-123"
  },
  "value": {
    "from": "2026-01-14T00:00:00Z",
    "to": "2026-01-15T00:00:00Z",
    "step": "1h",
    "aggregation": "avg",
    "timeFormat": "ms"
  }
}
```

**Example - Retrieve Timeseries Response:**

```json
{
  "topic": "org.eclipse.ditto/sensor-1/things/twin/timeseries/retrieve",
  "path": "/features/environment/properties/temperature",
  "headers": {
    "correlation-id": "query-123"
  },
  "status": 200,
  "value": {
    "result": {
      "count": 24,
      "unit": "cel",
      "dataType": "number"
    },
    "data": [
      {"t": 1736812800000, "v": 22.3},
      {"t": 1736816400000, "v": 22.1}
    ]
  }
}
```

#### Cross-Thing Aggregation Query

For cross-thing aggregation queries, use the `query` action with `_/_` placeholder since no specific Thing is targeted:

**Command (Client → Ditto):**

| Action   | Topic                                | Description                        |
|----------|--------------------------------------|------------------------------------|
| `query`  | `_/_/things/twin/timeseries/query`   | Cross-thing aggregation query      |

**Response (Ditto → Client):**

The response uses the same topic as the command, with added `status` field:

| Field      | Value                                                                   |
|------------|-------------------------------------------------------------------------|
| **topic**  | `_/_/things/twin/timeseries/query`                                      |
| **status** | `200` for success, error codes as per Section 7.5                       |
| **value**  | Aggregation query result                                                |

**Example - Query Command:**

```json
{
  "topic": "_/_/things/twin/timeseries/query",
  "path": "/",
  "headers": {
    "correlation-id": "agg-query-456"
  },
  "value": {
    "filter": "eq(attributes/building,'A')",
    "paths": ["/features/environment/properties/temperature"],
    "from": "2026-01-14T00:00:00Z",
    "to": "2026-01-15T00:00:00Z",
    "step": "1h",
    "aggregation": "avg",
    "groupBy": ["attributes/floor"]
  }
}
```

**Example - Query Response:**

```json
{
  "topic": "_/_/things/twin/timeseries/query",
  "path": "/",
  "headers": {
    "correlation-id": "agg-query-456"
  },
  "status": 200,
  "value": {
    "query": {
      "filter": "eq(attributes/building,'A')",
      "paths": ["/features/environment/properties/temperature"],
      "from": "2026-01-14T00:00:00Z",
      "to": "2026-01-15T00:00:00Z",
      "step": "1h",
      "aggregation": "avg",
      "groupBy": ["attributes/floor"]
    },
    "groups": [
      {
        "tags": {"attributes/floor": "1"},
        "thingCount": 5,
        "series": {
          "/features/environment/properties/temperature": {
            "unit": "cel",
            "data": [
              {"t": "2026-01-14T00:00:00Z", "v": 22.3},
              {"t": "2026-01-14T01:00:00Z", "v": 22.1}
            ]
          }
        }
      }
    ]
  }
}
```

> **Note**: Streaming support for large result sets via reactive-streams pattern (similar to Things-Search) may be added in a future version if needed.

#### Path Field Semantics

The `path` field in timeseries protocol messages specifies which property's timeseries is being queried:

| Path                                          | Scope                                      |
|-----------------------------------------------|--------------------------------------------|
| `/`                                           | All TS-enabled properties of the Thing     |
| `/features`                                   | All TS-enabled feature properties          |
| `/features/{featureId}`                       | All TS-enabled properties of a feature     |
| `/features/{featureId}/properties/{property}` | Single feature property                    |
| `/attributes`                                 | All TS-enabled attributes                  |
| `/attributes/{attribute}`                     | Single attribute                           |

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
/**
 * A single timeseries data point for ingestion.
 * The path follows Ditto Protocol format (e.g., /features/env/properties/temp or /attributes/battery).
 */
public record TimeseriesDataPoint(
    ThingId thingId,
    JsonPointer path,           // Full path as in Ditto Protocol
    Instant timestamp,
    JsonValue value,
    long revision,
    Map<String, String> tags,
    @Nullable String unit       // Unit from WoT property (semantic prefix stripped)
) {}

/** Single-thing query. */
public record TimeseriesQuery(
    ThingId thingId,
    List<JsonPointer> paths,    // Full paths (e.g., /features/env/properties/temp)
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
    List<JsonPointer> paths,            // Full paths to query
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
    JsonPointer path,           // Full path as in Ditto Protocol
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

| Database                 | Module                | Status      | Notes                                                     |
|--------------------------|-----------------------|-------------|-----------------------------------------------------------|
| **MongoDB Time Series**  | `timeseries-mongodb`  | **Default** | Uses existing Ditto MongoDB, no additional infrastructure |

#### Custom Adapter Examples

Organizations can implement the `TimeseriesAdapter` interface to integrate other timeseries databases. Example implementations that could be developed:

| Database     | Potential Module       | Notes                                        |
|--------------|------------------------|----------------------------------------------|
| Apache IoTDB | `timeseries-iotdb`     | Native Java client, tree-based schema        |
| TimescaleDB  | `timeseries-timescale` | JDBC, PostgreSQL extension                   |
| InfluxDB 2.x | `timeseries-influx`    | HTTP API, Flux queries                       |

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
    "path": "/features/environment/properties/temperature",  // Full Ditto Protocol path
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

| Operation                 | MongoDB Support | Implementation                           |
|---------------------------|-----------------|------------------------------------------|
| avg, min, max, sum, count | ✅ Native        | `$avg`, `$min`, `$max`, `$sum`, `$count` |
| first, last               | ✅ Native        | `$first`, `$last`                        |
| derivative                | ✅ Native        | `$setWindowFields` with `$shift`         |
| Time bucketing            | ✅ Native        | `$dateTrunc` with `binSize` and `unit`   |
| Gap filling               | ✅ Native        | `$densify` + `$fill`                     |
| Window functions          | ✅ Native        | `$setWindowFields`                       |
| Tag filtering             | ✅ Native        | `$match` on `meta.tags.*`                |
| Retention                 | ✅ TTL Index     | `expireAfterSeconds` on collection       |

**Limitations:**

| Limitation                     | Impact                       | Mitigation                         |
|--------------------------------|------------------------------|------------------------------------|
| No change streams              | Cannot watch TS data changes | Use Thing events instead           |
| Updates restricted             | Can only update `metaField`  | TS data is append-only anyway      |
| No `distinct` command          | Use `$group` instead         | Already using aggregation pipeline |
| No transactions                | Cannot write in transactions | Acceptable for TS ingestion        |
| Performance vs dedicated TS DB | ~5x slower than InfluxDB     | Sufficient for most IoT workloads  |

**When to Choose MongoDB Time Series:**

| Scenario                               | Recommendation         |
|----------------------------------------|------------------------|
| Simple deployment, moderate throughput | ✅ MongoDB TS           |
| Existing MongoDB expertise             | ✅ MongoDB TS           |
| Very high write throughput (>100k/sec) | ❌ Use dedicated TS DB  |
| Complex analytical queries             | ❌ Consider TimescaleDB |
| Minimal operational overhead priority  | ✅ MongoDB TS           |

### 8.4 Schema Mapping

**Ditto Concept → TS Database Concept**:

| Ditto     | MongoDB TS        | IoTDB           | TimescaleDB             |
|-----------|-------------------|-----------------|-------------------------|
| Namespace | Collection prefix | Storage Group   | Schema                  |
| ThingId   | `meta.thingId`    | Device          | Table prefix            |
| Path      | `meta.path`       | Entity path     | Table/column derivation |
| Value     | `value` field     | Data point      | Row value               |
| Tags      | `meta.tags.*`     | Tags/Attributes | Additional columns      |
| Retention | TTL index         | TTL config      | Retention policy        |

**IoTDB Schema Example**:
```
root.{namespace}.{thingName}.{path}

root.org_eclipse_ditto.sensor_1.features.environment.properties.temperature
root.org_eclipse_ditto.sensor_1.features.environment.properties.humidity
root.org_eclipse_ditto.sensor_1.attributes.batteryExchangeDate
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
    "path": "/features/environment/properties/temperature",
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

| Feature                              | MongoDB TS (Default)   | IoTDB             | TimescaleDB             | InfluxDB            |
|--------------------------------------|------------------------|-------------------|-------------------------|---------------------|
| **Deployment**                       |
| Additional infrastructure            | ❌ None (uses existing) | ✅ Required        | ✅ Required              | ✅ Required          |
| Operational complexity               | Low                    | Medium            | Medium                  | Medium              |
| **Query Capabilities**               |
| Aggregations (avg/min/max/sum/count) | ✅                      | ✅                 | ✅                       | ✅                   |
| first/last                           | ✅                      | ✅                 | ✅                       | ✅                   |
| derivative (rate of change)          | ✅ `$setWindowFields`   | ✅ `DIFFERENCE`    | ✅ Window functions      | ✅ `derivative()`    |
| Time bucketing                       | ✅ `$dateTrunc`         | ✅ `GROUP BY TIME` | ✅ `time_bucket`         | ✅ `aggregateWindow` |
| Gap filling                          | ✅ `$densify`+`$fill`   | ✅ `FILL`          | ✅ `time_bucket_gapfill` | ✅ `fill()`          |
| Window functions                     | ✅ `$setWindowFields`   | ✅                 | ✅                       | ✅                   |
| Complex joins                        | ✅ `$lookup`            | Limited           | ✅ Full SQL              | Limited             |
| **Performance**                      |
| Write throughput                     | Medium                 | High              | High                    | Very High           |
| Simple query speed                   | Medium                 | High              | High                    | Very High           |
| Complex query speed                  | Medium                 | Medium            | Very High               | Medium              |
| Compression                          | Good                   | Excellent         | Good                    | Excellent           |
| **RQL Translation**                  |
| Translation complexity               | Medium (MQL)           | Medium (SQL-like) | Low (SQL)               | Medium (Flux)       |
| **Integration**                      |
| Java client                          | ✅ MongoDB Driver       | ✅ Native          | ✅ JDBC                  | ✅ HTTP/Client       |
| Authentication                       | Shared with Ditto      | Separate          | Separate                | Separate            |

**When to Consider a Custom Adapter:**

| Scenario                                      | Recommendation                            |
|-----------------------------------------------|-------------------------------------------|
| **Getting started / PoC**                     | Use default MongoDB TS adapter            |
| **Small-medium deployment** (<10k writes/sec) | Use default MongoDB TS adapter            |
| **High-volume IoT** (>100k writes/sec)        | Consider custom IoTDB or InfluxDB adapter |
| **Complex analytics requirements**            | Consider custom TimescaleDB adapter       |
| **Edge deployment** (resource constrained)    | Use default MongoDB TS adapter            |

**Note**: IoTDB, TimescaleDB, and InfluxDB adapters require custom implementation of the `TimeseriesAdapter` interface. 
The comparison above is provided as guidance for organizations evaluating custom adapter development.

---

## 9. Configuration

### 9.1 timeseries Service Configuration

**File**: `timeseries/service/src/main/resources/timeseries.conf`

```hocon
ditto {
  timeseries {
    # Enforcement configuration
    enforcement {
      # Permission required to read timeseries data.
      # Options:
      #   - "READ_TS": Users need explicit READ_TS permission (fine-grained control)
      #   - "READ": Users with READ permission can also read timeseries (simplified management)
      required-permission = "READ_TS"
      required-permission = ${?TIMESERIES_REQUIRED_PERMISSION}
    }

    # Timeseries adapter configuration
    adapter {
      # Which adapter to use. Default: "mongodb"
      # Custom adapters can register additional types via SPI
      type = "mongodb"
      type = ${?TIMESERIES_ADAPTER_TYPE}

      # MongoDB Time Series adapter configuration (default adapter)
      mongodb {
        # Use Ditto's existing MongoDB URI (recommended)
        # If not set, uses ditto.mongodb.uri from common config
        uri = ${?TIMESERIES_MONGODB_URI}

        # Database name for timeseries collections
        database = "ditto_ts"
        database = ${?TIMESERIES_MONGODB_DATABASE}

        # Collection name prefix (namespace appended)
        collection-prefix = "ts_"
        collection-prefix = ${?TIMESERIES_MONGODB_COLLECTION_PREFIX}

        # Time series granularity: "seconds", "minutes", "hours"
        # Use "seconds" for high-frequency data, "hours" for daily metrics
        granularity = "seconds"
        granularity = ${?TIMESERIES_MONGODB_GRANULARITY}

        # Write concern for ingestion
        write-concern = "majority"
        write-concern = ${?TIMESERIES_MONGODB_WRITE_CONCERN}

        # Data retention (TTL) - applied to MongoDB Time Series collections
        # ISO 8601 duration format
        retention = P90D
        retention = ${?TIMESERIES_MONGODB_RETENTION}

        # Minimum sampling resolution - data points arriving faster may be aggregated
        # Use "PT0S" to accept all data points without aggregation
        # ISO 8601 duration format
        resolution = PT0S
        resolution = ${?TIMESERIES_MONGODB_RESOLUTION}
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
      batch-size = ${?TIMESERIES_INGESTION_BATCH_SIZE}

      # Max time to buffer before flush
      flush-interval = 5s
      flush-interval = ${?TIMESERIES_INGESTION_FLUSH_INTERVAL}

      # Parallelism for write operations
      parallelism = 4
      parallelism = ${?TIMESERIES_INGESTION_PARALLELISM}
    }

    # Query settings
    query {
      # Default time range if 'from' not specified
      default-lookback = 24h
      default-lookback = ${?TIMESERIES_QUERY_DEFAULT_LOOKBACK}

      # Maximum time range for a single query
      max-time-range = 365d
      max-time-range = ${?TIMESERIES_QUERY_MAX_TIME_RANGE}

      # Maximum data points per query
      max-data-points = 10000
      max-data-points = ${?TIMESERIES_QUERY_MAX_DATA_POINTS}

      # Query timeout
      timeout = 30s
      timeout = ${?TIMESERIES_QUERY_TIMEOUT}
    }

    # Policy enforcer cache
    policy-cache {
      maximum-size = 10000
      maximum-size = ${?TIMESERIES_POLICY_CACHE_MAX_SIZE}

      expire-after-write = 5m
      expire-after-write = ${?TIMESERIES_POLICY_CACHE_EXPIRE}
    }
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
      # Enable/disable publishing of ThingEvents to things.ts-events: topic
      publish-enabled = true
      publish-enabled = ${?THINGS_TIMESERIES_PUBLISH_ENABLED}
    }
  }
}
```

### 9.3 Helm Configuration

**File**: `deployment/helm/ditto/values.yaml` (additions)

```yaml
timeseries:
  enabled: true
  replicaCount: 1

  image:
    repository: eclipse/ditto-timeseries
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
    enforcement:
      # Permission required to read timeseries data.
      # "READ_TS" (default): Users need explicit READ_TS permission
      # "READ": Users with READ permission can also read timeseries data
      requiredPermission: "READ_TS"

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
      retention: "P90D"       # TTL for MongoDB Time Series collections
      resolution: "PT0S"      # Minimum sampling resolution (0 = no limit)

    # Custom adapter configurations would be added here
    # See documentation for implementing custom adapters

    ingestion:
      batchSize: 100
      flushInterval: "5s"

    query:
      defaultLookback: "24h"
      maxTimeRange: "365d"
      maxDataPoints: 10000

# Add to things service config
things:
  config:
    timeseries:
      publishEnabled: true
```

---

## 10. Module Structure

```
ditto/
├── timeseries/
│   ├── model/                    # TS-specific signals, models (Java 8)
│   │   └── src/main/java/org/eclipse/ditto/timeseries/model/
│   │       ├── signals/
│   │       │   ├── commands/     # RetrieveTimeseries, etc.
│   │       │   └── events/       # TimeseriesDataIngested, etc.
│   │       ├── TimeseriesDataPoint.java
│   │       ├── TimeseriesQuery.java
│   │       └── TimeseriesQueryResult.java
│   │
│   ├── api/                      # Adapter interfaces (Java 8)
│   │   └── src/main/java/org/eclipse/ditto/timeseries/api/
│   │       ├── TimeseriesAdapter.java
│   │       ├── TimeseriesAdapterConfig.java
│   │       └── TimeseriesAdapterFactory.java
│   │
│   ├── service/                  # Service implementation (Java 21)
│   │   └── src/main/java/org/eclipse/ditto/timeseries/service/
│   │       ├── starter/
│   │       │   └── TimeseriesService.java
│   │       ├── actors/
│   │       │   ├── TimeseriesRootActor.java
│   │       │   ├── TimeseriesQueryActor.java
│   │       │   └── TimeseriesIngestionActor.java
│   │       ├── enforcement/
│   │       │   └── TimeseriesPolicyEnforcerProvider.java
│   │       └── routes/
│   │           └── TimeseriesRoute.java
│   │
│   └── mongodb/                  # MongoDB TS adapter (Java 21) - DEFAULT
│       └── src/main/java/org/eclipse/ditto/timeseries/mongodb/
│           ├── MongoDbTimeseriesAdapter.java
│           └── MongoDbRqlTranslator.java
│
│   # Custom adapters (e.g., IoTDB, TimescaleDB) would be implemented
│   # externally using the timeseries/api interfaces
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
- timeseries service skeleton with Pekko cluster integration
- **MongoDB Time Series adapter** (default, uses existing infrastructure)
- Basic ingestion via pub/sub
- Simple query API (single property, time range, no aggregation)
- READ_TS permission in policy model

**Deliverables**:
- `timeseries/model`, `timeseries/api`, `timeseries/service` modules
- `timeseries/mongodb` adapter (default)
- WoT extension: `ditto:timeseries` with `ingest` only (no `tags` in MVP)
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
- Full WoT extension support (`ditto:timeseries` with `ingest` and `tags`)
- Connectivity integration for TS queries via messages
- Helm chart updates

**Deliverables**:
- Complete WoT ontology support
- Message-based query support
- Production-ready Helm configuration

---

## 12. Open Questions

### Resolved

1. ~~**Attribute timeseries**: Should Thing-level attributes also support TS tracking, or only Feature properties?~~
   **Decision**: Yes, attributes are supported. The HTTP API already includes attribute endpoints (`/api/2/timeseries/things/{thingId}/attributes/{path}`). WoT ThingModels can declare `ditto:timeseries` on Thing-level properties (attributes), not just Feature properties.

4. ~~**Cross-thing queries**: Should timeseries support queries across multiple Things?~~
   **Decision**: Yes, via tag denormalization. The `POST /api/2/timeseries/query` endpoint supports cross-thing aggregation using tags stored natively in the TS database. See Section 7.2.

6. ~~**TS database management**: Who creates/manages the TS database itself?~~
   **Decision**: Out of scope for Ditto. The TS database (MongoDB, IoTDB, TimescaleDB, etc.) must be provisioned and managed externally. Ditto's timeseries only connects to an existing database. This follows the same pattern as Ditto's MongoDB requirement.

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
