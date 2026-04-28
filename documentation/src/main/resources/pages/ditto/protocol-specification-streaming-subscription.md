---
title: Streaming subscription protocol specification
keywords: protocol, specification, thing, policy, stream, subscription, history, historical
tags: [protocol, history]
permalink: protocol-specification-streaming-subscription.html
---

The streaming subscription protocol lets you stream historical persisted events for Things and Policies using a reactive-streams-based flow.

{% include callout.html content="**TL;DR**: Send a `subscribeForPersistedEvents` command to start streaming historical events, use `request` to pull items, and `cancel` to stop early. Ditto responds with `created`, `next`, `complete`, or `failed` events." type="primary" %}

## Overview

The [history capabilities](basic-history.html) of the Ditto Protocol consist of 3 commands and 4 events that implement the [reactive-streams](https://reactive-streams.org) protocol over any duplex transport layer. Ditto acts as the publisher of historical events, and you act as the subscriber. You control the delivery pace and can cancel before all results arrive.

### Connections support

While [connections](basic-connections.html) do not inherently expose a duplex transport layer, the streaming subscription protocol works with them too. Send commands via any [connection source](basic-connections.html#sources), and receive events at the source's reply-target.

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

Each command or event corresponds to a reactive-streams signal. A subscription ID links all messages of one streaming request.

| Reactive-streams signal | Streaming protocol topic | Type | Direction |
|---|---|---|---|
| [Publisher#subscribe][ps] | [`<ns>/<entity>/<group>/<channel>/streaming/subscribeForPersistedEvents`](#subscribe-for-persisted-events) | Command | Client to Ditto |
| [Subscription#request][nr] | [`<ns>/<entity>/<group>/<channel>/streaming/request`](#request) | Command | Client to Ditto |
| [Subscription#cancel][nc] | [`<ns>/<entity>/<group>/<channel>/streaming/cancel`](#cancel) | Command | Client to Ditto |
| [Subscriber#onSubscribe][ss] | [`<ns>/<entity>/<group>/<channel>/streaming/created`](#created) | Event | Ditto to Client |
| [Subscriber#onNext][sn] | [`<ns>/<entity>/<group>/<channel>/streaming/next`](#next) | Event | Ditto to Client |
| [Subscriber#onComplete][sc] | [`<ns>/<entity>/<group>/<channel>/streaming/complete`](#complete) | Event | Ditto to Client |
| [Subscriber#onError][se] | [`<ns>/<entity>/<group>/<channel>/streaming/failed`](#failed) | Event | Ditto to Client |

### Interaction pattern

**Client sends** (in order):
```text
subscribe request* cancel?
```

**Ditto responds** with:
```text
created next* (complete | failed)?
```

1. You send a `subscribeForPersistedEvents` command. Ditto responds with a `created` event containing the subscription ID.
2. You send `request` commands specifying how many items you want. Ditto sends `next` events with results.
3. When all results are delivered, Ditto sends `complete`. On error, Ditto sends `failed`.
4. You can send `cancel` at any time to stop receiving results.

Ditto guarantees that no `complete` or `failed` event arrives before your first `request` command. This simplifies concurrency handling on multi-threaded clients.

After sending `cancel`, you may still receive buffered `next`, `complete`, or `failed` events.

## Commands (client to Ditto)

### Subscribe for persisted events

Start streaming historical events. Ditto always responds with a `created` event.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<entityName>/<group>/<channel>/streaming/subscribeForPersistedEvents` |
| **path** | `/` |
| **value** | JSON object specifying start/stop options. {% include docson.html schema="jsonschema/protocol-streaming-subscription-subscribe-for-persisted-events-payload.json" %} |

Specify where to start and stop via the `value` field:
- `fromHistoricalRevision` / `toHistoricalRevision` -- revision-based range
- `fromHistoricalTimestamp` / `toHistoricalTimestamp` -- timestamp-based range

If you provide no options, Ditto streams the complete available history.

### Request

After receiving a subscription ID from the `created` event, send `request` commands to tell Ditto how many items you want.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<entityName>/<group>/<channel>/streaming/request` |
| **path** | `/` |
| **value** | JSON object specifying the demand. {% include docson.html schema="jsonschema/protocol-streaming-subscription-request-payload.json" %} |

### Cancel

Send a `cancel` command to stop receiving items. Items already in flight may still arrive.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<entityName>/<group>/<channel>/streaming/cancel` |
| **path** | `/` |
| **value** | JSON object identifying the subscription. {% include docson.html schema="jsonschema/protocol-streaming-subscriptionid.json" %} |

## Events (Ditto to client)

### Created

Ditto sends a `created` event in response to every `subscribeForPersistedEvents` command. It contains the subscription ID.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<entityName>/<group>/<channel>/streaming/created` |
| **path** | `/` |
| **value** | JSON object with the subscription ID. {% include docson.html schema="jsonschema/protocol-streaming-subscriptionid.json" %} |

### Next

Each `next` event contains one historical event item. Ditto sends at most as many items as you requested.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<entityName>/<group>/<channel>/streaming/next` |
| **path** | `/` |
| **value** | JSON object with one result item. {% include docson.html schema="jsonschema/protocol-streaming-subscription-next-payload.json" %} |

### Complete

Ditto sends `complete` when all items have been delivered.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<entityName>/<group>/<channel>/streaming/complete` |
| **path** | `/` |
| **value** | JSON object identifying the subscription. {% include docson.html schema="jsonschema/protocol-streaming-subscriptionid.json" %} |

### Failed

Ditto sends `failed` on internal errors or reactive-streams specification violations. You cannot request more items after receiving `failed`.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<entityName>/<group>/<channel>/streaming/failed` |
| **path** | `/` |
| **value** | JSON object with the failure reason. {% include docson.html schema="jsonschema/protocol-streaming-subscription-failed-payload.json" %} |

## Further reading

- [History](basic-history.html) -- history concepts, SSE streaming, and configuration
- [Things specification](protocol-specification-things.html) -- Thing commands reference
- [Search protocol](protocol-specification-things-search.html) -- another reactive-streams protocol for searching
