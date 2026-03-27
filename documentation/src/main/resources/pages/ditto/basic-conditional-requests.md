---
title: Conditional requests on things
keywords: request, conditional, condition, rql
tags: [protocol, http, rql]
permalink: basic-conditional-requests.html
---

Conditional requests let you apply commands to Things only when specific conditions about the Thing's current state are met.

{% include callout.html content="**TL;DR**: Use the `condition` header with an RQL expression to make updates conditional on the Thing's current state. You can also use `If-Match`/`If-None-Match` headers for ETag-based conditions, and `live-channel-condition` for automatic twin/live switching." type="primary" %}

## Overview

Ditto supports [conditional requests as defined in RFC-7232](https://tools.ietf.org/html/rfc7232) using `If-Match` and `If-None-Match` headers. Additionally, the `condition` header lets you specify conditions based on the current state of the persisted twin.

You can combine both header types in one request. When you do, Ditto evaluates the ETag header first.

## Defining conditions

You define conditions using [RQL expressions](basic-rql.html). A condition specifies that Ditto should apply the request only if the expression evaluates to `true` against the current twin state.

For example, to update an attribute only if the current value is not already 42:

```text
PUT /api/2/things/org.eclipse.ditto:foo1/attributes/value?condition=ne(attributes/value,42)
42
```

You can reference any field in the Thing to build a condition. This is useful for timestamp-based updates where you only want to apply a change if the incoming value is newer than the stored one.

- If the condition is met, Ditto updates the Thing and emits an event.
- If the condition is not met, Ditto does not modify the Thing and emits no [event/change notification](basic-changenotifications.html).

Conditional requests work with HTTP API, WebSocket, Ditto Protocol, and the Ditto Java Client.

### Permissions for conditions

You need READ permission on the resource referenced in the condition. Otherwise, the request fails.

## Examples

The following examples assume this Thing state:

```json
{
  "thingId": "org.eclipse.ditto:fancy-thing",
  "policyId": "org.eclipse.ditto:fancy-thing",
  "attributes": {
    "location": "kitchen"
  },
  "features": {
    "temperature": {
      "properties": {
        "value": 23.42,
        "unit": "Celcius",
        "lastModified": "2021-08-10T15:07:20.398Z"
      }
    }
  }
}
```

The goal: update the temperature value only if the incoming value is newer than the stored one, using the `lastModified` field.

### HTTP API

Specify the condition as a query parameter:

```bash
curl -X PUT -H 'Content-Type: application/json' /api/2/things/org.eclipse.ditto:fancy-thing/features/temperature/properties/value?condition=gt(features/temperature/properties/lastModified,'2021-08-10T15:10:02.592Z') -d 19.26
```

Or as an HTTP header:

```bash
curl -X PUT -H 'Content-Type: application/json' -H 'condition: gt(features/temperature/properties/lastModified,"2021-08-10T15:10:02.592Z")' /api/2/things/org.eclipse.ditto:fancy-thing/features/temperature/properties/value -d 19.26
```

### Ditto Protocol

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/commands/modify",
  "headers": {
    "condition": "gt(features/temperature/properties/lastModified,2021-08-10T15:10:02.592Z)"
  },
  "path": "/features/temperature/properties/value",
  "value": 19.26
}
```

### Ditto Java Client

```java
final Option<String> option =
        Options.condition("gt(features/temperature/properties/lastModified,\"2021-08-10T15:10:02.592Z\")")

client.twin().forFeature(ThingId.of("org.eclipse.ditto:fancy-thing"), "temperature")
        .putProperty("value", 42, option)
        .whenComplete((ununsed, throwable) -> {
            if (throwable != null) {
                System.out.println("Property update was not successfull: " + throwable.getMessage());
            } else {
                System.out.println("Updating the property was successful.");
            }
        });
```

## Live channel condition

Ditto supports automatic switching between [twin](protocol-twinlive.html#twin) and [live](protocol-twinlive.html#live) channels based on a condition.

Define the condition with RQL. If it matches, Ditto retrieves data from the device itself:

```text
GET .../things/{thingId}?live-channel-condition=eq(attributes/useLiveChannel,true)
```

```text
GET .../things/{thingId}?live-channel-condition=lt(_modified,"2021-12-24T12:23:42Z")
```

### Timeout strategy

The `live-channel-timeout-strategy` header controls what happens when the device does not respond within the timeout:

- `fail` (default): return status code `408`
- `use-twin`: fall back to the persisted twin data

### Response headers

The response includes two headers indicating which channel was used:

- `live-channel-condition-matched`: `true` or `false`
- `channel`: `twin` or `live`

You can use the [UpdateTwinWithLiveResponse](connectivity-mapping.html#updatetwinwithliveresponse-mapper) payload mapper to automatically update the digital twin with live response data.

## Path-specific conditions

Since Ditto 3.8.0, you can apply different conditions to different parts of a [merge operation](protocol-specification-things.html#merge-commands) using the `merge-thing-patch-conditions` header.

The header contains a JSON object where each key is a JSON pointer path (relative to the merge command's `path`) and each value is an RQL expression. Paths without conditions are always applied.

- If a path-specific condition is met, that part of the merge patch is applied.
- If a path-specific condition is not met, that part is skipped.
- Paths without conditions are always applied.

### Permissions for path-specific conditions

You need READ permission on all resources referenced in the conditions.

### Examples

Given this Thing state:

```json
{
  "thingId": "org.eclipse.ditto:fancy-thing",
  "features": {
    "temperature": {
      "properties": { "value": 15 }
    },
    "humidity": {
      "properties": { "value": 90 }
    },
    "status": {
      "properties": { "state": "active" }
    }
  }
}
```

#### HTTP API

```bash
curl -X PATCH -H 'Content-Type: application/merge-patch+json' \
    -H 'merge-thing-patch-conditions: {"features/temperature/properties/value": "gt(features/temperature/properties/value,20)", "features/humidity/properties/value": "lt(features/humidity/properties/value,80)"}' \
    http://localhost:8080/api/2/things/org.eclipse.ditto:fancy-thing \
    -d '{"features": {"temperature": {"properties": {"value": 25}}, "humidity": {"properties": {"value": 60}}, "status": {"properties": {"state": "updated"}}}}'
```

In this example:
- `temperature` is not updated (15 is not > 20)
- `humidity` is not updated (90 is not < 80)
- `status` is always updated (no condition)

#### Ditto Protocol

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/commands/merge",
  "headers": {
    "merge-thing-patch-conditions": "{\"features/temperature/properties/value\":\"gt(features/temperature/properties/value,20)\",\"features/humidity/properties/value\":\"lt(features/humidity/properties/value,80)\"}"
  },
  "path": "/",
  "value": {
    "features": {
      "temperature": { "properties": { "value": 25 } },
      "humidity": { "properties": { "value": 60 } },
      "status": { "properties": { "state": "updated" } }
    }
  }
}
```

#### Ditto Java Client

```java
Map<String, String> cond = new HashMap<>();
cond.put("features/temperature/properties/value", "gt(features/temperature/properties/value,20)");
cond.put("features/humidity/properties/value", "lt(features/humidity/properties/value,80)");

Option<Map<String, String>> condOption = Options.mergeThingPatchConditions(patchConditions);

client.twin().forId(ThingId.of("org.eclipse.ditto:fancy-thing"))
        .merge(JsonObject.newBuilder()
                .set("features", JsonObject.newBuilder()
                        .set("temperature", JsonObject.newBuilder()
                                .set("properties", JsonObject.newBuilder()
                                        .set("value", 25).build()).build())
                        .set("humidity", JsonObject.newBuilder()
                                .set("properties", JsonObject.newBuilder()
                                        .set("value", 60).build()).build())
                        .set("status", JsonObject.newBuilder()
                                .set("properties", JsonObject.newBuilder()
                                        .set("state", "updated").build()).build())
                        .build())
                .build(), condOption)
        .whenComplete((unused, throwable) -> {
            if (throwable != null) {
                System.out.println("Merge was not successful: " + throwable.getMessage());
            } else {
                System.out.println("Merge was successful.");
            }
        });
```

### Empty object removal configuration

**Configuration option:** `MERGE_REMOVE_EMPTY_OBJECTS_AFTER_PATCH_CONDITION_FILTERING`

**Default**: `false` (empty objects preserved for backward compatibility).

When enabled, Ditto recursively removes empty JSON objects created by condition filtering. This prevents unnecessary database operations when all parts of a merge patch are filtered out.

For configuration details, see [Merge operations configuration](operating-configuration.html#merge-operations-configuration).

## Further reading

- [RQL expressions](basic-rql.html) -- query language reference
- [Things specification](protocol-specification-things.html) -- Thing commands including merge
