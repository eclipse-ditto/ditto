---
title: "E2E acknowledgment"
published: true
permalink: 2020-10-31-end-2-end-acknowledgment.html
layout: post
author: yannic_klem
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---
# E2E acknowledgement using Eclipse Ditto
By adding the new [acknowledgements feature](basic-acknowledgements.html) to Ditto, it is now possible to provide an end to end QoS (quality of service) with level 1.
Previously the connectivity service of ditto did accept all incoming messages immediately as soon as it received the message.
This behaviour can now be controlled by a Ditto header called [`requested-acks`](basic-acknowledgements.html#requesting-acks).

In this blog post I will provide a few examples for how to use this new feature in the following scenarios:

1. Device publishes a change of its state and doesn't care about whether the event regarding this change will be received by any subscriber or not. This is the "fire and forget" scenario. 
2. Device publishes a change of its state and wants to be sure it is persisted in Ditto. This is the "processed in Ditto" scenario.
3. Device publishes a change of its state and wants to be sure an application connected to Eclipse Ditto will receive the event regarding this change of the state. This is the "notification" scenario.
4. Device publishes a change of its state and wants to be notified when a subscriber was not able to receive the event regarding this change. This is the "tracked notification" scenario.
5. Device sends a live message and wants to be sure that it will eventually receive a response. This is the "Long running live message" scenario.
6. Device sends a live message and wants to either receive the response within a given timeout or never.

## Scenario 1 - Fire and Forget
This is the simplest scenario of all, since the change can be published in a fire and forget semantics. 
In this scenario the device will send the modification command containing the `requested-acks` header with a value of `[]` and the `response-required` header with value of `false`.

Example:
```json
{
  "topic": "namespace/name/things/twin/commands/modify",
  "headers": {
    "response-required": false,
    "requested-acks": []
  },
  "path": "/features/lightSwitch/properties/status",
  "value": "on"
}
```

For this case the connectivity service will immediately acknowledge the incoming message at the messaging system and then continues to process the command.

It doesn't matter if the command could be processed successfully or if any subscriber received an event for this change.

## Scenario 2 - Processed in Ditto
For this scenario the device wants to be sure its change will be properly persisted in Ditto.

The command needs to define the following headers:
* `response-required=false`
* `requested-acks=["twin-persisted"]`
 
Example:
```json
{
  "topic": "namespace/name/things/twin/commands/modify",
  "headers": {
    "response-required": false,
    "requested-acks": ["twin-persisted"]
  },
  "path": "/features/lightSwitch/properties/status",
  "value": "on"
}
```

For this case the connectivity service will wait until the modification will be properly persisted in Ditto before acknowledging the incoming message at the messaging system.

If the device published this command for example via an AMQP broker, this will cause the broker to redeliver the command to Ditto if the acknowledgment fails.
Please be aware that a redelivery will only be requested for the following error status codes:
* 408 (Request timed out)
* 424 (Dependency Failure)
* All kinds of 5xx Status codes

In this scenario it does matter if the command could be processed successfully, but it's still not relevant if any subscriber received an event for this change. 

## Scenario 3 - Notification scenario

For this scenario the device wants to be sure another system will be notified about the change of its state. This could be for example an alarming system which wants to be sure a backend application receives the information that the alarm was triggered.

A prerequisite for this is that any kind of connection exists that publishes the event to the backend application and declares a user defined acknowledgement label. This can be a Websocket connection or any kind of Ditto Connection types which can be found [here](connectivity-overview.html).

For this example we expect the event to be forwarded by an HTTP connection which declared the following acknowledgement label as issued acknowledgement of the target: `d45d4522-142e-4057-ae87-8969343a3ddc:backend-processed`.

The UUID prefix in this case is the ID of the HTTP connection and the`backend-process` part is a custom label, defined by the user.

The command needs to define the following headers:
* `response-required=false`
* `requested-acks=["d45d4522-142e-4057-ae87-8969343a3ddc:backend-processed"]`
* `timeout=20s` (optional. Default is 10s.)

Example:
```json
{
  "topic": "namespace/name/things/twin/commands/modify",
  "headers": {
    "response-required": false,
    "requested-acks": ["d45d4522-142e-4057-ae87-8969343a3ddc:backend-processed"],
    "timeout": "30s"
  },
  "path": "/features/alarm/properties/status",
  "value": "on"
}
```

For this case the connectivity service will wait until the HTTP request, which forwards the event regarding the thing change will be finished before acknowledging the incoming message at the messaging system.

The status code of the HTTP response will in this case determine if the message will be acknowledged successfully or not and if a redelivery will be requested or not.
All kinds of 2xx status codes will lead to a successful acknowledgement at the messaging system. All other status codes will lead to a failed acknowledgement at the messaging system and for the following status codes a redelivery will be requested:
* 408 (Request timed out)
* 424 (Dependency Failure)
* All kinds of 5xx St atus codes

In this scenario it is ensured a specified subscriber will receive an event for this change.

## Scenario 4 - Tracked notification scenario

For this scenario the device wants to know when a system could not be notified about the change of its state. This could be for example an alarming system which wants to be sure a backend application receives the information of if not tries to send an SMS as notification.

This scenario is mostly like scenario 3, but n eeds to set the `response-required` header to `true` and it's required to [configure the reply-target of the source](basic-connections.html#source-reply-target) to also expect "nack"  responses.

Example:
```json
{
  "topic": "namespace/name/things/twin/commands/modify",
  "headers": {
    "response-required": true,
     "requested-acks": ["d45d4522-142e-4057-ae87-8969343a3ddc:backend-processed"]
  },
  "path": "/features/alarm/properties/status",
  "value": "on"
}
```

In this case the device will receive an acknowledgement response containing the status code and payload of the response of the backend application.
Based on this the device can decide how to handle the situation.
It is suggested to publish the modification command with QoS 0 in this case because the device handles the result of the E2E acknowledgement. With QoS 1 brokers would usually redeliver the message to Ditto.

If the HTTP endpoint of the backend application responds with the following response:
headers:
```
"content-type": "application/json"
```
body: 
```json
{
  "errorCode": "notification.smartphone.failed",
  "message": "Could not notify smartphone."
}
```

status code: `424`

The response received at the device would look like this:

```json
{
  "topic": "namespace/name/things/twin/acks/d45d4522-142e-4057-ae87-8969343a3ddc:backend-processed",
  "headers": {
    "response-required": false,
    "requested-acks": ["d45d4522-142e-4057-ae87-8969343a3ddc:backend-processed"],
    "timeout": "30s",
    "content-type": "application/json"
  },
  "path": "/",
  "value": {
    "errorCode": "notification.smartphone.failed",
    "message": "Could not notify smartphone."
  },
  "status": 424
}
```
## Scenario 5 - Long running live message scenario
For this scenario the device is going to ask for an information which it needs eventually. Let's say the device asks for the endpoint where it should download the new firmware from. It's not required that this response arrives within a given time. It's just required to eventually arrive at the device and after it was receive the device can signal the user, that it is ready to download the firmware.

The headers of the live message should have the following values:

* `response-required=true`
* `requested-acks=["live-response"]`

Example Message:
```json
{
    "topic": "namespace/name/things/live/messages/car-enter",
    "headers": {
      "response-required": true,
      "requested-acks": ["live-response"],
      "content-type": "text/plain",
      "timeout": "5s"
    },
    "path": "/outbox/messages/car-enter",
    "value": "FN IB 1337"
}
```

By requesting the acknowledgement `live-response` the connectivity service will wait until the response for the live message arrived for 5s before acknowledging the incoming message at the broker and will request a redelivery if the response did not arrive within this timeout.
This will repeat until either the broker discards the message or the response arrives in the specified timeout.
That way it is guaranteed that the device will eventually receive the response.

## Scenario 6 - Asking for required information scenario
For this scenario the device is going to ask for an information which it needs right now to proceeds its current task. Let's say the device asks if it should allow a car with a license plate it detected to drive on the property by opening the barrier.
It could be possible to ask for that information, so the barrier opens automatically, but providing a fallback mechanism like entering a code directly at the device if this response does not arrive within time.

The headers of the live message should have the following values:

* `response-required=true`
* `requested-acks=[]`

Example Message:
```json
{
    "topic": "namespace/name/things/live/messages/car-enter",
    "headers": {
      "response-required": true,
      "requested-acks": [],
      "content-type": "text/plain",
      "timeout": "5s"
    },
    "path": "/outbox/messages/car-enter",
    "value": "FN IB 1337"
}
```
By requesting explicitly not requesting any acknowledgement but still requiring a response, the connectivity service will immediately acknowledge the incoming message at the broker. The device will then either receive the response within the specified timeout or never.
So the device can provide its alternative options to open the barrier after 5 seconds.

## We embrace your feedback

I hope I could demonstrate the power of the new acknowledgement feature properly and could make it clear how it can be used.
Maybe you did recognize some of your use cases in the given examples or maybe you have another use case which can or cannot be solved by this feature.
We would love to get your feedback.
  
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
