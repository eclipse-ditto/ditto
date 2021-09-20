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

Ditto supports retrieving, modifying, and deleting things based on specific conditions of the current persisted twin state.
Conditions are not intended to be used for message dispatching.
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
curl -X PUT -H 'Content-Type: application/json' /api/2/things/org.eclipse.ditto:fancy-thing/features/temperature/properties/value?condition=gt(features/temperature/properties/lastModified,2021-08-10T15:10:02.592Z) -d 19.26
```

or via HTTP header

```
curl -X PUT -H 'Content-Type: application/json' -H 'condition: gt(features/temperature/properties/lastModified,2021-08-10T15:10:02.592Z)' /api/2/things/org.eclipse.ditto:fancy-thing/features/temperature/properties/value -d 19.26
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

## Further reading on RQL expressions

See [RQL expressions](basic-rql.html).
