---
title: "Support conditional requests for live messages"
published: true
permalink: 2022-11-04-live-message-conditions.html
layout: post
author: aleksandar_stanchev
tags: [blog, http, protocol, rql]
hide_sidebar: true
sidebar: false
toc: true
---

With the upcoming release of Eclipse Ditto **version 3.1.0** it will be possible to process live messages based on 
conditions.

## Conditional live messages
Ditto now supports conditional message sending based on a specified condition in the request.
This functionality can be used via the HTTP API with an HTTP header or query parameter, as well as via the Ditto protocol,
and the Ditto Java Client.
For all three ways there is an example provided in this blog post.

This turns useful, if you want for example to send a message to your device, but only if its digital twin has a specific attribute set.

To be more concrete let's say we have a thing with a feature that is measuring carbon monoxide levels, and we only want to
send an alarm live message to the corresponding device, if the particle level is over 10.
To achieve this the following HTTP request can be used:

```
POST /api/2/things/org.eclipse.ditto:coffeebrewer/inbox/mesages/co-alarm?condition=gt(features/carbon-monoxide-level/properties/ppm,10)

CO Level too high! Open your windows!
```

Conditions can be specified using [RQL syntax](basic-rql.html) to check if a thing has a specific attribute
or feature property value.

In case the condition does not match to the actual state of the thing, the request will fail with
HTTP status code **412 - Precondition Failed**. And the message will not be processed.

If the given condition is invalid, the request will fail with HTTP status code **400 - Bad Request**.

More documentation for this feature can be found here: [Conditional Requests](basic-conditional-requests.html)

### Permissions for conditional requests

In order to execute a conditional request, the authorized subject needs to have WRITE permission at the resource
that should be changed by the request.

Additionally, the authorized subject needs to have READ permission at the resource used in the specified condition.
Given the condition from the introduction `gt(features/carbon-monoxide-level/properties/ppm,10)`,
read access on the single attribute would be sufficient.
However, the condition can also be more complex, or include other sub-structures of the thing.
Then of course, the authorized subject needs READ permission on all parameters of the specified condition.

## Examples
The following subsections will show how to use conditional requests via the HTTP API, Ditto protocol,
and Ditto Java Client.

To demonstrate the new conditional request, we assume that the following thing already exists:

```json
{
  "thingId": "org.eclipse.ditto:carbon-monoxide-alarm",
  "policyId": "org.eclipse.ditto:carbon-monoxide-alarm",
  "attributes": {
    "manufacturer": "ACME demo corp.",
    "location": "Wonderland",
    "serialno": "42"
  },
  "features": {
    "carbon-monoxide-level": {
      "properties": {
        "ppm,": 2
      }
    },
    "alarm": {
      "properties": {
        "lastTriggered": "2021-09-23T07:01:56Z",
        "confirmed": false
      }
    }
  },
  "ConnectionStatus": {
    "definition": [
      "org.eclipse.ditto:ConnectionStatus:1.0.0"
    ],
    "properties": {
      "status": {
        "readySince": "2022-11-04T14:35:02.643Z",
        "readyUntil": "2022-11-04T16:35:03.643Z"
      }
    }
  }
}

```

### Condition based on alarm/confirmed
In this example a live alarm message from the device should only be sent, if the alarm confirmed property is set to 
false by the end user application. This is done to prevent duplicate received alarms by the customer.
```
POST /api/2/things/org.eclipse.ditto:carbon-monoxide-alarm/inbox/mesages/co-alarm?condition=and(gt(features/carbon-monoxide-level/properties/ppm,10),eq(features/alarm/properties/confirmed,false))
```

Another use case could be to i.e. only send a message to a device when the device is connected:
```
POST /api/2/things/org.eclipse.ditto:carbon-monoxide-alarm/inbox/messages/doSomething?condition=gt(features/ConnectionStatus/properties/status/readyUntil,time:now)
```

### Permissions to execute the example
For this example, the authorized subject could have READ and WRITE permissions on the complete thing resource.
However, it is only necessary on the path _thing:/features/alarm/properties/confirmed_ and _thing:features/carbon-monoxide-level/properties/ppm_.

## Conditional requests via HTTP API
Using the HTTP API the condition can either be specified via HTTP Header or via HTTP query parameter.  
In this section, we will show how to use both options.

### Conditional request with HTTP Header
```
POST /api/2/things/org.eclipse.ditto:carbon-monoxide-alarm/outbox/messages/co-alarm
Content-Type: application/json
condition: eq(features/alarm/properties/confirmed,false)

CO Level too high! Open your windows!
```

### Conditional request with HTTP query parameter
```
POST /api/2/things/org.eclipse.ditto:carbon-monoxide-alarm/outbox/messages/co-alarm?condition=eq(features/alarm/properties/confirmed,false)
Content-Type: application/json

CO Level too high! Open your windows!
```

## Conditional request via Ditto protocol
It is also possible to use conditional requests via the Ditto protocol.
Applying the following Ditto command to the existing thing will lead to the same result as in the above HTTP example.

```json
{
  "topic": "org.eclipse.ditto/carbon-monoxide-alarm/things/live/messages/co-alarm",
  "headers": {
    "content-type": "application/json",
    "condition": "eq(features/alarm/properties/confirmed,false)"
  },
  "path": "/outbox/messages/co-alarm",
  "value": "CO Level to high! Open your windows!"
}
```

## Using conditional requests in the Ditto Java Client
The conditional requests are also supported via the [Ditto Java Client](client-sdk-java.html)
with the upcoming (**Ditto Java Client version 3.1.0**).

Example for a conditional update of a thing with the Ditto Java client:

```java
String thingId = "org.eclipse.ditto:carbon-monoxide-alarm";

// initialize the ditto-client
DittoClient dittoClient = ... ;

dittoClient.live().message(Options.condition("eq(features/alarm/properties/confirmed,false)"))
        .from(thingId)
        .subject("co-alarm")
        .payload("CO Level to high! Open your windows!")
        .send(String.class, (response, throwable) -> {
    if (throwable != null) {
        LOGGER.error("Received error while sending conditional update: '{}' ", throwable.toString());
    } else {
        LOGGER.info("Received response for conditional update: '{}'", response);
    }
});
```

## Feedback?

Please [get in touch](feedback.html) if you have feedback or questions towards this new functionality.

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/> 
The Eclipse Ditto team
