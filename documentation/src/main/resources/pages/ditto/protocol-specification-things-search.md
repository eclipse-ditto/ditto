---
title: Things - Search protocol specification
keywords: protocol, specification, search, thing
tags: [protocol, search]
permalink: protocol-specification-things-search.html
---

The search protocol lets you query across all digital twins using a reactive-streams-based flow of paginated results.

{% include callout.html content="**TL;DR**: Send a `subscribe` command to start a search, then use `request` to pull pages of results and `cancel` to stop early. Ditto responds with `created`, `next`, `complete`, or `failed` events." type="primary" %}

## Overview

The [search](basic-search.html) aspect of the Ditto Protocol consists of 3 commands and 4 events that implement the [reactive-streams](https://reactive-streams.org) protocol over any duplex transport layer. Ditto acts as the publisher of search result pages, and you act as the subscriber.

You control how fast pages arrive and can cancel a search before all results are sent.

### Connections support

While [connections](basic-connections.html) do not inherently expose a duplex transport layer, the search protocol works with them too. Send commands via any [connection source](basic-connections.html#sources), and receive events at the source's reply-target.

[ps]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/Flow.Publisher.html#subscribe(java.util.concurrent.Flow.Subscriber)
[ss]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/Flow.Subscriber.html#onSubscribe(java.util.concurrent.Flow.Subscription)
[sn]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/Flow.Subscriber.html#onNext(T)
[sc]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/Flow.Subscriber.html#onComplete()
[se]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/Flow.Subscriber.html#onError(java.lang.Throwable)
[nr]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/Flow.Subscription.html#request(long)
[nc]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/Flow.Subscription.html#cancel()
[n]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/Flow.Subscription.html

## How it works

### Signal mapping

Each search command or event corresponds to a reactive-streams signal. A subscription ID links all messages of one search query.

| Reactive-streams signal | Search protocol topic | Type | Direction |
|---|---|---|---|
| [Publisher#subscribe][ps] | [`_/_/things/twin/search/subscribe`](#subscribe) | Command | Client to Ditto |
| [Subscription#request][nr] | [`_/_/things/twin/search/request`](#request) | Command | Client to Ditto |
| [Subscription#cancel][nc] | [`_/_/things/twin/search/cancel`](#cancel) | Command | Client to Ditto |
| [Subscriber#onSubscribe][ss] | [`_/_/things/twin/search/created`](#created) | Event | Ditto to Client |
| [Subscriber#onNext][sn] | [`_/_/things/twin/search/next`](#next) | Event | Ditto to Client |
| [Subscriber#onComplete][sc] | [`_/_/things/twin/search/complete`](#complete) | Event | Ditto to Client |
| [Subscriber#onError][se] | [`_/_/things/twin/search/failed`](#failed) | Event | Ditto to Client |

### Interaction pattern

**Client sends** (in order):
```text
subscribe request* cancel?
```

**Ditto responds** with:
```text
created next* (complete | failed)?
```

1. You send a `subscribe` command. Ditto always responds with a `created` event containing the subscription ID.
2. You send `request` commands to pull pages. Ditto sends `next` events with results.
3. When all results are delivered, Ditto sends `complete`. On error, Ditto sends `failed`.
4. You can send `cancel` at any time to stop receiving results.

Ditto guarantees that no `complete` or `failed` event arrives before your first `request` command. This simplifies concurrency handling on multi-threaded clients.

After sending `cancel`, you may still receive buffered `next`, `complete`, or `failed` events.

## Commands (client to Ditto)

### Subscribe

Start a new search. Ditto always responds with a `created` event.

| Field | Value |
|---|---|
| **topic** | `_/_/things/twin/search/subscribe` |
| **path** | `/` |
| **fields** | Optional comma-separated list of Thing fields to include in results. |
| **value** | JSON object specifying the query. {% include docson.html schema="jsonschema/protocol-search-subscribe-payload.json" %} |

You specify the [filter](basic-rql.html#rql-filter), [options](basic-search.html#sorting-and-paging-options), and [namespaces](basic-search.html#namespaces) in the `value` field. They have the same semantics and defaults as other search APIs:

- `size(<count>)` in `options` limits results per `next` event (default: 25, max: 200).
- `sort(<+|-><property>, ...)` in `options` sets result order (default: `sort(+thingId)`).

The `cursor` and `limit` paging options from the HTTP API are not supported here -- the search protocol handles pagination automatically through the reactive-streams flow.

### Request

After receiving a subscription ID from the `created` event, send `request` commands to tell Ditto how many pages you are ready to receive.

| Field | Value |
|---|---|
| **topic** | `_/_/things/twin/search/request` |
| **path** | `/` |
| **value** | JSON object specifying the page count. {% include docson.html schema="jsonschema/protocol-search-request-payload.json" %} |

### Cancel

Send a `cancel` command to stop receiving results. Pages already in flight may still arrive, but Ditto eventually stops sending events for this subscription.

| Field | Value |
|---|---|
| **topic** | `_/_/things/twin/search/cancel` |
| **path** | `/` |
| **value** | JSON object identifying the subscription. {% include docson.html schema="jsonschema/protocol-search-subscriptionid.json" %} |

## Events (Ditto to client)

### Created

Ditto sends a `created` event in response to every `subscribe` command. It contains the subscription ID you need for subsequent commands.

| Field | Value |
|---|---|
| **topic** | `_/_/things/twin/search/created` |
| **path** | `/` |
| **value** | JSON object with the subscription ID. {% include docson.html schema="jsonschema/protocol-search-subscriptionid.json" %} |

### Next

Each `next` event contains one page of search results. Ditto sends at most as many pages as you requested via `request` commands.

| Field | Value |
|---|---|
| **topic** | `_/_/things/twin/search/next` |
| **path** | `/` |
| **value** | JSON object with one page of results. {% include docson.html schema="jsonschema/protocol-search-next-payload.json" %} |

### Complete

Ditto sends `complete` when all search results have been delivered.

| Field | Value |
|---|---|
| **topic** | `_/_/things/twin/search/complete` |
| **path** | `/` |
| **value** | JSON object identifying the subscription. {% include docson.html schema="jsonschema/protocol-search-subscriptionid.json" %} |

### Failed

Ditto sends `failed` when an internal error occurs or when you breach the reactive-streams specification. You cannot request more results after a `failed` event.

| Field | Value |
|---|---|
| **topic** | `_/_/things/twin/search/failed` |
| **path** | `/` |
| **value** | JSON object with the failure reason. {% include docson.html schema="jsonschema/protocol-search-failed-payload.json" %} |

## Further reading

- [Search](basic-search.html) -- search concepts and query syntax
- [RQL expressions](basic-rql.html) -- query language reference
- [Things specification](protocol-specification-things.html) -- Thing commands reference
