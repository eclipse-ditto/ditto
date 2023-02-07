---
title: History capabilities
keywords: history, historic, historian
tags: [history]
permalink: basic-history.html
---

Starting with **Eclipse Ditto 3.2.0**, APIs for retrieving the history of the following entities is provided:
* [things](basic-thing.html)
* [policies](basic-policy.html)
* [connections](basic-connections.html)

The capabilities of these APIs are the following:

| Entity     | [Retrieving entity at a specific revision or timestamp](#retrieving-entity-from-history) | [Streaming modification events of an entity specifying from/to revision/timestamp](#streaming-historical-events-of-entity) |
|------------|------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| Thing      | ✓                                                                                        | ✓                                                                                                                          |
| Policy     | ✓                                                                                        | ✓                                                                                                                          |
| Connection | ✓                                                                                        | no                                                                                                                         |

{% include note.html content="Ditto's history API capabilities are not comparable with the features of a time series database.
    E.g. no aggregations on or compactions of the historical data can be done." %}


## Retrieving entity from history

Provides:
* Finding out the state of an entity (thing, policy, connection) at a given:
  * revision number
  * timestamp
* Retrieving "historical headers" persisted together with a modification (see [configuring historical headers to persist](#configuring-historical-headers-to-persist))

Target use cases:
* Compare changes to entity (e.g. a connection) to a former state
  * In order to solve potential errors in e.g. policy or connection configuration
* **Audit log**: Find out who (which subject) did a change to an entity
  * E.g. in order to find out who changed a policy/connection
  * Configure [which headers to persist as historical headers](#configuring-historical-headers-to-persist) to e.g. include the subject which did a modification

### Retrieve entity at specific revision

Retrieving an entity at an (historical) revision, set the header `at-historical-revision` to a `long` number for all
"retrieve" commands of persisted state.

Example for the HTTP API:
```bash
# Access a thing:
curl -u ditto:ditto 'http://localhost:8080/api/2/things/org.eclipse.ditto:thing-1' \
  --header 'at-historical-revision: 1'
  
# Access a policy:
curl -u ditto:ditto 'http://localhost:8080/api/2/policies/org.eclipse.ditto:policy-1' \
  --header 'at-historical-revision: 1'
  
# Access a connection:
curl -u devops:foobar 'http://localhost:8080/api/2/connections/some-connection-1' \
  --header 'at-historical-revision: 1'
```

The same functionality is available via a [header of a Ditto Protocol](protocol-specification.html#headers) message.

If [historical headers](#configuring-historical-headers-to-persist) were configured to be persisted, they can be found
in the response header named `historical-headers`.

### Retrieve entity at specific timestamp

Retrieving an entity at an (historical) timestamp, set the header `at-historical-timestamp` to an ISO-8601 formatted 
`string`  for all "retrieve" commands of persisted state.

Example for the HTTP API:
```bash
# Access a thing:
curl -u ditto:ditto 'http://localhost:8080/api/2/things/org.eclipse.ditto:thing-1' \
  --header 'at-historical-timestamp: 2022-10-24T03:11:15Z'
  
# Access a policy:
curl -u ditto:ditto 'http://localhost:8080/api/2/policies/org.eclipse.ditto:policy-1' \
  --header 'at-historical-timestamp: 2022-10-24T06:11:15Z'
  
# Access a connection:
curl -u devops:foobar 'http://localhost:8080/api/2/connections/some-connection-1' \
  --header 'at-historical-timestamp: 2022-10-24T07:11Z'
```

The same functionality is available via a [header of a Ditto Protocol](protocol-specification.html#headers) message.

If [historical headers](#configuring-historical-headers-to-persist) were configured to be persisted, they can be found
in the response header named `historical-headers`.


## Streaming historical events of entity

Provides:
* A stream of changes to a specific thing or policy, based on specified:
  * entity ID
  * start revision number (and optional stop revision number)
  * start timestamp (and optional stop timestamp)
* Retrieving "historical headers" persisted together with a modification (see [configuring historical headers to persist](#configuring-historical-headers-to-persist))

Target use cases:
* Inspect the changes of an entity over time
  * E.g. displaying a value on a chart with that way

### Streaming historical events via SSE

The easiest way to stream historical events is the [SSE (Server Sent Event) API](httpapi-sse.html).  
This API is however **only available for things** (not for policies).

Use the following query parameters in order to specify the start/stop revision/timestamp.

Either use the revision based parameters:
* `from-historical-revision`: specifies the revision number to start streaming historical modification events from
* `to-historical-revision`: optionally specifies the revision number to stop streaming at (if omitted, it streams events until the current state of the entity)

Alternatively, use the timestamp based parameters:
* `from-historical-timestamp`: specifies the timestamp to start streaming historical modification events from
* `to-historical-timestamp`: optionally specifies the timestamp to stop streaming at (if omitted, it streams events until the current state of the entity)

The messages sent over the SSE are the same as for the [SSE (Server Sent Event) API](httpapi-sse.html), each historical 
modification event is "normalized" to the Thing JSON representation.

Examples:
```bash
# stream complete history starting from earliest available revision of a thing:
curl --http2 -u ditto:ditto -H 'Accept:text/event-stream' -N \
  http://localhost:8080/api/2/things/org.eclipse.ditto:thing-2?from-historical-revision=0&fields=thingId,attributes,features,_revision,_modified

# stream specific history range of a thing based on revisions:
curl --http2 -u ditto:ditto -H 'Accept:text/event-stream' -N \
  http://localhost:8080/api/2/things/org.eclipse.ditto:thing-2?from-historical-revision=23&to-historical-revision=42&fields=thingId,attributes,features,_revision,_modified

# stream specific history range of a thing based on timestamps:
curl --http2 -u ditto:ditto -H 'Accept:text/event-stream' -N \
  http://localhost:8080/api/2/things/org.eclipse.ditto:thing-2?from-historical-timestamp=2022-10-24T11:44:36Z&to-historical-timestamp=2022-10-24T11:44:37Z&fields=thingId,attributes,features,_revision,_modified

# stream specific history range, additionally selecting _context in "fields" which contains the historical headers:
curl --http2 -u ditto:ditto -H 'Accept:text/event-stream' -N \
  http://localhost:8080/api/2/things/org.eclipse.ditto:thing-2?from-historical-revision=0&fields=thingId,attributes,features,_revision,_modified,_context
```

### Streaming historical events via Ditto Protocol

Please inspect the [protocol specification of DittoProtocol messages for streaming persisted events](protocol-specification-streaming-subscription.html)
to find out how to stream historical (persisted) events via DittoProtocol.  
Using the DittoProtocol, historical events can be streamed either via WebSocket or connections.

Example protocol interaction for retrieving the persisted events of a thing:

**First:** Subscribe for the persisted events of a thing
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

Alternatively to `fromHistoricalRevision` and `toHistoricalRevision`, also a timestamp based range may be used:
`fromHistoricalTimestamp` and `toHistoricalTimestamp`.  
The "to" can be omitted in order to receive all events up to the current revision or timestamp.

As a result, the following `created` event is received as response:
```json
{
  "topic": "org.eclipse.ditto/thing-2/things/twin/streaming/created",
  "path": "/",
  "headers": {},
  "value": {
    "subscriptionId": "0"
  }
}
```

**Second:** Once the streaming subscription is confirmed to be created, request demand (of how many events to get streamed), 
referencing the `subscriptionId`:
```json
{
  "topic": "org.eclipse.ditto/thing-2/things/twin/streaming/request",
  "path": "/",
  "headers": {},
  "value": {
    "subscriptionId": "0",
    "demand": 25
  }
}
```

The backend will start sending the requested persisted events as `next` messages: 
```json
{
  "topic": "org.eclipse.ditto/thing-2/things/twin/streaming/next",
  "path": "/",
  "headers": {},
  "value": {
    "subscriptionId": "0",
    "item": {
      
    }
  }
}
```

It will do so either until all existing events were sent, in that case a `complete` event is sent:
```json
{
  "topic": "org.eclipse.ditto/thing-2/things/twin/streaming/complete",
  "path": "/",
  "headers": {},
  "value": {
    "subscriptionId": "0"
  }
}
```

Or it will stop after the `demand` was fulfilled, waiting for the requester to claim more demand with a new `request` 
message.


## Configuring historical headers to persist

In the configuration of the services (things, policies, connectivity) there is a section where to configure the historical
headers to persist, for example this is the section for policies:

```hocon
event {
  # define the DittoHeaders to persist when persisting events to the journal
  # those can e.g. be retrieved as additional "audit log" information when accessing a historical policy revision
  historical-headers-to-persist = [
    #"ditto-originator"  # who (user-subject/connection-pre-auth-subject) issued the event
    #"correlation-id"
  ]
  historical-headers-to-persist = ${?POLICY_EVENT_HISTORICAL_HEADERS_TO_PERSIST}
}
```

By default, no headers are persisted as historical headers, but it could e.g. make sense to persist the `ditto-originator`
in order to provide "audit log" functionality in order to find out who (which subject) changed a policy at which time.

## Cleanup retention time configuration

In order to be able to access the history of entities, their journal database entries must not be cleaned up too quickly.

By default, Ditto enables the [background cleanup](installation-operating.html#managing-background-cleanup) in order to
delete "stale" (when not using the history feature) data from the MongoDB.

If Ditto shall be used with history capabilities, the cleanup has either
* be disabled completely (which however could lead to a lot of used database storage)
* or be configured with a `history-retention-duration` of a duration how long to keep "the history" before cleaning up
  snapshots and events
