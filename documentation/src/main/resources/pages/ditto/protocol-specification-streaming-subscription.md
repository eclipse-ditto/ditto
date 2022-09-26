---
title: Streaming subscription protocol specification
keywords: protocol, specification, thing, policy, stream, subscription, history, historical
tags: [protocol, history]
permalink: protocol-specification-streaming-subscription.html
---

The [history capabilities](basic-history.html) of the Ditto protocol consists of 3 commands and 4 events that together 
implement the [reactive-streams](https://reactive-streams.org) protocol over any duplex transport layer.
For each streaming subscription request, Ditto acts as the reactive-streams publisher of pages of historical events,
and the client acts as the subscriber.
By reactive-streams means, the client controls how fast pages are delivered to it and may cancel
a request before all results are sent.

While [connections](basic-connections.html) do not expose or require a duplex transport layer,
the streaming subscription protocol is available for them as well: Send commands from client to Ditto via any
[connection source](basic-connections.html#sources). For each command, 0 or more events from Ditto to client
are published to the reply-target of the source.

[ps]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/Flow.Publisher.html#subscribe(java.util.concurrent.Flow.Subscriber)
[ss]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/Flow.Subscriber.html#onSubscribe(java.util.concurrent.Flow.Subscription)
[sn]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/Flow.Subscriber.html#onNext(T)
[sc]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/Flow.Subscriber.html#onComplete()
[se]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/Flow.Subscriber.html#onError(java.lang.Throwable)
[nr]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/Flow.Subscription.html#request(long)
[nc]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/Flow.Subscription.html#cancel()
[n]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/Flow.Subscription.html

For reactive-streams on the JVM, a publisher-subscriber pair is identified by a [Subscription][n] object according
to reference equality.
Similarly, the streaming subscription protocol commands and events of one request are identified by a subscription ID.

Each streaming subscription protocol command or event corresponds to a reactive-streams _signal_ and are bound
by the same rules in the [reactive-streams specification](https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md).

| Reactive-streams signal      | Streaming subscription protocol message topic                                                                         |Type   | Message direction |
|------------------------------|-----------------------------------------------------------------------------------------------------------------------|-------|-------------------|
| [Publisher#subscribe][ps]    | [`<namespace>/<entityName>/<group>/<channel>/streaming/subscribeForPersistedEvents`](#subscribe-for-persisted-events) |Command| Client to Ditto   |
| [Subscription#request][nr]   | [`<namespace>/<entityName>/<group>/<channel>/streaming/request`](#request)                                            |Command| Client to Ditto   |
| [Subscription#cancel][nc]    | [`<namespace>/<entityName>/<group>/<channel>/streaming/cancel`](#cancel)                                              |Command| Client to Ditto   |
| [Subscriber#onSubscribe][ss] | [`<namespace>/<entityName>/<group>/<channel>/streaming/created`](#created)                                            |Event  | Ditto to Client   |
| [Subscriber#onNext][sn]      | [`<namespace>/<entityName>/<group>/<channel>/streaming/next`](#next)                                                  |Event  | Ditto to Client   |
| [Subscriber#onComplete][sc]  | [`<namespace>/<entityName>/<group>/<channel>/streaming/complete`](#complete)                                          |Event  | Ditto to Client   |
| [Subscriber#onError][se]     | [`<namespace>/<entityName>/<group>/<channel>/streaming/failed`](#failed)                                              |Event  | Ditto to Client   |

## Interaction pattern

For one request, the commands from client to Ditto should follow this protocol:
```
subscribe request* cancel?
```
The client should send one ["subscribeForPersistedEvents"](#subscribe-for-persisted-events) command,
followed by multiple ["request"](#request) commands and an optional ["cancel"](#cancel) command.

In response to a ["subscribeForPersistedEvents"](#subscribe-for-persisted-events) command and after each ["request"](#request) command,
Ditto will send 0 or more events to the client according to the following protocol:
```
created next* (complete | failed)?
```
A ["created"](#created) event bearing the subscription ID is always sent.
0 or more ["next"](#next) events are sent according to the amount of results requested 
by the client. A ["complete"](#complete) or ["failed"](#failed) event comes at the
end unless the client sends a ["cancel"](#cancel) command before the results are exhausted.

There is no special event in response to a ["cancel"](#cancel) command.
The client may continue to receive buffered ["next"](#next),
["complete"](#complete) or ["failed"](#failed) events after sending a ["cancel"](#cancel) command.

In addition to the rules of reactive-streams, Ditto guarantees that no ["complete"](#complete) or
["failed"](#failed) event will arrive
before the client expresses its readiness by a first ["request"](#request) command. The reason is to facilitate
concurrency at the client side. Without the extra guarantee, a multi-threaded client would have to process a
["complete"](#complete) or ["failed"](#failed) event in parallel of the preceding ["created"](#created) event.
It would put the burden of sequentialization at the client side and complicate the programming there.

## Commands from Client to Ditto

### Subscribe for persisted events

Sent a ["subscribeForPersistedEvents"](#subscribe-for-persisted-events) command to Ditto to start receiving persisted events as results.
Ditto will always respond with a ["created"](#created) event.

| Field      | Value                                                                                                                                                                                                     |
|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **topic**  | `<namespace>/<entityName>/<group>/<channel>/streaming/subscribeForPersistedEvents`                                                                                                                        |
| **path**   | `/`                                                                                                                                                                                                       |
| **value**  | JSON object specifying the options how the persisted events should be selected. {% include docson.html schema="jsonschema/protocol-streaming-subscription-subscribe-for-persisted-events-payload.json" %} |

The options where to start/stop historical persisted events from can be specified in the `value` field
of a ["subscribeForPersistedEvents"](#subscribe-for-persisted-events) command.
If no options are provided at all, the complete available history for the specified entity is streamed as a result.

### Request

After obtaining a subscription ID from a ["created"](#created) event,
use ["request"](#request) commands to tell Ditto how many results you are prepared to receive.
Ditto will send ["next"](#next) events until all requested results are fulfilled,
the results are exhausted, or an error occurred.

| Field      | Value                                                                                                                                            |
|------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| **topic**  | `<namespace>/<entityName>/<group>/<channel>/streaming/request`                                                                                   |
| **path**   | `/`                                                                                                                                              |
| **value**  | JSON object specifying the demand of results. {% include docson.html schema="jsonschema/protocol-streaming-subscription-request-payload.json" %} |

### Cancel

After obtaining a subscription ID from a ["created"](#created) event,
use a ["cancel"](#cancel) command to stop Ditto from sending more items of the results.
Pages in-flight may yet arrive, but the client will eventually stop receiving
events of the same subscription ID.

| Field      | Value                                                                                                                     |
|------------|---------------------------------------------------------------------------------------------------------------------------|
| **topic**  | `<namespace>/<entityName>/<group>/<channel>/streaming/cancel`                                                             |
| **path**   | `/`                                                                                                                       |
| **value**  | Identifies a streaming subscription. {% include docson.html schema="jsonschema/protocol-streaming-subscriptionid.json" %} |

## Events from Ditto to Client

### Created

To any ["subscribeForPersistedEvents"](#subscribe-for-persisted-events) command, Ditto will always respond with 
a ["created"](#created) event.

| Field      | Value                                                                                                                                                                           |
|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **topic**  | `<namespace>/<entityName>/<group>/<channel>/streaming/created`                                                                                                                  |
| **path**   | `/`                                                                                                                                                                             |
| **value**  | Discloses the ID of a streaming subscription which all subsequent commands should include. {% include docson.html schema="jsonschema/protocol-streaming-subscriptionid.json" %} |

### Next

Each ["next"](#next) event contains one item of the results.
Ditto will not send more ["next"](#next) events for a given subscription ID than the total number of items
requested by previous ["request"](#request) commands.

| Field      | Value                                                                                                                                           |
|------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| **topic**  | `<namespace>/<entityName>/<group>/<channel>/streaming/next`                                                                                     |
| **path**   | `/`                                                                                                                                             |
| **value**  | JSON object containing one item of the results. {% include docson.html schema="jsonschema/protocol-streaming-subscription-next-payload.json" %} |

### Complete

A streaming subscription ends with a ["complete"](#complete) or a ["failed"](#failed) event from Ditto,
or with a ["cancel"](#cancel) command from the client.
Ditto sends a ["complete"](#complete) event when all items of the results are delivered to the client.

| Field      | Value                                                                                                                     |
|------------|---------------------------------------------------------------------------------------------------------------------------|
| **topic**  | `<namespace>/<entityName>/<group>/<channel>/streaming/complete`                                                           |
| **path**   | `/`                                                                                                                       |
| **value**  | Identifies a streaming subscription. {% include docson.html schema="jsonschema/protocol-streaming-subscriptionid.json" %} |

### Failed

A streaming subscription ends with a ["complete"](#complete) or a ["failed"](#failed) event from Ditto,
or with an ["cancel"](#cancel) command from the client.
Ditto sends a ["failed"](#failed) event when an internal error occurred,
or when the client breaches the reactive-streams specification.
It is not possible to ["request"](#request) more items of the streaming subscription results after a ["failed"](#failed) event.

| Field      | Value                                                                                                                                                |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| **topic**  | `<namespace>/<entityName>/<group>/<channel>/streaming/failed`                                                                                        |
| **path**   | `/`                                                                                                                                                  |
| **value**  | JSON object containing the reason for the failure. {% include docson.html schema="jsonschema/protocol-streaming-subscription-failed-payload.json" %} |
