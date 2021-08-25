---
title: "Support conditional requests for things resources"
published: true
permalink: 2021-08-25-conditional-requests.html
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
This functionality can be used via the HTTP API with an HTTP header or query parameter, or via the Ditto protocol.
Conditional requests are also supported with the Ditto Java Client. 
For all three ways there is an example provided in this blog post.

With the new functionality it is possible to update, retrieve and delete things and all sub-resources 
based on a given condition. E.g. if you want to update a feature property of your thing but only if the thing has a 
special attribute set. To be more concrete let's say we have a thing with an attribute location, and we only want to 
update the temperature status of the feature water-tank if the location is "Wonderland".
To achieve this the following HTTP request can be used:
```http request
PUT /api/2/things/org.eclipse.ditto:coffeebrewer/features/water-tank/properties/status/temperature?condition=eq(attributes/location,"Wonderland")
45.5
```

Conditions can be specified using [RQL syntax](basic-rql.html) to check if a thing has a special attribute 
or feature property value.

In case the condition does not match to the actual state of the thing the request will fail with 
HTTP status code 412 Precondition Failed. There will also be no event emitted for this case. 
If the given condition is invalid the request will fail with HTTP status code 400 Bad Request. 

### Permissions for conditional requests on things and things sub-resources
In order to execute a conditional request, the authorized subject needs to have WRITE permission at the resource
that should be changed by the request. Additionally, the authorized subject needs to have READ permission at the resource
which is used in the specified condition.


## Examples

To demonstrate the new conditional request feature, we assume that the following thing already exists:

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
          "lastModified": "2021-08-25T12:38:27"
        }
      }
    }
  }
}
```

### Permissions to execute the example
For this example, the authorized subject needs to have WRITE permissions on the thing resource (at least for all features).
The WRITE permission must not be revoked on any level further down the hierarchy.
Additionally, the authorized subject needs to have READ permissions on the attributes of the thing.

The following sub-sections will show how to use conditional requests via the HTTP API, the Ditto protocol 
and the Ditto Java Client. In these examples the temperature feature property of the thing should only be updated if
it was last modified after "2021-08-25T12:38:27".


### Conditional requests via HTTP API
Using the HTTP API the condition can either be specified via HTTP Header or via HTTP query parameter.
In this section we will show how to use both options.

#### Conditional request with HTTP Header
```http request
curl -X PATCH -H 'Content-Type: application/json' -H 'condition: gt(features/water-tank/properties/status/lastModified,"2021-08-25T12:40:00")' /api/2/things/org.eclipse.ditto:coffeebrewer/features/water-tank/properties/properties/temperature -d '{ temperature: 45.26, "lastModified": "'"$(date +%Y-%m-%dT%H:%M:%S)"'" }'
```

#### Conditional request with HTTP query parameter
```http request
curl -X PATCH -H 'Content-Type: application/json' /api/2/things/org.eclipse.ditto:coffeebrewer/features/water-tank/properties/status/temperature?condition=gt(features/water-tank/properties/status/lastModified,"2021-08-25T12:40:00") -d '{ temperature: 45.26, "lastModified": "'"$(date +%Y-%m-%dT%H:%M:%S)"'" }'
```

After the request was successfully performed the thing will look like this:

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
          "lastModified": "2021-08-25T12:42:12"
        }
      }
    }
  }
}
```

### Conditional request via Ditto protocol
It is also possible to use conditional requests via the Ditto protocol.
Applying the following Ditto command to the existing thing will lead to the same result as in the above HTTP example.   

```json
{
  "topic": "org.eclipse.ditto/coffeebrewer/things/twin/commands/modify",
  "headers": {
    "content-type": "application/json",
    "condition": "gt(features/water-tank/properties/status/lastModified,\"2021-08-25T12:40:00\")"
  },
  "path": "/features/water-tank/properties/status/temperature",
  "value": 45.26
}
```

### Using conditional requests in the ditto-client 
The conditional requests are also supported via the [Ditto Java Client](client-sdk-java.html) 
with the upcoming (**Ditto Java Client version 2.1.0**).

Example for a conditional update of a thing with the Ditto Java Client:

```java
final String THING_ID = "org.eclipse.ditto:coffeebrewer";
final String FEATURE_ID = "water-tank";
final Feature FEATURE = ThingsModelFactory.newFeatureBuilder()
        .properties(ThingsModelFactory.newFeaturePropertiesBuilder()
            .set("status", JsonFactory.newObjectBuilder()
                .set("temperature", 45.2)
                .set("lastModified", Instant.now())
                .build())
            .build())
        .withId(FEATURE_ID)
        .build();

final Thing THING = ThingsModelFactory.newThingBuilder()
        .setId(THING_ID)
        .setFeature(FEATURE)
        .build();

// initialize the ditto-client
final DittoClient dittoClient = ... ;

dittoClient.twin().update(THING, Options.Modify.condition("gt(features/water-tank/properties/status/lastModified,"2021-08-25T12:40:00")")).
        .whenComplete(((adaptable, throwable) -> {
            if (throwable != null) {
                LOGGER.error("Received error while sending conditional update: '{}' ", throwable.toString());
            } else {
                LOGGER.info("Received response for conditional update: '{}'", adaptable);
            }
        }));
```

After running this code snippet, the existing thing should look like the above result for the HTTP example.


## Feedback?

Please [get in touch](feedback.html) if you have feedback or questions towards this new functionality.

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/> 
The Eclipse Ditto team




