---
title: Messages
keywords: broker, content-type, correlation-id, feature, message, payload, thing
tags: [model]
permalink: basic-messages.html
---

Messages *do not affect* the state of a Digital Twin or an actual device.
Therefore Ditto does not handle messages like [commands](basic-signals-command.html): there are no responses which are
produced by Ditto and no events which are emitted for messages.

  {%
    include note.html content="Ditto has no knowledge of the payload of messages but merely routes messages between
    connected devices."
  %}

Messages provide the possibility to send something **to** or **from** an actual device using an arbitrary subject/topic.
They contain a custom payload with a custom content-type, so you can choose what content best 
fits your solution.

Expressed differently, messages
* **to** devices are operations which should trigger an action on a device (e.g. with a subject `turnOff`),
* **from** devices are events/alarms which are emitted by devices (e.g. with a subject `smokeDetected`).

{% include image.html file="pages/basic/ditto-messages.png" alt="Ditto Messages" caption="How Ditto acts as broker for Messages" max-width=600 %}

## Elements

Ditto messages always have to have at least this elements:
* **Direction**: *to* / *from*,
* **Thing ID**: the ID of the `Thing` (actual device) which should receive/send the message and
* **Subject**: the custom subject/topic.

Additionally they can contain more information:
* **Feature ID**: if a message should be addressed to the [Feature](basic-feature.html) of a Thing, this specifies 
  its ID.
* **content-type**: defines which content-type the optional payload has.
* **correlation-id**: Ditto can route message responses back to the issuer of a message. Therefore a correlation-id has
  to be present in the message.

## Payload

A message can optionally contain a payload. As Ditto does neither have to understand the message nor its payload, the 
content-type and serialization is arbitrary.

## APIs

Messages can be sent via
* the [WebSocket API](protocol-bindings-websocket.html) as [Ditto Protocol](protocol-overview.html) messages,
* the [HTTP API](httpapi-overview.html) either as "fire and forget" messages or, when expecting a response, in a
  blocking way at the [Messages HTTP API endpoint](http-api-doc.html#/Messages)

Messages can, however, be received only via the [WebSocket API](protocol-bindings-websocket.html) as
[Ditto Protocol](protocol-overview.html) messages.

## Receiving Messages

To be able to receive Messages for a Thing, you need to have `READ` access on that Thing.
When a Message is sent to or from a Thing, **every** client with the correct
access rights will receive the Message. If there is more than one response, only the
first one will be routed back to the initial issuer of a Message.

{% include note.html content="Currently, Messages can only be received using the
 Ditto Protocol WebSocket binding" %}

## Sending Messages

If you want to send a Message to or from a Thing, you need `WRITE` permissions on that Thing.
Every WebSocket client that is able to receive Messages for the Thing (`READ` permission), will receive your Message.

## Responding to Messages

Since WebSocket messages are stateless there is no *direct* response to a Message.
For Ditto to be able to route the response of a Message back to the issuer, the
correlation-ids need to match. E.g. when the sender uses correlation-id `random-aa98s`,
any receiver can reply by using the same correlation-id `random-aa98s`.

{% include note.html content="Currently, you can only respond to Messages using the
 Ditto Protocol WebSocket binding" %}

## Permissions

Permissions are simple for the Message API. If you want to receive Messages of a Thing,
you need `READ` access on the Thing. To be able to send Messages to or from a Thing
you need to have `WRITE` permissions.

There is one sole exception, which are [Claim Messages](#claim-messages). You do
not need access rights for sending them.

## Claim Messages

TODO: document 
