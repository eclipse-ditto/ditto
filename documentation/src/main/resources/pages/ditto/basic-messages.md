---
title: Messages
keywords: router, content-type, correlation-id, feature, message, payload, thing
tags: [model]
permalink: basic-messages.html
---

Messages let you send arbitrary payloads to or from a device through its digital twin. Unlike
[commands](basic-signals-command.html), messages do not change the twin's state -- Ditto routes
them without inspecting their content.

{% include callout.html content="**TL;DR**: Messages are fire-and-forget payloads routed through Ditto to or from
devices. Ditto does not interpret message content, does not guarantee delivery, and does not retain
messages for offline devices." type="primary" %}

## How messages work

You send a message **to** a device to trigger an action (for example, `turnOff`). A device sends
a message **from** itself to report something (for example, `smokeDetected`). Ditto routes the
message to all currently connected and authorized recipients.

{% include image.html file="pages/basic/ditto-messages.png" alt="Ditto Messages" caption="How Ditto acts as router for Messages" max-width=600 %}

{% include note.html content="Ditto has no knowledge of the payload of messages but routes messages between
connected devices." %}

### Important characteristics

Ditto is a message **router**, not a message **broker**:

{% include warning.html content="Ditto offers no message retention. If a device is not connected when a message is
   routed, it will never receive the message." %}

{% include warning.html content="Ditto makes no statement about message QoS. Messages are routed **at most once**." %}

{% include warning.html content="Ditto delivers messages in a fan-out style. If the same credentials are connected
   twice, both connections receive the message (assuming authorization permits it)." %}

## Message elements

Every message requires these fields:

| Field | Description |
|-------|-------------|
| **Direction** | `to` (toward the device) or `from` (from the device) |
| **Thing ID** | The ID of the `Thing` that should receive or is sending the message |
| **Subject** | A custom topic string (for example, `turnOff`, `smokeDetected`) |

Optional fields:

| Field | Description |
|-------|-------------|
| **Feature ID** | Target a specific [Feature](basic-feature.html) instead of the whole Thing |
| **content-type** | MIME type of the payload (for example, `application/json`) |
| **correlation-id** | Required if you want Ditto to route a response back to the sender |

## Payload

A message can carry any payload. Since Ditto does not interpret the content, you choose the
serialization format and content-type that fits your solution.

## Sending and receiving messages

### Sending

You can send messages through:

* The [HTTP API](http-api-doc.html#/Messages) -- fire-and-forget or blocking (waits for a response)
* The [WebSocket API](httpapi-protocol-bindings-websocket.html) -- as [Ditto Protocol](protocol-overview.html) messages
* [Connection sources](basic-connections.html#sources) -- incoming Ditto Protocol messages via a managed connection

Sending requires `WRITE` permission on the Thing (see [Permissions](#permissions)).

### Receiving

You can receive messages through:

* The [WebSocket API](httpapi-protocol-bindings-websocket.html) -- as Ditto Protocol messages
* [Server Sent Events (SSE)](httpapi-sse.html#thing-messages)
* [Connection targets](basic-connections.html#target-topics-and-filtering) -- subscribing for "Thing messages"

Receiving requires `READ` permission on the Thing. Every connected client with the correct
permissions receives the message. If multiple consumers respond, only the first response is
routed back to the original sender.

### Filtering messages

When subscribing for messages, you can filter to receive only the ones you care about.

**By namespace:** Provide a comma-separated list of namespaces:

```text
namespaces=org.eclipse.ditto.one,org.eclipse.ditto.two
```

**By RQL expression:** Use an [RQL expression](basic-rql.html) with
[enriched](basic-enrichment.html) Thing state or Ditto Protocol field
[placeholders](basic-placeholders.html#scope-rql-expressions-when-filtering-for-ditto-protocol-messages):

* `topic:subject` -- filter by message subject
* `resource:path` -- filter by the affected resource path

## Responding to messages

Messages are stateless, so there is no built-in request-response mechanism. To route a response
back to the sender, use the same `correlation-id` in the reply message.

## Permissions

To control message access, configure the `message:/` resource in the Thing's
[Policy](basic-policy.html#message-resources):

* `READ` on `message:/` -- receive all messages for the Thing
* `WRITE` on `message:/` -- send messages to the Thing
* Fine-grained control -- grant access to specific subjects or features (for example,
  `message:/features/lamp/inbox/messages/turnOff`)

### Claim messages

Claim messages are a special case for gaining initial access to a Thing:

* The subject is always `claim`
* You do **not** need `WRITE` permission to send a claim message
* Ditto forwards it to all subscribers registered for claim messages on that Thing
* The receiver decides whether to grant access (for example, by updating the Policy)

## Further reading

* [Policies](basic-policy.html) -- configure message permissions
* [Change Notifications](basic-changenotifications.html) -- subscribe to state change events
* [Messages HTTP API](http-api-doc.html#/Messages) -- HTTP endpoint reference
* [Ditto Protocol messages specification](protocol-specification-things-messages.html) -- wire format
