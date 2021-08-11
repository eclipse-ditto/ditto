---
title: Conditional updates on Things
keywords: update, conditional, condition, rql
tags: [protocol]
permalink: basic-conditional-updates.html
---


Ditto supports modifying, deleting or retrieving things based on specific conditions of the current thing state.
For example, we want to update a value of an attribute but only if the actual attribute value is 42.
In this case it is possible to specify a condition when updating the attribute value:
```http request
PUT /api/2/things/org.eclipse.ditto:foo1/attributes/value?condition=ne(attributes/value,42) 
42
```

Conditions are based on [RQL expressions](basic-rql.html) and define when a request should be applied to a Thing.
It is possible to use any field in your Thing to define a condition.
E.g. you can use a timestamp in case you only want to change the state of the thing if the provided value 
is newer than in the last state of the thing.


If the condition specified in the request is not fulfilled than the thing is not modified 
and no [event/change notification](basic-changenotifications.html) is emitted.
Otherwise, the thing will be updated normally and an event will be emitted.

Conditional updates are available via HTTP API, Ditto protocol and Ditto Java client.


## Examples

In this part we will show how to use conditional updates via HTTP API, Ditto protocol and Ditto Java client.
The below examples assume that we have the following thing state:
```json
{
  "thingId": "org.eclipse.ditto:fancy-thing",
  "policyId": "org.eclipse.ditto:fancy-thing",
  "attributes": {
    "location": "Kitchen"
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

In our example we want to update the temperature value but only if the actual value is newer than the already stored one.
For this we use the _lastModified_ field in the temperature feature.
In the following sections will be shown how to do the conditional update via HTTP API, Ditto protocol 
and Ditto Java client.

### HTTP API

Using the HTTP API it is possible to specify the condition via query parameter
```http request
curl -X PUT -H 'Content-Type: application/json' /api/2/things/org.eclipse.ditto:fancy-thing/features/temperature/properties/value?condition=gt(features/temperature/properties/lastModified,2021-08-10T15:10:02.592Z) -d 19.26
```

or via HTTP header
```http request
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

### Ditto Java client

The third option to use conditional updates is the Ditto Java client.
The following code snippet demonstrates how to achieve this.

```java
DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
   .condition(
      Condition.of("gt(features/temperature/properties/lastModified,2021-08-10T15:10:02.592Z)")
   )
   .build();

client.twin().forFeature(ThingId.of("org.eclipse.ditto:fancy-thing", "temperature"))
   .putProperty("value", 42, Options.dittoHeaders(dittoHeaders))
   .whenComplete((ununsed, throwable) -> {
        if (throwable != null) {
            System.out.println("Property update was not successfull: " + throwable.getMessage());
        } else {
            System.out.println("Updating the property was successful.");
        }  
   });
```


