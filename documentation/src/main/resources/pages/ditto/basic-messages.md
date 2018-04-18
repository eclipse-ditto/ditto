---
title: Messages
keywords: router, content-type, correlation-id, feature, message, payload, thing
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

Expressed differently, Messages
* **to** devices are operations which should trigger an action on a device (e.g. with a subject `turnOff`),
* **from** devices are events/alarms which are emitted by devices (e.g. with a subject `smokeDetected`).

{% include image.html file="pages/basic/ditto-messages.png" alt="Ditto Messages" caption="How Ditto acts as router for Messages" max-width=600 %}


## Characteristics of Messages
  
Eclipse Ditto is not a message broker and does not want to offer features a message broker does.

It can be seen as a message router which:
* accepts messages via 2 APIs ([HTTP](httpapi-messages.html) and 
  [Ditto Protocol](protocol-specification-things-messages.html), e.g. via [WebSocket binding](httpapi-protocol-bindings-websocket.html))
* checks for **currently connected** interested parties whether they may receive a specific Message 
  (performs [authorization checks](basic-auth.html#authorization))
* routes the Message and reply Messages in between connected clients 
  
  {% include warning.html content="Ditto offers no message retention. If a device isn't connected when a Message should 
     be routed, it will never receive the Message." 
  %}
  
  {% include warning.html content="Ditto makes no statement about Message QoS. Messages are routed **at most once**." 
  %}
  
  {% include warning.html content="Ditto does deliver messages only in \"fan out\" style,
     if the same credentials are connected twice, both connections will receive Messages if the credential is authorized
     to read a Message." 
  %}


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
* the [WebSocket API](httpapi-protocol-bindings-websocket.html) as [Ditto Protocol](protocol-overview.html) messages,
* the [HTTP API](httpapi-overview.html) either as "fire and forget" messages or, when expecting a response, in a
  blocking way at the [Messages HTTP API endpoint](http-api-doc.html#/Messages)

Messages can, however, be received only via the [WebSocket API](httpapi-protocol-bindings-websocket.html) as
[Ditto Protocol](protocol-overview.html) messages.


## Receiving Messages

To be able to receive Messages for a Thing, you need to have `READ` access on that Thing.
When a Message is sent to or from a Thing, **every** connected WebSocket with the correct
access rights will receive the Message. If there is more than one response, only the
first one will be routed back to the initial issuer of a Message.

{% include note.html content="Currently, Messages can only be received using the
 Ditto Protocol WebSocket binding" %}


## Sending Messages

If you want to send a Message to or from a Thing, you need `WRITE` permissions on that Thing.
Every WebSocket that is able to receive Messages for the Thing (`READ` permission), will receive your message.


## Responding to Messages

Since WebSocket messages are stateless there is no *direct* response to a Message.<br/>
For Ditto to be able to route the response of a Message back to the issuer, the
correlation-ids need to match. E.g. when the sender uses correlation-id `random-aa98s`,
any receiver can reply by using the same correlation-id `random-aa98s`.

{% include note.html content="Currently, you can only respond to Messages using the
 Ditto Protocol WebSocket binding" %}


## Permissions

### API version 1

Permissions in API version 1 are simple for the Message API. If you want to receive Messages of a Thing,
you need `READ` access on the Thing. To be able to send Messages to or from a Thing
you need to have `WRITE` permissions.

There is one sole exception, which are [Claim Messages](#claim-messages). You do
not need access rights for sending them.

### API version 2

Permissions in API version 2 can be more fine grained. In order to be able to receive all Messages of a Thing,
you need `READ` permission for the `message:/` resource in the used [Policy](basic-policy.html#message).<br/>
There can however be specified that you may only receive specific Messages (with a defined subject), also
expressed via [Policy entry](basic-policy.html#message).<br/>
The same applies for being able to send Messages, here a `WRITE` permission is required either globally for
all messages via the `message:/` resource or only for specific ones.

There is one sole exception, which are [Claim Messages](#claim-messages). You do
not need access rights for sending them.


## Claim Messages

A Claim Message is used to gain access to a Thing. For this purpose a Claim Messages has two specifics:
* the predefined message subject is always *claim*
* you do not require `WRITE` permission to send a Claim Message

Apart from that the Claim Message is handled like a standard Message. It is forwarded to all Ditto Protocol bindings 
that registered for Claim Messages of the specific Thing. The decision whether to grant access (by setting permissions) 
is completely up to the receiver of the Claim Message e.g. after verifying the payload of the Message.
