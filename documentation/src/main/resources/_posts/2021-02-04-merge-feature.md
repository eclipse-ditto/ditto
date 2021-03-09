---
title: "Support merge functionality for things resources"
published: true
permalink: 2021-02-04-merge-feature.html
layout: post
author: stefan_maute
tags: [blog, http, protocol]
hide_sidebar: true
sidebar: false
toc: true
---

With the upcoming release of Eclipse Ditto **version 2.0.0** it will be possible to merge existing things and their subresources.

## Merge functionality for things resources
Ditto now supports merging of existing things and all of its subresources with the provided payload in the request.
This can be done by using the HTTP API with the PATCH method, via the Ditto protocol, and also by using the Ditto Java Client. 
For all three ways there is an example provided in this blog post.

In contrast to the already existing PUT resource, this new functionality **allows partial updates** on a thing and all its subresources. 
To get more into detail, from now on it is possible to add or update attributes, and a feature property at the same time,
without overwriting the complete thing. Another use case might be to update several feature properties within a single request
and let all other parts of the thing untouched. 

Ditto uses the [JSON Merge Patch](https://tools.ietf.org/html/rfc7396) semantics to merge the request body 
with the existing thing. In short, a JSON merge patch resembles the original JSON structure of a thing, and 
the fields provided in the patch are added, updated, or deleted in the existing thing.

Please be aware that `null` values have a special meaning when applying a merge patch. A `null` value indicates 
the removal of existing fields in the updated thing. 
For more details and examples, please refer to [RFC-7396](https://tools.ietf.org/html/rfc7396).

### Permissions to merge things and things subresources
In order to execute such a merge operation, the authorized subject needs to have WRITE permission at all resources
that should change by the merge. Consequently, if the permission is missing for some part of the merge,
the merge is rejected and **not** applied at all.


## Examples

To demonstrate the new merge feature, we assume that the following thing already exists:

```json
{
  "thingId": "com.acme:coffeebrewer",
  "policyId": "com.acme:coffeebrewer-policy",
  "definition": "com.acme:coffeebrewer:0.1.0",
  "attributes": {
    "manufacturer": "ACME demo corp.",
    "location": "Berlin, main floor",
    "serialno": "42",
    "model": "Speaking coffee machine"
  },
  "features": {
    "coffee-brewer": {
      "definition": ["com.acme:coffeebrewer:0.1.0"],
      "properties": {
        "brewed-coffees": 0
      }
    },
    "water-tank": {
      "properties": {
        "configuration": {
          "smartMode": true,
          "brewingTemp": 87,
          "tempToHold": 44,
          "timeoutSeconds": 6000
        },
        "status": {
          "waterAmount": 731,
          "temperature": 44
        }
      }
    }
  }
}
```

### Permissions to execute the example
For this example, the authorized subject needs to have unrestricted WRITE permissions on all affected paths 
of the JSON merge patch: *attributes/manufacturingYear*, *features/water-tank/properties/configuration/smartMode*, and
*features/water-tank/properties/configuration/tempToHold*.
The WRITE permission must not be revoked on any level further down the hierarchy.
Consequently, it is also sufficient for the authorized subject to have unrestricted WRITE permission at root level or
unrestricted WRITE permission at /attributes and /features etc.

The following subparts will show how to use the merge feature via the HTTP API, the Ditto protocol 
and the Ditto Java Client.

### Merge via HTTP API
An existing thing can be merged via the HTTP API using the *PATCH* method with the following request body.
Notice that this request will add the "manufacturingYear" to the attributes, update the "tempToHold" to 50 and 
delete the "smartMode" key from  the feature property "water-tank".

The `Content-Type` header for this request must be *application/merge-patch+json*.

PATCH /things/com.acme:coffeebrewer
    
```json
{
  "attributes": {
    "manufacturingYear": "2020"
  },
  "features": {
    "water-tank": {
      "properties": {
        "configuration": {
          "smartMode": null,
          "tempToHold": 50
        }
      }
    }
  }
}
```

After the request was successfully performed the thing will look like this:

```json
{
  "thingId": "com.acme:coffeebrewer",
  "policyId": "com.acme:coffeebrewer-policy",
  "definition": "com.acme:coffeebrewer:0.1.0",
  "attributes": {
    "manufacturer": "ACME demo corp.",
    "manufacturingYear": "2020",
    "location": "Berlin, main floor",
    "serialno": "42",
    "model": "Speaking coffee machine"
  },
  "features": {
    "coffee-brewer": {
      "definition": ["com.acme:coffeebrewer:0.1.0"],
      "properties": {
        "brewed-coffees": 0
      }
    },
    "water-tank": {
      "properties": {
        "configuration": {
          "brewingTemp": 87,
          "tempToHold": 50,
          "timeoutSeconds": 6000
        },
        "status": {
          "waterAmount": 731,
          "temperature": 44
        }
      }
    }
  }
}
```

It is also possible to apply the *PATCH* method to all subresources of a thing, e.g. merging only the attributes of a thing.    
Check out the newly added *PATCH* resources in our [HTTP API](http-api-doc.html). 

### Merge via Ditto protocol
It is also possible to merge the existing thing via the Ditto protocol.
Applying the following Ditto merge command to the existing thing will lead to the same result as in the above HTTP example.   

```json
{
  "topic": "com.acme/coffeebrewer/things/twin/commands/merge",
  "headers": {
    "content-type": "application/merge-patch+json"
  },
  "path": "/",
  "value": {
    "thingId": "com.acme:coffeebrewer",
    "attributes": {
      "manufacturingYear": "2020"
    },
    "features": {
      "water-tank": {
        "properties": {
          "configuration": {
            "smartMode": null,
            "tempToHold": 50
          }
        }
      }
    }
  }
}
```

Another Ditto protocol example to merge a feature property:

```json
{
  "topic": "com.acme/coffeebrewer/things/twin/commands/merge",
  "headers": {
    "content-type": "application/merge-patch+json"
  },
  "path": "/features/coffee-brewer/properties/brewed-coffees",
  "value": 42
}
```


### Using the ditto-client to merge things
The merge functionality is also supported via the [Ditto Java Client](client-sdk-java.html) 
with the upcoming (**Ditto Java Client version 2.0.0**).

Example for merging a thing with the Ditto Java Client:

```java
final String THING_ID = "com.acme:coffeebrewer";
final String FEATURE_ID = "water-tank";
final JsonPointer ATTRIBUTE_KEY = JsonFactory.newPointer("manufacturingYear");
final String ATTRIBUTE_VALUE = "2020";
final Feature FEATURE = ThingsModelFactory.newFeatureBuilder()
        .withId(FEATURE_ID)
        .properties(ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("smartMode", false)
                        .set("tempToHold", 50)
                        .build())
        .build();

final Thing THING = ThingsModelFactory.newThingBuilder()
        .setId(THING_ID)
        .setAttribute(ATTRIBUTE_KEY_NEW, JsonFactory.newValue(ATTRIBUTE_VALUE))
        .setFeature(FEATURE)
        .build();

// initialize the ditto-client
final DittoClient dittoClient = ... ;

dittoClient.twin().merge(THING_ID, THING)
        .whenComplete(((adaptable, throwable) -> {
            if (throwable != null) {
                LOGGER.error("Received error while sending MergeThing: '{}' ", throwable.toString());
            } else {
                LOGGER.info("Received response for MergeThing: '{}'", adaptable);
            }
        }));
```

After running this code snippet, the existing thing should look like the above result for the HTTP example.

More examples for merging an attribute, all attributes and a feature property via Ditto Java Client.
```java
// initialize the ditto-client
final DittoClient dittoClient = ... ;

    // merge attribute
    dittoClient.twin()
        .forId("com.acme:coffeebrewer")
        .mergeAttribute("manufacturingYear", "2021")
        .whenComplete(...);
        
    // merge attributes
    dittoClient.twin()
        .forId("com.acme:coffeebrewer")
        .mergeAttributes(JsonObject.newBuilder().set("manufacturingYear", "2021").build())
        .whenComplete(...);
    
    // merge feature property
    dittoClient.twin()
        .forFeature("com.acme:coffeebrewer", "water-tank")
        .mergeProperty("configuration/smartMode", false)
        .whenComplete(...);
```

## Merge events
In this section we want to cover the new `ThingMerged` event which will be emitted after successfully applying an `MergeThing` command.
For every HTTP request or Ditto protocol message which performs a merge operation on a thing there will be sent out 
exactly one `ThingMerged` event. This event contains the __path__ and the __value__ of the merge operation.
The __path__ describes on which level of the thing the __value__ was merged.


### Merge event example
Let's assume we want to patch/merge multiple feature properties at once.
PATCH /things/com.acme:coffeebrewer/features

```json
{
  "coffee-brewer": {
    "properties": {
      "brewed-coffees": 10
    }
  },
  "water-tank": {
    "properties": {
      "configuration": {
        "smartMode": null,
        "tempToHold": 30
      }
    }
  }
}
```

The following `ThingMerged` event is emitted:

```json
{
  "topic": "com.acme/coffeebrewer/things/twin/events/merged",
  "headers": {
    "content-type": "application/merge-patch+json"
  },
  "path": "/features",
  "value": {
    "coffee-brewer": {
      "properties": {
        "brewed-coffees": 10
      }
    },
    "water-tank": {
      "properties": {
        "configuration": {
          "smartMode": null,
          "tempToHold": 30
        }
      }
    }
  },
  "revision": 42,
  "timestamp": "2021-02-04T09:42:39Z"
}
```


## Feedback?

Please [get in touch](feedback.html) if you have feedback or questions towards this new functionality.

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/> 
The Eclipse Ditto team




