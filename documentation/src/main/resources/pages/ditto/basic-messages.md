---
title: Messages
keywords: router, content-type, correlation-id, feature, message, payload, thing
tags: [model]
permalink: basic-messages.html
---

Messages *do not affect* the state of a digital twin or an actual device.
Therefore, Ditto does not handle messages like [commands](basic-signals-command.html): there are no responses which are
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

Ditto messages always have to have at least these elements:
* **Direction**: *to* / *from*,
* **Thing ID**: the ID of the `Thing` (actual device) which should receive/send the message and
* **Subject**: the custom subject/topic.

Additionally, they can contain more information:
* **Feature ID**: if a message should be addressed to the [Feature](basic-feature.html) of a Thing, this specifies 
  its ID.
* **content-type**: defines which content-type the optional payload has.
* **correlation-id**: Ditto can route message responses back to the issuer of a message. Therefore, a correlation-id has
  to be present in the message.


## Payload

A message can optionally contain a payload. As Ditto does neither have to understand the message nor its payload, the 
content-type and serialization is arbitrary.


## APIs

Messages can be sent via:
* the [WebSocket API](httpapi-protocol-bindings-websocket.html) as [Ditto Protocol](protocol-overview.html) messages,
* the [HTTP API](httpapi-overview.html) either as "fire and forget" messages or, when expecting a response, in a
  blocking way at the [Messages HTTP API endpoint](http-api-doc.html#/Messages)
* Ditto managed [connection sources](basic-connections.html#sources) when receiving messages in [Ditto Protocol](protocol-overview.html)
  via the source

Messages can be received via:
* the [WebSocket API](httpapi-protocol-bindings-websocket.html) as [Ditto Protocol](protocol-overview.html) messages
* the [Server Sent Event API](httpapi-sse.html#subscribe-for-messages-for-a-specific-thing)
* Ditto managed [connection targets](basic-connections.html#target-topics-and-filtering) when "subscribing for Thing messages"


## Receiving Messages

To be able to receive Messages for a Thing, you need to have `READ` access on that Thing.
When a Message is sent to or from a Thing, **every** connected WebSocket, [SSE](httpapi-sse.html) or 
[connection target](basic-connections.html#targets) with the correct access rights will receive the Message.

If there is more than one response to a message received by multiple consumers, only the
first response will be routed back to the initial issuer of a Message.

### Filtering when subscribing for messages

In order to not receive all messages an [authenticated subject](basic-auth.html#authenticated-subjects) is entitled to 
receive when subscribing for messages, but to filter for specific criteria, messages may be filtered on the Ditto 
backend side before they are sent to a message receiver.

#### Filtering messages by namespaces

Filtering messages may be done based on a namespace name. Each Ditto [Thing](basic-thing.html) has an ID containing a 
namespace (see also the conventions for a [Thing ID](basic-thing.html#thing-id)).

By providing the `namespaces` filter, a comma separated list of which namespaces to include in the result, only Things
in namespaces of interest are considered and thus only messages of these Things are published to the message receiver.

For example, one would only subscribe for messages occurring in 2 specific namespaces by defining:
```
namespaces=org.eclipse.ditto.one,org.eclipse.ditto.two
```

#### Filtering messages by RQL expression

If filtering by namespaces is not sufficient, Ditto also allows to provide an [RQL expression](basic-rql.html)
specifying:

* an [enriched](basic-enrichment.html) Thing state based condition determining when messages should be delivered and when not
* a filter based on the fields of the [Ditto Protocol](protocol-specification.html) message which should be filtered,
  e.g.:
    * using the `topic:subject` placeholder as query property, filtering for the message's subject 
      and other filter options on the [Ditto Protocol topic](protocol-specification-topic.html) can be done
    * using the `resource:path` placeholder as query property, filtering based on the affected
      [Ditto Protocol path](protocol-specification.html#path) of a Ditto Protocol message can be done, e.g. targeting a 
      specific message addressed to a certain feature ID
    * for all supported placeholders, please refer to the
      [placeholders documentation](basic-placeholders.html#scope-rql-expressions-when-filtering-for-ditto-protocol-messages)


## Sending Messages

If you want to send a Message to or from a Thing, you need `WRITE` permissions on that Thing.
Every WebSocket or [connection target](basic-connections.html#targets) that is able to receive Messages for the 
Thing (`READ` permission), will receive your message.


## Responding to Messages

Since messages are stateless there is no *direct* response to a Message.

For Ditto to be able to route the response of a Message back to the issuer, the
correlation-ids need to match. E.g. when the sender uses correlation-id `random-aa98s`,
any receiver can reply by using the same correlation-id `random-aa98s`.


## Permissions

### API version 2

Permissions in API version 2 can be defined fine-grained. In order to be able to receive all Messages of a Thing,
you need `READ` permission for the `message:/` resource in the used [Policy](basic-policy.html#message).<br/>
There can however be specified that you may only receive specific Messages (with a defined subject), also
expressed via [Policy entry](basic-policy.html#message).<br/>
The same applies for being able to send Messages, here a `WRITE` permission is required either globally for
all messages via the `message:/` resource or only for specific ones.

There is one sole exception, which are [Claim Messages](#claim-messages). You do
not need the access rights for sending them.


## Claim Messages

A Claim Message is used to gain access to a Thing. For this purpose a Claim Messages has two specifics:
* the predefined message subject is always *claim*
* you do not require `WRITE` permission to send a Claim Message

Apart from that the Claim Message is handled like a standard Message. It is forwarded to all Ditto Protocol bindings 
that registered for Claim Messages of the specific Thing. The decision whether to grant access (by setting permissions) 
is completely up to the receiver of the Claim Message e.g. after verifying the payload of the Message.
