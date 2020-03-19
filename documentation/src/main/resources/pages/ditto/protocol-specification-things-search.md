---
title: Things - Search protocol specification
keywords: protocol, specification, search, thing
tags: [protocol, search]
permalink: protocol-specification-things-search.html
---

The [search aspect](basic-search.html) of the Ditto protocol consists of 7 messages that together implement
the [reactive-streams](https://reactive-streams.org) protocol over any duplex transport layer.
For each search request, Ditto acts as the reactive-streams publisher of pages of search results,
and the client acts as the subscriber.
By reactive-streams means, the client controls how fast pages are delivered to it and may cancel
a search request before all results are sent.

While [connections](basic-connections.html) do not expose or require a duplex transport layer,
the search protocol is available for them as well: Send messages from client to Ditto via any
[connection source](basic-connections.html#sources); subsequent messages from Ditto to client are published
to the reply-target of the source.

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
Similarly, the search protocol messages related to one query are identified by a subscription ID.

Each search protocol message corresponds to a reactive-streams _signal_ and are bound
by the same rules in the [reactive-streams specification](https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md).

| Reactive-streams signal      | Search protocol message topic                     | Message direction |
|------------------------------|---------------------------------------------------|-------------------|
| [Publisher#subscribe][ps]    | [`_/_/things/twin/search/subscribe`](#subscribe)  | Client to Ditto   |
| [Subscription#request][nr]   | [`_/_/things/twin/search/request`](#request)      | Client to Ditto   |
| [Subscription#cancel][nc]    | [`_/_/things/twin/search/cancel`](#cancel)        | Client to Ditto   |
| [Subscriber#onSubscribe][ss] | [`_/_/things/twin/search/created`](#created)      | Ditto to Client   |
| [Subscriber#onNext][sn]      | [`_/_/things/twin/search/hasNext`](#hasnext)      | Ditto to Client   |
| [Subscriber#onComplete][sc]  | [`_/_/things/twin/search/complete`](#complete)    | Ditto to Client   |
| [Subscriber#onError][se]     | [`_/_/things/twin/search/failed`](#failed)        | Ditto to Client   |

## Messages from Client to Ditto

### Subscribe

Sent a "subscribe" message to Ditto to start receiving search results.
Ditto will always respond with a ["created"](#created) message, optionally followed by
a ["complete"](#complete) message if no thing is found or a ["failed"](#failed) message
if an error is encountered.

Use the placeholder namespace `_` in the topic to search in all visible namespaces.

| Field      | Value                   |
|------------|-------------------------|
| **topic**  | `<namespace>/_/things/twin/search/subscribe`     |
| **path**   | `/`     |
| **fields** | Contains a comma separated list of fields to be included in each thing in the search result. |
| **value**  | JSON object specifying the search query. {% include docson.html schema="jsonschema/protocol-search-subscribe-payload.json" %} |

### Request

After obtaining a subscription ID from a ["created"](#created) message,
use "request" messages to tell Ditto how many pages of search result you are prepared to receive.
Ditto will send ["hasNext"](#hasnext) messages until all requested pages are fulfilled,
the search result is exhausted, or an error occurred.

| Field      | Value                   |
|------------|-------------------------|
| **topic**  | `_/_/things/twin/search/request`     |
| **path**   | `/`     |
| **value**  | JSON object specifying the number of pages to request. {% include docson.html schema="jsonschema/protocol-search-request-payload.json" %} |

### Cancel

After obtaining a subscription ID from a ["created"](#created) message,
use a "cancel" message to stop Ditto from sending more pages of the search result.
Pages in-flight may yet arrive, but the client will eventually stop receiving
messages of the same subscription ID.

| Field      | Value                   |
|------------|-------------------------|
| **topic**  | `_/_/things/twin/search/cancel`     |
| **path**   | `/`     |
| **value**  | Identifies a search subscription. {% include docson.html schema="jsonschema/protocol-search-subscriptionid.json" %} |

## Messages from Ditto to Client

### Created

To any "subscribe" message, Ditto will always respond with a ["created"](#created) message,
optionally followed by a ["complete"](#complete) message if no thing is found or a ["failed"](#failed) message
if an error is encountered.

| Field      | Value                   |
|------------|-------------------------|
| **topic**  | `_/_/things/twin/search/created`     |
| **path**   | `/`     |
| **value**  | Discloses the ID of a search subscription with which all subsequent messages should include. {% include docson.html schema="jsonschema/protocol-search-subscriptionid.json" %} |

### HasNext

Each "hasNext" message contains a page of the search result.
Ditto will not send more "hasNext" messages for a given subscription ID than the total number of pages requested by
previous ["request"](#request) messages.

| Field      | Value                   |
|------------|-------------------------|
| **topic**  | `_/_/things/twin/search/hasNext`     |
| **path**   | `/`     |
| **value**  | JSON object containing a page of the search result. {% include docson.html schema="jsonschema/protocol-search-hasnext-payload.json" %} |

### Complete

A search subscription ends with a "complete" or a ["failed"](#failed) message from Ditto,
or with an ["cancel"](#cancel) message from the client.
Ditto sends a "complete" message when all pages of a search result are deliverd to the client.

| Field      | Value                   |
|------------|-------------------------|
| **topic**  | `_/_/things/twin/search/complete`     |
| **path**   | `/`     |
| **value**  | Identifies a search subscription. {% include docson.html schema="jsonschema/protocol-search-subscriptionid.json" %} |

### Failed

A search subscription ends with a ["complete"](#complete) or a "failed" message from Ditto,
or with an ["cancel"](#cancel) message from the client.
Ditto sends a "failed" message when an internal error occurred,
or when the client breaches the reactive-streams specification.
It is not possible to ["request"](#request) more pages of the search result after a "failed" message.

| Field      | Value                   |
|------------|-------------------------|
| **topic**  | `_/_/things/twin/search/failed`     |
| **path**   | `/`     |
| **value**  | JSON object containing the reason for the failure. {% include docson.html schema="jsonschema/protocol-search-failed-payload.json" %} |
