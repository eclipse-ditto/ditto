---
title: Conditional requests on things
keywords: request, conditional, condition, rql
tags: [protocol, http, rql]
permalink: basic-conditional-requests.html
---

Ditto already supports [Conditional Requests](httpapi-concepts.html#conditional-requests) as defined in [RFC-7232](https://tools.ietf.org/html/rfc7232) 
where the `If-Match` and `If-None-Match` headers can be used to specify if a request should be applied or not.
With the `condition` header it is possible to specify a condition based on the state of the actual thing.
It is possible to combine both headers within one request. If you use both headers keep in mind that the ETag header is evaluated first.   

## Defining conditions

Ditto supports retrieving, modifying, deleting and sending messages to/from things based on specific conditions of the 
current persisted twin state.
For example, if you want to update the value of an attribute, but only if the current attribute value is not already 42,
you can specify a condition:

```
PUT /api/2/things/org.eclipse.ditto:foo1/attributes/value?condition=ne(attributes/value,42)
42
```

Conditions are based on [RQL expressions](basic-rql.html) and define that a request should be applied to a thing 
only if the condition is met. It is possible to use any field in your thing to define a condition. 
E.g. you can use a timestamp in case you only want to change the state of the thing, if the provided value 
is newer than in the last state of the thing.

* If the condition specified in the request is fulfilled, the thing will be updated and an event will be emitted.
* If the condition specified in the request is not fulfilled, the thing is not modified, and no [event/change notification](basic-changenotifications.html) is emitted.

Conditional requests are supported by HTTP API, WebSocket, Ditto protocol and Ditto Java Client.

### Permissions for conditions

READ permission is necessary on the resource specified in the condition, otherwise, the request will fail.

## Examples

In this part, we will show how to use conditional updates via HTTP API, Ditto protocol, and Ditto Java Client.
The below examples assume that we have the following thing state:

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

In our example we want to update the **temperature** value, but only if the current value is newer than the already stored one.
To express this condition, we use the _lastModified_ field in the temperature feature.

In the following sections, we will show how request the conditional update via HTTP API, Ditto protocol, 
and Ditto Java Client which is based on the WebSocket protocol.

### HTTP API

Using the HTTP API it is possible to specify the condition via query parameter

```
curl -X PUT -H 'Content-Type: application/json' /api/2/things/org.eclipse.ditto:fancy-thing/features/temperature/properties/value?condition=gt(features/temperature/properties/lastModified,'2021-08-10T15:10:02.592Z') -d 19.26
```

or via HTTP header

```
curl -X PUT -H 'Content-Type: application/json' -H 'condition: gt(features/temperature/properties/lastModified,"2021-08-10T15:10:02.592Z")' /api/2/things/org.eclipse.ditto:fancy-thing/features/temperature/properties/value -d 19.26
```

### Ditto protocol

The Ditto protocol supports also conditional updates. 
This is an example how to do a conditional update via [Ditto Protocol](protocol-specification.html) message:

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

The third option to use conditional updates is the ditto-client.
The following code snippet demonstrates how to achieve this.

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

Ditto also supports retrieving thing data with an automatic approach for switching between 
[twin](protocol-twinlive.html#twin) and [live](protocol-twinlive.html#live) channel.

Conditions are defined with RQL as described [before](#defining-conditions). 
If a condition is matched, the Thing data is retrieved from the device itself.

Example: retrieve data from the device itself if a certain attribute is configured at the twin:
```
GET .../things/{thingId}?live-channel-condition=eq(attributes/useLiveChannel,true)
```

Example: retrieve data from the device itself if the last modification timestamp is behind a specified timestamp:
```
GET .../things/{thingId}?live-channel-condition=lt(_modified,"2021-12-24T12:23:42Z")
```

### Live channel condition headers

Additionally, a strategy to handle timeouts for retrieving live thing data can be specified with the header
`live-channel-timeout-strategy`.  
The header value holds a **strategy** what to do in case a timeout (can also be specified as header) was encountered.

* If the value cannot be retrieved live from the device itself during the specified timeout, the request will
  `fail` (which is the default strategy if not specified otherwise) with a status code 408.
* Alternatively, if `use-twin` was defined as the `live-channel-timeout-strategy` strategy, the request will fall back 
  to the persisted twin and return the latest value stored in the digital twin.


### Live channel condition response headers

The response includes two additional headers to indicate which channel was used to retrieve the thing data:

* `live-channel-condition-matched` – value could be `true` or `false` and states whether the passed live-channel-condition was a match or not
* `channel` – value could be `twin` or `live` and defines which channel was the origin of the returned data.

In line with the procedure described above on how to retrieve live data directly from a device, a new type of 
pre-configured payload mapping, namely [UpdateTwinWithLiveResponse](connectivity-mapping.html#updatetwinwithliveresponse-mapper)
was introduced.

Upon activation, the digital twin stored in Eclipse Ditto will implicitly be updated with the latest data from the 
_live response_ sent by the device.

## Path-specific conditions

In addition to global conditions, Ditto supports since version 3.8.0 path-specific conditions for 
[merge operations](protocol-specification-things-merge.html) using the `merge-thing-patch-conditions` header.  
This feature allows you to apply different [RQL conditions](basic-rql.html) to different parts of a merge patch, 
enabling fine-grained control over which parts of the patch are applied based on the current state of the Thing.

The `merge-thing-patch-conditions` header contains a JSON object where each key represents a JSON pointer path and each 
value is an RQL expression that must evaluate to `true` for that path to be included in the merge.

* If a path-specific condition is fulfilled, that part of the merge patch will be applied.
* If a path-specific condition is not fulfilled, that part of the merge patch will be skipped.
* Paths without conditions will always be applied.

Path-specific conditions are supported by HTTP API, WebSocket, Ditto protocol and Ditto Java Client.

### Permissions for path-specific conditions

READ permission is necessary on all resources referenced in the path-specific conditions, otherwise, the request will fail.

### Examples

This part shows how to use path-specific conditions via HTTP API, Ditto protocol, and Ditto Java Client.
The below examples assume that there is a thing with the following thing state:

```json
{
  "thingId": "org.eclipse.ditto:fancy-thing",
  "policyId": "org.eclipse.ditto:fancy-thing",
  "attributes": {
    "location": "kitchen",
    "manufacturer": "ACME Corp",
    "lastMaintenance": "2023-01-15T10:30:00Z"
  },
  "features": {
    "temperature": {
      "properties": {
        "value": 15,
        "unit": "celsius",
        "lastUpdated": "2023-01-20T14:30:00Z"
      }
    },
    "humidity": {
      "properties": {
        "value": 90,
        "unit": "percent",
        "lastUpdated": "2023-01-20T14:30:00Z"
      }
    },
    "status": {
      "properties": {
        "state": "active",
        "mode": "automatic"
      }
    }
  }
}
```

#### HTTP API

Using the HTTP API it is possible to specify path-specific conditions via HTTP header `merge-thing-patch-conditions`:

```shell
curl -X PATCH -H 'Content-Type: application/merge-patch+json' \
    -H 'merge-thing-patch-conditions: {"features/temperature/properties/value": "gt(features/temperature/properties/value,20)", "features/humidity/properties/value": "lt(features/humidity/properties/value,80)"}' \
    http://localhost:8080/api/2/things/org.eclipse.ditto:fancy-thing \
    -d '{"features": {"temperature": {"properties": {"value": 25}}, "humidity": {"properties": {"value": 60}}, "status": {"properties": {"state": "updated"}}}}'
```

In this example:
- `temperature` will only be updated if the current temperature is greater than 20 (condition fails, so temperature won't be updated)
- `humidity` will only be updated if the current humidity is less than 80 (condition fails, so humidity won't be updated)  
- `status` will always be updated (no condition specified)

#### Ditto protocol

The Ditto protocol supports also path-specific conditions for merge operations.
This is an example how to do a conditional merge via [Ditto Protocol](protocol-specification.html) message:

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/commands/merge",
  "headers": {
    "merge-thing-patch-conditions": "{\"features/temperature/properties/value\":\"gt(features/temperature/properties/value,20)\",\"features/humidity/properties/value\":\"lt(features/humidity/properties/value,80)\"}"
  },
  "path": "/",
  "value": {
    "attributes": {
      "lastMaintenance": "2023-01-20T15:00:00Z"
    },
    "features": {
      "temperature": {
        "properties": {
          "value": 25
        }
      },
      "humidity": {
        "properties": {
          "value": 60
        }
      },
      "status": {
        "properties": {
          "state": "updated"
        }
      }
    }
  }
}
```

#### Ditto Java Client

The third option to use path-specific conditions is the [ditto-client](client-sdk-java.html).  
The following code snippet demonstrates how to achieve this.

```java
Map<String, String> cond = new HashMap<>();
cond.put("features/temperature/properties/value", "gt(features/temperature/properties/value,20)");
cond.put("features/humidity/properties/value", "lt(features/humidity/properties/value,80)");

Option<Map<String, String>> condOption = Options.mergeThingPatchConditions(patchConditions);

client.twin().forId(ThingId.of("org.eclipse.ditto:fancy-thing"))
        .merge(JsonObject.newBuilder()
                .set("attributes", JsonObject.newBuilder()
                        .set("lastMaintenance", "2023-01-20T15:00:00Z")
                        .build())
                .set("features", JsonObject.newBuilder()
                        .set("temperature", JsonObject.newBuilder()
                                .set("properties", JsonObject.newBuilder()
                                        .set("value", 25)
                                        .build())
                                .build())
                        .set("humidity", JsonObject.newBuilder()
                                .set("properties", JsonObject.newBuilder()
                                        .set("value", 60)
                                        .build())
                                .build())
                        .set("status", JsonObject.newBuilder()
                                .set("properties", JsonObject.newBuilder()
                                        .set("state", "updated")
                                        .build())
                                .build())
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

### Configuration for path-specific conditions

When using path-specific conditions, it's possible that all parts of a merge payload are filtered out, resulting in 
empty JSON objects being persisted to the database.  
Ditto provides a configuration option to avoid persisting events in case that only empty objects are left after applying 
the conditions.

#### Empty object removal

**Configuration option:** `MERGE_REMOVE_EMPTY_OBJECTS_AFTER_PATCH_CONDITION_FILTERING`

**Default behavior:** `false` (empty objects are preserved for backward compatibility)

**When enabled:** Empty objects created by patch condition filtering are removed recursively, preventing unnecessary 
database operations and saving database I/O.

**Example scenario - Complete filtering:**

```json
// Current Thing state
{
  "features": {
    "temp": {"properties": {"value": 15}},  // Current: 15
    "hum": {"properties": {"value": 70}}    // Current: 70
  }
}

// Merge payload
{
  "features": {
    "temp": {"properties": {"value": 25}},
    "hum": {"properties": {"value": 60}}
  }
}

// Patch conditions (both will evaluate to false)
{
  "features/temp/properties/value": "gt(features/temp/properties/value,30)",  // 15 > 30 = false
  "features/hum/properties/value": "lt(features/hum/properties/value,50)"     // 70 < 50 = false
}

// Result with configuration disabled (default)
{
  "features": {
    "temp": {"properties": {}},  // Empty object preserved
    "hum": {"properties": {}}    // Empty object preserved
  }
}
// → Database operation still occurs, empty objects stored

// Result with configuration enabled
{}  // Completely empty payload
// → Database operation skipped entirely, no storage, no event emission, no new revision
```

For configuration details, see [Merge operations configuration](installation-operating.html#merge-operations-configuration).

## Further reading on RQL expressions

See [RQL expressions](basic-rql.html).
