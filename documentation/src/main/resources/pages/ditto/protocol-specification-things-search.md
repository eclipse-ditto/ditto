---
title: Things - Search protocol specification
keywords: protocol, specification, search, thing
tags: [protocol, search]
permalink: protocol-specification-things-search.html
---

The [search aspect](basic-search.html) of the Ditto protocol consists of 3 commands and 4 events that together 
implement the [reactive-streams](https://reactive-streams.org) protocol over any duplex transport layer.
For each search request, Ditto acts as the reactive-streams publisher of pages of search results,
and the client acts as the subscriber.
By reactive-streams means, the client controls how fast pages are delivered to it and may cancel
a search request before all results are sent.

While [connections](basic-connections.html) do not expose or require a duplex transport layer,
the search protocol is available for them as well: Send commands from client to Ditto via any
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
Similarly, the search protocol commands and events of one search query are identified by a subscription ID.

Each search protocol command or event corresponds to a reactive-streams _signal_ and are bound
by the same rules in the [reactive-streams specification](https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md).

| Reactive-streams signal      | Search protocol message topic                     |Type   | Message direction |
|------------------------------|---------------------------------------------------|-------|-------------------|
| [Publisher#subscribe][ps]    | [`_/_/things/twin/search/subscribe`](#subscribe)  |Command| Client to Ditto   |
| [Subscription#request][nr]   | [`_/_/things/twin/search/request`](#request)      |Command| Client to Ditto   |
| [Subscription#cancel][nc]    | [`_/_/things/twin/search/cancel`](#cancel)        |Command| Client to Ditto   |
| [Subscriber#onSubscribe][ss] | [`_/_/things/twin/search/created`](#created)      |Event  | Ditto to Client   |
| [Subscriber#onNext][sn]      | [`_/_/things/twin/search/next`](#next)      |Event  | Ditto to Client   |
| [Subscriber#onComplete][sc]  | [`_/_/things/twin/search/complete`](#complete)    |Event  | Ditto to Client   |
| [Subscriber#onError][se]     | [`_/_/things/twin/search/failed`](#failed)        |Event  | Ditto to Client   |

## Interaction pattern

For one search query, the commands from client to Ditto should follow this protocol:
```
subscribe request* cancel?
```
The client should send one ["subscribe"](#subscribe) command,
followed by multiple ["request"](#request) commands and an optional ["cancel"](#cancel) command.

In response to a ["subscribe"](#subscribe) command and after each ["request"](#request) command,
Ditto will send 0 or more events to the client according to the following protocol:
```
created next* (complete | failed)?
```
A ["created"](#created) event bearing the subscription ID is always sent.
0 or more ["next"](#next) events are sent according to the amount of search results and the number of pages requested 
by the client. A ["complete"](#complete) or ["failed"](#failed) event comes at the
end unless the client sends a ["cancel"](#cancel) command before the search results are exhausted.

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

### Subscribe

Sent a ["subscribe"](#subscribe) command to Ditto to start receiving search results.
Ditto will always respond with a ["created"](#created) event.

| Field      | Value                   |
|------------|-------------------------|
| **topic**  | `_/_/things/twin/search/subscribe`     |
| **path**   | `/`     |
| **fields** | Contains a comma separated list of fields, to describe which things to be included in the search results. |
| **value**  | JSON object specifying the search query. {% include docson.html schema="jsonschema/protocol-search-subscribe-payload.json" %} |

The standard [filter](basic-rql.html#rql-filter),  [options](basic-search.html#sorting-and-paging-options)
and the [namespaces](basic-search.html#namespaces) can be specified in the `value` field
of a ["subscribe"](#subscribe) command.
They have identical semantics and default values as other search APIs.
In particular:
- When given in the `options` field, `size(<count>)` limits the number of search results delivered
  in one ["next"](#next) event to `<count>` items. The default value of `<count>` is 25 and the maximum value is 200.
- When given in the `options` field, `sort(<+"-><property1>, ...)` sets the order of search results.
  If not given, the default `sort(+thingId)` is used.

The paging options `cursor` and `limit` of the [HTTP-API](httpapi-search.html) are not supported here, because
they are not meaningful for the search protocol. For the HTTP-API, those options are for iterating through large
numbers of search results over many HTTP requests in a stateless manner.
The search protocol is not stateless and does not require the client to keep track of any cursor or offset;
results of any size are streamed over an arbitrarily long period of time.

### Request

After obtaining a subscription ID from a ["created"](#created) event,
use ["request"](#request) commands to tell Ditto how many pages of search results you are prepared to receive.
Ditto will send ["next"](#next) events until all requested pages are fulfilled,
the search results are exhausted, or an error occurred.

| Field      | Value                   |
|------------|-------------------------|
| **topic**  | `_/_/things/twin/search/request`     |
| **path**   | `/`     |
| **value**  | JSON object specifying the number of pages to request. {% include docson.html schema="jsonschema/protocol-search-request-payload.json" %} |

### Cancel

After obtaining a subscription ID from a ["created"](#created) event,
use a ["cancel"](#cancel) command to stop Ditto from sending more pages of the search results.
Pages in-flight may yet arrive, but the client will eventually stop receiving
events of the same subscription ID.

| Field      | Value                   |
|------------|-------------------------|
| **topic**  | `_/_/things/twin/search/cancel`     |
| **path**   | `/`     |
| **value**  | Identifies a search subscription. {% include docson.html schema="jsonschema/protocol-search-subscriptionid.json" %} |

## Events from Ditto to Client

### Created

To any ["subscribe"](#subscribe) command, Ditto will always respond with a ["created"](#created) event.

| Field      | Value                   |
|------------|-------------------------|
| **topic**  | `_/_/things/twin/search/created`     |
| **path**   | `/`     |
| **value**  | Discloses the ID of a search subscription which all subsequent commands should include. {% include docson.html schema="jsonschema/protocol-search-subscriptionid.json" %} |

### Next

Each ["next"](#next) event contains one page of the search results.
Ditto will not send more ["next"](#next) events for a given subscription ID than the total number of pages
requested by previous ["request"](#request) commands.

| Field      | Value                   |
|------------|-------------------------|
| **topic**  | `_/_/things/twin/search/next`     |
| **path**   | `/`     |
| **value**  | JSON object containing one page of the search results. {% include docson.html schema="jsonschema/protocol-search-next-payload.json" %} |

### Complete

A search subscription ends with a ["complete"](#complete) or a ["failed"](#failed) event from Ditto,
or with an ["cancel"](#cancel) command from the client.
Ditto sends a ["complete"](#complete) event when all pages of the search results are delivered to the client.

| Field      | Value                   |
|------------|-------------------------|
| **topic**  | `_/_/things/twin/search/complete`     |
| **path**   | `/`     |
| **value**  | Identifies a search subscription. {% include docson.html schema="jsonschema/protocol-search-subscriptionid.json" %} |

### Failed

A search subscription ends with a ["complete"](#complete) or a ["failed"](#failed) event from Ditto,
or with an ["cancel"](#cancel) command from the client.
Ditto sends a ["failed"](#failed) event when an internal error occurred,
or when the client breaches the reactive-streams specification.
It is not possible to ["request"](#request) more pages of the search results after a ["failed"](#failed) event.

| Field      | Value                   |
|------------|-------------------------|
| **topic**  | `_/_/things/twin/search/failed`     |
| **path**   | `/`     |
| **value**  | JSON object containing the reason for the failure. {% include docson.html schema="jsonschema/protocol-search-failed-payload.json" %} |
