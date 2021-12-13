---
title: "Support conditional requests for things resources"
published: true
permalink: 2021-09-23-conditional-requests.html
layout: post
author: stefan_maute
tags: [blog, http, protocol, rql]
hide_sidebar: true
sidebar: false
toc: true
---

With the upcoming release of Eclipse Ditto **version 2.1.0** it will be possible to execute conditional requests on things 
and their sub-resources.

## Conditional requests for things resources
Ditto now supports conditional requests on things and all of its sub-resources based on a specified condition in the request.
This functionality can be used via the HTTP API with an HTTP header or query parameter, as well as via the Ditto protocol, 
and the Ditto Java Client. 
For all three ways there is an example provided in this blog post.

With the new functionality, it is possible to retrieve, update, and delete things and all sub-resources 
based on a given condition. This turns useful, if you want for example to update a feature property of your thing, 
but only if the thing has a specific attribute set. 

To be more concrete let's say we have a thing with an attribute _location_, and we only want to 
update the _temperature_ status of the feature water-tank to 45.5, if the location is "Wonderland".
To achieve this the following HTTP request can be used:

```
PUT /api/2/things/org.eclipse.ditto:coffeebrewer/features/water-tank/properties/status/temperature?condition=eq(attributes/location,"Wonderland")
45.5
```

Conditions can be specified using [RQL syntax](basic-rql.html) to check if a thing has a specific attribute 
or feature property value.

In case the condition does not match to the actual state of the thing, the request will fail with 
HTTP status code **412 - Precondition Failed**. There will also be no event emitted for this case. 

If the given condition is invalid, the request will fail with HTTP status code **400 - Bad Request**. 

More documentation for this feature can be found here: [Conditional Requests](basic-conditional-requests.html)

### Permissions for conditional requests

In order to execute a conditional request, the authorized subject needs to have WRITE permission at the resource
that should be changed by the request. 

Additionally, the authorized subject needs to have READ permission at the resource used in the specified condition.
Given the condition from the introduction `condition=eq(attributes/location,"Wonderland")`, 
read access on the single attribute would be sufficient. 
However, the condition can also be more complex, or include other sub-structures of the thing. 
Then of course, the authorized subject needs READ permission on all parameters of the specified condition.

## Examples
The following sub-sections will show how to use conditional requests via the HTTP API, the Ditto protocol, 
and the Ditto Java Client.

To demonstrate the new conditional request, we assume that the following thing already exists:

```json
{
  "thingId": "org.eclipse.ditto:coffeebrewer",
  "policyId": "org.eclipse.ditto:coffeebrewer-policy",
  "definition": "org.eclipse.ditto:coffeebrewer:0.1.0",
  "attributes": {
    "manufacturer": "ACME demo corp.",
    "location": "Wonderland",
    "serialno": "42",
    "model": "Speaking coffee machine"
  },
  "features": {
    "coffee-brewer": {
      "definition": ["org.eclipse.ditto:coffeebrewer:0.1.0"],
      "properties": {
        "brewed-coffees": 0
      }
    },
    "water-tank": {
      "properties": {
        "configuration": {
          "smartMode": true,
          "brewingTemp": 87,
          "tempToHold": 44.5,
          "timeoutSeconds": 6000
        },
        "status": {
          "waterAmount": 731,
          "temperature": 44.2,
          "lastModified": "2021-09-23T07:01:56Z"
        }
      }
    }
  }
}
```

### Condition based on last modification
In this example the water-tanks's **temperature** should only be updated if it was **lastModified** 
after "2021-08-25T12:38:27".

### Permissions to execute the example
For this example, the authorized subject could have READ and WRITE permissions on the complete thing resource.
However, it is only necessary on the path _thing:/features/water-tank/properties/status_, because the **temperature** 
as well as the conditional part **lastModified** are located there.

## Conditional requests via HTTP API
Using the HTTP API the condition can either be specified via HTTP Header or via HTTP query parameter.  
In this section, we will show how to use both options.

### Conditional request with HTTP Header
```
curl -X PATCH -H 'Content-Type: application/json' -H 'condition: gt(features/water-tank/properties/status/lastModified,"2021-09-23T07:00:00Z")' /api/2/things/org.eclipse.ditto:coffeebrewer/features/water-tank/properties/properties/temperature -d '{ temperature: 45.26, "lastModified": "'"$(date --utc +%FT%TZ)"'" }'
```

### Conditional request with HTTP query parameter
```
curl -X PATCH -H 'Content-Type: application/json' /api/2/things/org.eclipse.ditto:coffeebrewer/features/water-tank/properties/status/temperature?condition=gt(features/water-tank/properties/status/lastModified,"2021-09-23T07:00:00Z") -d '{ temperature: 45.26, "lastModified": "'"$(date --utc +%FT%TZ)"'" }'
```

### Result  
After the request was successfully performed, the thing will look like this:

```json
{
  "thingId": "org.eclipse.ditto:coffeebrewer",
  "policyId": "org.eclipse.ditto:coffeebrewer-policy",
  "definition": "org.eclipse.ditto:coffeebrewer:0.1.0",
  "attributes": {
    "manufacturer": "ACME demo corp.",
    "location": "Wonderland",
    "serialno": "42",
    "model": "Speaking coffee machine"
  },
  "features": {
    "coffee-brewer": {
      "definition": ["org.eclipse.ditto:coffeebrewer:0.1.0"],
      "properties": {
        "brewed-coffees": 0
      }
    },
    "water-tank": {
      "properties": {
        "configuration": {
          "brewingTemp": 87,
          "tempToHold": 44.5,
          "timeoutSeconds": 6000
        },
        "status": {
          "waterAmount": 731,
          "temperature": 45.26,
          "lastModified": "2021-09-23T07:05:36Z"
        }
      }
    }
  }
}
```

## Conditional request via Ditto protocol
It is also possible to use conditional requests via the Ditto protocol.
Applying the following Ditto command to the existing thing will lead to the same result as in the above HTTP example.

```json
{
  "topic": "org.eclipse.ditto/coffeebrewer/things/twin/commands/modify",
  "headers": {
    "content-type": "application/json",
    "condition": "gt(features/water-tank/properties/status/lastModified,\"2021-09-23T07:00:00Z\")"
  },
  "path": "/features/water-tank/properties/status/temperature",
  "value": 45.26
}
```

## Using conditional requests in the Ditto Java Client 
The conditional requests are also supported via the [Ditto Java Client](client-sdk-java.html) 
with the upcoming (**Ditto Java Client version 2.1.0**).

Example for a conditional update of a thing with the Ditto Java client:

```java
String thingId = "org.eclipse.ditto:coffeebrewer";
String featureId = "water-tank";
Feature feature = ThingsModelFactory.newFeatureBuilder()
        .properties(ThingsModelFactory.newFeaturePropertiesBuilder()
            .set("status", JsonFactory.newObjectBuilder()
                .set("temperature", 45.2)
                .set("lastModified", Instant.now())
                .build())
            .build())
        .withId(featureId)
        .build();

Thing thing = ThingsModelFactory.newThingBuilder()
        .setId(thingId)
        .setFeature(feature)
        .build();

// initialize the ditto-client
DittoClient dittoClient = ... ;

dittoClient.twin().update(thing, Options.condition("gt(features/water-tank/properties/status/lastModified,'2021-09-23T07:00:00Z')"))
        .whenComplete((adaptable, throwable) -> {
            if (throwable != null) {
                LOGGER.error("Received error while sending conditional update: '{}' ", throwable.toString());
            } else {
                LOGGER.info("Received response for conditional update: '{}'", adaptable);
            }
        });
```

After running this code snippet, the existing thing should look like the above result for the HTTP example.


## Feedback?

Please [get in touch](feedback.html) if you have feedback or questions towards this new functionality.

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/> 
The Eclipse Ditto team
