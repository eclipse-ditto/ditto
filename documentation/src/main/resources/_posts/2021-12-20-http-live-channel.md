---
title: "Sending live channel commands via HTTP to devices"
published: true
permalink: 2021-12-20-http-live-channel.html
layout: post
author: stefan_maute
tags: [blog, http]
hide_sidebar: true
sidebar: false
toc: true
---

The upcoming release of Eclipse Ditto **version 2.3.0** will support sending commands via the HTTP API 
directly to devices using the [live channel](protocol-twinlive.html#live) by just adding the `channel=live` 
query parameter to the same HTTP API request which would target the [twin](protocol-twinlive.html#twin).

## HTTP Live channel

Ditto supports sending all kind of [Thing commands](protocol-specification-things.html#commands) via
the `live` channel directly to devices.
When sending a `live` command to  a device, the device is responsible for sending a correlated and correct response 
in [Ditto Protocol](protocol-overview.html).

Ditto supports two types of `channel`s for communication.

* [twin](protocol-twinlive.html#twin): The default value of the channel parameter is `twin` 
  to communicate with the persisted **twin** representation.
* [live](protocol-twinlive.html#live): The `channel` parameter can be changed to `live` 
  to communicate with the real device.

{% include note.html content="In order to use the live channel, the device receiving live commands must be able to understand
  and answer in [Ditto Protocol messages](protocol-specification.html) with the
[topic's channel being `live`](protocol-specification-topic.html#live-channel)." %}

When using the `twin` channel, the command is routed to the Ditto backend and handled by the **twin** persistence.
Before using the `live` channel, it is necessary to create the **digital twin** of the device in the 
Ditto backend.

If the `live` channel is used, the command is directly routed to the device. In this case the device is 
responsible for answering the command and sending back a response. In case no response is sent back, the Ditto HTTP API 
is responding with `408 Request Timeout`.  
The default timeout for live commands is `10s` but it can be changed by setting the `timeout` parameter to the 
desired value.

Ditto ensures that the `correlation ID`, `entity ID`, `path` and `command response type` of the command response 
is the same as in the sending command. If this is not the case, Ditto is dropping the response and the caller of the 
HTTP request will get a `408 Request Timeout` with a message that the timeout was caused by an incompatible
command response from the device.

### Permissions for live commands

Sending live commands to devices is restricted by the policy of the thing.  
Thus Ditto ensures that only authorized parties with `WRITE` permission are able to send commands or messages.  
Ditto also filters responses from the device based on the policy. This ensures that the requester only gets the data
where he/she has `READ` permission on.

For retrieve commands, the [authenticated subject](basic-auth.html#authenticated-subjects) needs to have 
(at least partial) `READ` permission at the resource which is requested.  
In case a `RetrieveThing` (via HTTP a `GET /api/2/things/<thing-id>`) command is sent to a real device and the 
requester only has partial `READ` permission on the thing, the response is filtered based on the policy 
and only the fields where `READ` permission is granted are returned.

### Live commands via HTTP API

When using the HTTP API the `channel` parameter can either be specified via HTTP Header or via HTTP query parameter.  
In the examples below both ways are possible to specify the channel parameter.

#### Live command with HTTP Header

```bash
curl -X GET -H 'channel: live' -H 'timeout: 30s' /api/2/things/org.eclipse.ditto:coffeebrewer'
```

#### Live command with HTTP query parameter

```bash
curl -X GET /api/2/things/org.eclipse.ditto:coffeebrewer?channel=live&timeout=30s'
```

## Example

The following section provides an example how to use the HTTP `live` channel together with the Ditto Java client. 

For demonstration purpose, we assume that the thing with ID `org.eclipse.ditto:outdoor-sensor` already exists.

In this example we want to retrieve the live state of the device by sending a `RetrieveThing` command via
the `live` channel directly to the device.

### Permissions to execute the example

For this example, the [authenticated subject](basic-auth.html#authenticated-subjects) has
`READ` and `WRITE` permissions on the complete thing resource to send the command and retrieve the full response.

### Executing the example

When sending a command over the `live` channel to a device, the device needs to take action and send back a response.  
The response from the device is routed back to the initial requester of the `live` command at the HTTP API.
 
In this example the [Ditto Java Client](client-sdk-java.html) acts as device and sends back the response.  
The following snippet shows how to register for retrieve thing live commands and send back a `RetrieveThingResponse`. 

```java
String thingId = "org.eclipse.ditto:outdoor-sensor";
String featureId = "environment-sensor";
Attributes attributes = Attributes.newBuilder()
  .set("location", "outdoor in the woods")
  .build();
Feature feature = ThingsModelFactory.newFeatureBuilder()
  .properties(ThingsModelFactory.newFeaturePropertiesBuilder()
    .set("temperature", 9.2)
    .set("humidity", 56.3)
    .build())
  .withId(featureId)
  .build();

Thing thing = ThingsModelFactory.newThingBuilder()
  .setId(thingId)
  .setFeature(feature)
  .setAttributes(attributes)
  .build();

// initialize the ditto-client and startConsumption() of live commands
DittoClient dittoClient = ... ;

dittoClient.live()
  .forId(thingId)
  .handleRetrieveThingCommandsFunction(retrieveThingLiveCommand -> {
      return retrieveThingLiveCommand.answer()
                  .withResponse(response -> response.retrieved(thing));
  });
```

When the above shown code snippet is running and the following HTTP request is sent out:
```bash
curl -X GET /api/2/things/org.eclipse.ditto:outdoor-sensor?channel=live&timeout=15s
```

The received HTTP response payload should look like this:
```json
{
  "thingId": "org.eclipse.ditto:outdoor-sensor",
  "_namespace": "org.eclipse.ditto",
  "attributes": {
    "location": "outdoor in the woods"
  },
  "features": {
    "environment-sensor": {
      "properties": {
        "temperature": 9.2,
        "humidity": 56.3
      }
    }
  }
}
```


## Feedback?

Please [get in touch](feedback.html) if you have feedback or questions towards this new functionality.

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/> 
The Eclipse Ditto team
