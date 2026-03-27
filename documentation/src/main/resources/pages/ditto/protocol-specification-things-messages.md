---
title: Things - Messages protocol specification
keywords: protocol, specification, messages, thing
tags: [protocol]
permalink: protocol-specification-things-messages.html
---

The Messages protocol lets you send, receive, and respond to custom messages between applications and devices through Ditto.

{% include callout.html content="**TL;DR**: Messages carry arbitrary payloads over the live channel. You address them to a Thing's inbox (sending to a device) or outbox (sending from a device), and correlate request/response pairs via `correlation-id`." type="primary" %}

## Overview

Messages carry custom payloads and can be answered by correlated response messages. If you want to learn more about the concepts behind messaging, see the [Messages page](basic-messages.html).

{% include tip.html content="If you only need to send Messages, but don't need to receive
 or respond, you could also use the [HTTP Messages API](httpapi-messages.html)" %}

## How it works

### Protocol envelope for Messages

Three fields have special meaning for Messages:

* **topic**: `{namespace}/{thingId}/things/live/messages/{messageSubject}`
* **path**: `{addressedPartOfThing}/{mailbox}/messages/{messageSubject}`
* **headers**: must include `content-type`

The `topic` uses the Thing's namespace and ID, with `messages` as the criterion and `live` as the channel. The `messageSubject` describes the message and must conform to [RFC-3986](https://tools.ietf.org/html/rfc3986).

Examples of valid topics:
* `org.eclipse.ditto/smartcoffee/things/live/messages/ask/question`
* `com.example/smarthome/things/live/messages/turnoff`

The `path` also contains the `messageSubject`. The `mailbox` is either `inbox` (message sent to a Thing) or `outbox` (message sent from a Thing). The `addressedPartOfThing` specifies which part of the Thing you address -- `/features/water-tank` targets the water-tank Feature, while an empty prefix targets the whole Thing.

Examples of valid paths:
* `/inbox/messages/ask/question`
* `/features/lights/inbox/messages/turnoff`
* `/features/smokedetector/outbox/messages/smokedetected`

## Examples

### Sending a Message to a Thing

Send a message to the inbox of the receiving entity. Here is a message asking the Thing `smartcoffee` how it is doing:

```json
{
    "topic": "org.eclipse.ditto/smartcoffee/things/live/messages/ask",
    "headers": {
        "content-type": "text/plain",
        "correlation-id": "a-unique-string-for-this-message"
    },
    "path": "/inbox/messages/ask",
    "value": "Hey, how are you?"
}
```

{% include tip.html content="If you want to receive the response to
a Message, make sure to always send a `correlation-id` with it." %}

A response from the coffee machine might look like:

```json
{
    "topic": "org.eclipse.ditto/smartcoffee/things/live/messages/ask",
    "headers": {
        "correlation-id": "a-unique-string-for-this-message",
        "content-type": "text/plain"
    },
    "path": "/inbox/messages/ask",
    "value": "I do not know, since i am only a coffee machine.",
    "status": 418
}
```

The response shares the same `topic` and `correlation-id` as the original message. The `status` field uses [HTTP status code](protocol-specification.html#status) semantics.

### Receiving a Message

To receive Messages using the WebSocket binding, you need to:

1. Have an open WebSocket connection to Ditto
2. Send the [WebSocket binding message](httpapi-protocol-bindings-websocket.html#request-events) `START-SEND-MESSAGES`
3. Be [authorized](basic-auth.html) to receive Messages for the Thing

{% include note.html content="We encourage you to play around with the examples. You can use
the WebSocket binding to do so. Make sure the Thing you are sending Messages to is existing and has
 the correct access rights." %}

Example JavaScript code:

```javascript
// connect to the WebSocket
var websocket = new WebSocket('ws://ditto:ditto@localhost:8080/ws/1');
websocket.onmessage(function(message) {
    console.log('received message data: ' +  message.data);
});
websocket.onopen(function(ws) {
    // register for receiving messages
    ws.send('START-SEND-MESSAGES');
});
```

### Responding to a Message

To respond, reuse the original message's `topic` and `correlation-id`, and change the path from `inbox` to `outbox`:

```javascript
createTextResponse = function(originalMessage, payload, statusCode) {
    var topic = originalMessage.topic;
    var correlationId = originalMessage.headers["correlation-id"];
    var outboxPath = originalMessage.path.replace("inbox", "outbox");

    return {
      "topic": topic,
      "headers": {
          "correlation-id": correlationId,
          "content-type": "text/plain"
      },
      "path": outboxPath,
      "status": statusCode,
      "value": payload
    };
};
```

### Sending Messages to Features

When you send Messages to or from Features, only the `path` and an additional `feature-id` header change:

```json
{
    "topic": "org.eclipse.ditto/smartcoffee/things/live/messages/heatUp",
    "headers": {
        "content-type": "text/plain",
        "correlation-id": "a-unique-string-for-this-message"
    },
    "path": "/features/water-tank/inbox/messages/heatUp",
    "value": "47"
}
```

### Claim Messages

Claim Messages let a user request access to a Thing. They work like standard Messages with these differences:

- The message subject is always `claim`
- They can only be sent to a Thing (not to Features)
- You do not need `WRITE` permission to send a Claim Message

Because claiming bypasses normal write permissions, the receiving device must carefully verify incoming Claim Messages before granting access.

```json
{
    "topic": "org.eclipse.ditto/smartcoffee/things/live/messages/claim",
    "headers": {
        "content-type": "text/plain",
        "correlation-id": "a-unique-string-for-this-claim-message"
    },
    "path": "/inbox/messages/claim",
    "value": "some-claiming-secret"
}
```

After verifying the claim, the device grants access by updating the Policy and responds with status `200` or `204`. To reject the claim, respond with a non-success status code.

## Further reading

- [Messages](basic-messages.html) -- messaging concepts
- [HTTP Messages API](httpapi-messages.html) -- sending messages via HTTP
- [Things specification](protocol-specification-things.html) -- Thing commands reference
