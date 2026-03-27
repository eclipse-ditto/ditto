---
title: History capabilities
keywords: history, historic, historian
tags: [history]
permalink: basic-history.html
---

Ditto provides APIs for retrieving the history of Things, Policies, and Connections, letting you inspect past states and stream modification events.

{% include callout.html content="**TL;DR**: You can retrieve any entity at a specific revision or timestamp, and stream historical modification events for Things and Policies. Configure `history-retention-duration` to control how long historical data is kept." type="primary" %}

## Overview

Since Eclipse Ditto 3.2.0, you can access the history of:

* [Things](basic-thing.html)
* [Policies](basic-policy.html)
* [Connections](basic-connections.html)

| Entity | Retrieve at revision/timestamp | Stream historical events |
|---|---|---|
| Thing | Yes | Yes |
| Policy | Yes | Yes |
| Connection | Yes | No |

{% include note.html content="Ditto's history API capabilities are not comparable with the features of a time series database.
    E.g. no aggregations on or compactions of the historical data can be done." %}

## Retrieving an entity from history

You can retrieve the state of an entity (Thing, Policy, Connection) at a given revision number or timestamp. You can also retrieve "historical headers" persisted alongside a modification (see [configuring historical headers](#configuring-historical-headers-to-persist)).

Use cases:
* Compare an entity's current state to a former state for debugging
* **Audit log**: Find out which subject made a specific change

### Retrieve at a specific revision

Set the `at-historical-revision` header to a revision number on any retrieve command.

```bash
# Retrieve a Thing at revision 1:
curl -u ditto:ditto 'http://localhost:8080/api/2/things/org.eclipse.ditto:thing-1' \
  --header 'at-historical-revision: 1'

# Retrieve a Policy at revision 1:
curl -u ditto:ditto 'http://localhost:8080/api/2/policies/org.eclipse.ditto:policy-1' \
  --header 'at-historical-revision: 1'

# Retrieve a Connection at revision 1:
curl -u devops:foobar 'http://localhost:8080/api/2/connections/some-connection-1' \
  --header 'at-historical-revision: 1'
```

This functionality is also available via [Ditto Protocol headers](protocol-specification.html#headers).

If [historical headers](#configuring-historical-headers-to-persist) are configured, they appear in the response header `historical-headers`.

### Retrieve at a specific timestamp

Set the `at-historical-timestamp` header to an ISO-8601 timestamp.

```bash
# Retrieve a Thing at a specific time:
curl -u ditto:ditto 'http://localhost:8080/api/2/things/org.eclipse.ditto:thing-1' \
  --header 'at-historical-timestamp: 2022-10-24T03:11:15Z'

# Retrieve a Policy at a specific time:
curl -u ditto:ditto 'http://localhost:8080/api/2/policies/org.eclipse.ditto:policy-1' \
  --header 'at-historical-timestamp: 2022-10-24T06:11:15Z'

# Retrieve a Connection at a specific time:
curl -u devops:foobar 'http://localhost:8080/api/2/connections/some-connection-1' \
  --header 'at-historical-timestamp: 2022-10-24T07:11Z'
```

## Streaming historical events

You can stream a sequence of modification events for a specific Thing or Policy. Specify the range by revision numbers or timestamps.

Use cases:
* Inspect how an entity changed over time
* Display historical values on a chart

### Streaming via SSE

The [SSE (Server Sent Event) API](httpapi-sse.html) is the simplest way to stream historical events. It is available **for Things only**.

Use these query parameters:

**Revision-based:**
* `from-historical-revision`: Starting revision. Use negative values for relative offsets from the current revision.
* `to-historical-revision`: Optional end revision. Use `0` for latest, or negative for relative offsets.

**Timestamp-based:**
* `from-historical-timestamp`: Starting timestamp (ISO-8601).
* `to-historical-timestamp`: Optional end timestamp.

Each historical event is normalized to the Thing JSON representation.

```bash
# Stream complete history from earliest available revision:
curl --http2 -u ditto:ditto -H 'Accept:text/event-stream' -N \
  http://localhost:8080/api/2/things/org.eclipse.ditto:thing-2?from-historical-revision=0&fields=thingId,attributes,features,_revision,_modified

# Stream a specific revision range:
curl --http2 -u ditto:ditto -H 'Accept:text/event-stream' -N \
  http://localhost:8080/api/2/things/org.eclipse.ditto:thing-2?from-historical-revision=23&to-historical-revision=42&fields=thingId,attributes,features,_revision,_modified

# Stream a specific timestamp range:
curl --http2 -u ditto:ditto -H 'Accept:text/event-stream' -N \
  http://localhost:8080/api/2/things/org.eclipse.ditto:thing-2?from-historical-timestamp=2022-10-24T11:44:36Z&to-historical-timestamp=2022-10-24T11:44:37Z&fields=thingId,attributes,features,_revision,_modified

# Include historical headers via the _context field:
curl --http2 -u ditto:ditto -H 'Accept:text/event-stream' -N \
  http://localhost:8080/api/2/things/org.eclipse.ditto:thing-2?from-historical-revision=0&fields=thingId,attributes,features,_revision,_modified,_context
```

#### Filtering streamed events via SSE

Add a `filter` parameter with an [RQL](basic-rql.html) expression to only receive events matching the query:

```bash
# Only events where a "bamboo" feature was modified:
curl --http2 -u ditto:ditto -H 'Accept:text/event-stream' -N \
  http://localhost:8080/api/2/things/org.eclipse.ditto:thing-2?from-historical-revision=0&fields=thingId,attributes,features,_revision,_modified&filter=exists(features/bamboo)

# Only events where temperature exceeded 50:
curl --http2 -u ditto:ditto -H 'Accept:text/event-stream' -N \
  http://localhost:8080/api/2/things/org.eclipse.ditto:thing-2?from-historical-timestamp=2022-10-24T11:44:36Z&to-historical-timestamp=2022-10-24T11:44:37Z&fields=thingId,attributes,features,_revision,_modified&filter=gt(features/temperature/properties/value,50)
```

### Streaming via Ditto Protocol

Use the [streaming subscription protocol](protocol-specification-streaming-subscription.html) to stream historical events via WebSocket or connections.

**Step 1**: Subscribe for persisted events:

```json
{
  "topic": "org.eclipse.ditto/thing-2/things/twin/streaming/subscribeForPersistedEvents",
  "path": "/",
  "headers": {},
  "value": {
    "fromHistoricalRevision": 1,
    "toHistoricalRevision": 10
  }
}
```

You can also use `fromHistoricalTimestamp` and `toHistoricalTimestamp`. Omit the "to" parameter to stream up to the current state.

Ditto responds with a `created` event containing the subscription ID:

```json
{
  "topic": "org.eclipse.ditto/thing-2/things/twin/streaming/created",
  "path": "/",
  "value": { "subscriptionId": "0" }
}
```

**Step 2**: Request demand:

```json
{
  "topic": "org.eclipse.ditto/thing-2/things/twin/streaming/request",
  "path": "/",
  "value": { "subscriptionId": "0", "demand": 25 }
}
```

Ditto sends `next` events with historical events, followed by `complete` when done, or it pauses after fulfilling the requested demand.

#### Filtering via Ditto Protocol

Include a `filter` in the subscribe command's value:

```json
{
  "topic": "org.eclipse.ditto/thing-2/things/twin/streaming/subscribeForPersistedEvents",
  "path": "/",
  "value": {
    "fromHistoricalRevision": 1,
    "toHistoricalRevision": 10,
    "filter": "exists(features/bamboo)"
  }
}
```

## Configuring historical headers to persist

Configure which Ditto headers to persist alongside events in the service configuration (things, policies, connectivity):

```hocon
event {
  historical-headers-to-persist = [
    #"ditto-originator"
    #"correlation-id"
  ]
  historical-headers-to-persist = ${?POLICY_EVENT_HISTORICAL_HEADERS_TO_PERSIST}
}
```

By default, no headers are persisted. Persisting `ditto-originator` enables audit-log functionality (tracking who made each change).

## Cleanup retention time configuration

To access entity history, journal entries must not be cleaned up too quickly.

By default, Ditto enables [background cleanup](operating-devops.html#managing-background-cleanup) to remove stale data from MongoDB. If you use history capabilities, either:

* Disable cleanup entirely (which increases database storage usage)
* Configure `history-retention-duration` to keep history for a specific duration before cleanup

## Further reading

- [Streaming subscription protocol](protocol-specification-streaming-subscription.html) -- reactive-streams protocol for historical events
- [SSE API](httpapi-sse.html) -- server-sent events for streaming
