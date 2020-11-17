---
title: "Desired Feature Properties"
published: true
permalink: 2020-11-11-desired-properties.html
layout: post
author: david_schwilk
tags: [blog]
hide_sidebar: true
sidebar: false
toc: true
---

## Desired feature properties added to things model
With the upcoming release of Eclipse Ditto **version 1.5.0** 
[desired feature properties](basic-feature.html#feature-desired-properties) are introduced to the things model for 
**API versions later than 1**. The _desired properties_ for features are added on the same level of the model as 
the feature properties and can reflect for example feature property updates ,which are intended, but not yet applied. 

{% include note.html content="Further logics for desired feature properties might be implemented in future Ditto 
                              versions." %}

A fully-fledged JSON representation of a feature with desired properties is shown below:

```json
{
    "lamp": {
        "definition": [ "com.mycompany.fb:Lamp:1.0.0" ],
        "properties": {
            "configuration": {
                "on": true,
                "location": {
                    "longitude": 34.052235,
                    "latitude": -118.243683
                }
            },
            "status": {
                "on": false,
                "color": {
                    "red": 128,
                    "green": 255,
                    "blue": 0
                }
            }
        },
        "desiredProperties": {
            "configuration": {
                "on": false
            }
        }
    }
}
```

## Operations on desired feature properties

* **CRUD operations**
    - You can create multiple desired properties of a feature or just single ones.
    - You can retrieve all desired properties of a feature or just single ones.
    - You can modify all desired properties of a feature or just single ones.
    - You can delete all desired properties of a feature or just single ones.
* **Search**
    - You can [search](httpapi-search.html) for things with specific desired properties with [RQL-functions](basic-rql.html).
    - You can search for things, which have [existent](basic-rql.html#exists) desired properties for a feature.
* **Get notified on changes**
    - You can [receive events](basic-signals-event.html) for changes done to the desired properties of things 
      you're authorized to read.
    - You can [enrich](basic-enrichment.html) and [filter](basic-changenotifications.html#filtering) the 
    events you want to receive, for changes done to the desired properties.
    
### Executing CRUD operations on desired feature properties
CRUD operations can be executed either via the [Ditto HTTP API](httpapi-concepts.html) **versions later than 1** or via 
[ditto-protocol](protocol-overview.html) messages.

_Possible CRUD operations for desired feature properties via ditto-protocol_:

- [Retrieve all desired properties of a feature via ditto-protocol](protocol-examples-retrievedesiredproperties.html)
- [Retrieve a single desired property of a feature via ditto-protocol](protocol-examples-retrievedesiredproperty.html)
- [Create/Modify all desired properties of a feature via ditto-protocol](protocol-examples-modifydesiredproperties.html)
- [Create/Modify a single desired property of a feature via ditto-protocol](protocol-examples-modifydesiredproperty.html)
- [Delete all desired properties of a feature via ditto-protocol](protocol-examples-deletedesiredproperties.html)
- [Delete a single desired property of a feature via ditto-protocol](protocol-examples-deletedesiredproperty.html)

### Using the ditto-client to manage desired feature properties
The desired feature properties can also be retrieved, modified and deleted via the [Ditto Java Client](client-sdk-java.html). 
With the upcoming (**Ditto Java Client version 1.5.0**), no special CRUD operations for 
desired feature properties are implemented in the client. Thus, the operations have to be executed via creating 
**ditto-protocol messages** manually in the client.

Example for creating/modifying desired feature properties of a thing via the ditto-client:

```java
final Adaptable modifyFeatureDesiredProperties =
                Adaptable.newBuilder(TopicPath.newBuilder(ThingId.of("com.mycompany.fb:Car:1.0.0"))
                        .things()
                        .twin()
                        .commands()
                        .modify()
                        .build())
                        .withPayload(Payload.newBuilder(
                                JsonPointer.of("/features/lamp/desiredProperties"))
                                .withValue(JsonObject.newBuilder().set("on", false).build())
                                .build()).build();

        client.sendDittoProtocol(modifyFeatureDesiredProperties).whenComplete(((adaptable, throwable) -> {
            if (throwable != null) {
                LOGGER.error("Received error while sending ModifyFeatureDesiredProperties: '{}' ",
                        throwable.toString());
            } else {
                LOGGER.info("Received response for ModifyFeatureDesiredProperties: '{}'", adaptable);
            }
        }));
```

## Feedback?

Please [get in touch](feedback.html) if you have feedback or questions towards this new concept of desired properties.

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
