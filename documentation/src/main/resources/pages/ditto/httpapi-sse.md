---
title: Server-Sent Events (SSE)
keywords: http, api, sse, EventSource, fields, projection, extra, enrich
tags: [http, rql]
permalink: httpapi-sse.html
---

You use Server-Sent Events (SSE) to receive real-time change notifications for digital twins and to stream search results -- all through a simple, unidirectional HTTP connection.

{% include callout.html content="**TL;DR**: Open an SSE connection to `/api/2/things` with `Accept: text/event-stream` to stream change notifications, or to `/api/2/search/things` to stream search results. Filter with `namespaces`, `filter`, `fields`, and `extraFields` parameters." type="primary" %}

## Overview

Server-Sent Events (<a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.sse}}">SSEs</a>) provide a unidirectional stream from Ditto to your client. Unlike WebSockets, you cannot send data back through the same connection (use plain HTTP for that).

SSE is simpler than WebSocket to set up and works natively with the browser `EventSource` API. The events use the same JSON structure as the HTTP API responses.

For a detailed introduction to SSE, see the [HTML5 specification](https://html.spec.whatwg.org/multipage/server-sent-events.html).

## SSE for Thing change notifications

### Endpoint

```
http://localhost:8080/api/2/things
```

When you call this endpoint with `Accept: text/event-stream`, Ditto opens an SSE stream and sends a [change notification](basic-changenotifications.html) for every Thing modification that you have `READ` permission to see.

Events arrive in [Thing JSON](basic-thing.html#model-specification) format. For partial updates, only the changed portion is sent -- not the complete `Thing`.

### Filtering notifications

You can combine all of the following parameters to narrow what events you receive.

#### By Thing IDs

Watch only specific Things:

```
http://localhost:8080/api/2/things?ids=<thingId1>,<thingId2>
```

#### By field projection

Watch only specific fields using the [partial request](httpapi-concepts.html#partial-requests) `fields` parameter:

```
http://localhost:8080/api/2/things?fields=thingId,attributes
```

{% include tip.html content="The `thingId` should always be included in the `fields` query, otherwise it is no longer visible for which thing the change was made." %}

##### Projecting `_context`

Select the `_context` field to receive metadata about each event:

```
http://localhost:8080/api/2/things?fields=thingId,attributes,_context
```

The `_context` includes:
* `topic` -- the [Ditto Protocol topic](protocol-specification.html#topic)
* `path` -- the [Ditto Protocol path](protocol-specification.html#path)
* `value` -- the [Ditto Protocol value](protocol-specification.html#value)
* `headers` -- the [Ditto Protocol headers](protocol-specification.html#headers)

You can also select specific context fields: `fields=_context/topic,_context/path,_context/value`

#### By field enrichment

Add [extra fields](basic-enrichment.html) to each event beyond what actually changed:

```
http://localhost:8080/api/2/things?extraFields=attributes
```

Combine with RQL filtering to filter on enriched fields:

```
http://localhost:8080/api/2/things?extraFields=attributes/location&filter=eq(attributes/location,"kitchen")
```

When using both `fields` and `extraFields`, include the extra fields in the `fields` list if you want them in the response:

```
http://localhost:8080/api/2/things?fields=thingId,attributes&extraFields=attributes
```

#### By namespace

Receive events only from specific namespaces:

```
http://localhost:8080/api/2/things?namespaces=org.eclipse.ditto.one,org.eclipse.test
```

#### By RQL expression

Filter events with an [RQL expression](basic-rql.html):

```
http://localhost:8080/api/2/things?filter=gt(attributes/counter,42)
```

### Example: JavaScript EventSource

Assuming a Thing with the following content:

```json
{
  "thingId": "org.eclipse.ditto:fancy-thing",
  "policyId": "org.eclipse.ditto:fancy-thing",
  "attributes": {
    "manufacturer": "ACME corp",
    "complex": {
      "some": false,
      "serialNo": 4711
    }
  },
  "features": {
    "lamp": {
      "properties": {
        "on": false,
        "color": "blue"
      }
    }
  }
}
```

Create an `EventSource` to stream changes for this Thing's `lamp` feature:

```javascript
// the JavaScript must be served from the same domain as Ditto to avoid CORS issues
let source = new EventSource(
  '/api/2/things?ids=org.eclipse.ditto:fancy-thing&fields=thingId,features/lamp',
  { withCredentials: true }
);
source.onmessage = function (event) {
    console.log(event.data);
};
```

Setting `{ withCredentials: true }` sends browser credentials (Basic Auth or JWT `Bearer` token) with the request.

When the `on` property changes via `PUT /api/2/things/org.eclipse.ditto:fancy-thing/features/lamp/properties/on`, the EventSource receives:

```json
{
  "thingId": "org.eclipse.ditto:fancy-thing",
  "features": {
    "lamp": {
      "properties": { "on": false }
    }
  }
}
```

## SSE for a single Thing

### Watch all changes to one Thing

```
http://localhost:8080/api/2/things/<thingId>
```

### Watch a specific path within a Thing

```
http://localhost:8080/api/2/things/<thingId>/attributes/location
```

## SSE for messages

### Thing messages

Subscribe to messages sent to or from a Thing:

```
http://localhost:8080/api/2/things/<thingId>/inbox/messages
http://localhost:8080/api/2/things/<thingId>/outbox/messages
```

Filter by [message subject](basic-messages.html#message-elements):

```
http://localhost:8080/api/2/things/<thingId>/outbox/messages/smoke-alarm
```

### Feature messages

Subscribe to messages for a specific Feature:

```
http://localhost:8080/api/2/things/<thingId>/features/<featureId>/inbox/messages
http://localhost:8080/api/2/things/<thingId>/features/<featureId>/outbox/messages
```

Filter by subject:

```
http://localhost:8080/api/2/things/<thingId>/features/Pinger/outbox/messages/ping
```

## SSE for search results

### Endpoint

```
http://localhost:8080/api/2/search/things
```

This streams [search results](basic-search.html) as SSE events. Compared to the [search protocol](protocol-specification-things-search.html):
* **Simpler client implementation** -- no need to implement reactive-streams rules
* **Resumable** -- supports the `Last-Event-ID` header to resume interrupted streams
* **No application-layer flow control** -- relies on TCP for back-pressure

### Filtering and sorting

Apply the same parameters as the regular search endpoint:

```
http://localhost:8080/api/2/search/things?filter=eq(attributes/counter,42)
http://localhost:8080/api/2/search/things?namespaces=org.eclipse.ditto.one,org.eclipse.test
http://localhost:8080/api/2/search/things?option=sort(-_modified)
http://localhost:8080/api/2/search/things?fields=thingId,attributes
```

### Resuming with Last-Event-ID

Each search result event includes the Thing ID as its event ID. To resume an interrupted stream, send the `Last-Event-ID` header with the last received ID. Specification-conformant SSE clients handle this automatically.

```
GET http://localhost:8080/api/2/search/things?fields=thingId&option=sort(+thingId) HTTP/1.1
Accept:        text/event-stream
Last-Event-ID: ditto:device7152
```

Response:

```
HTTP/1.1 200 OK
Content-Type: text/event-stream

data:{"thingId":"ditto:device7153"}
id:ditto:device7153

data:{"thingId":"ditto:device7154"}
id:ditto:device7154

data:{"thingId":"ditto:device7155"}
id:ditto:device7155
```

## Further reading

* [Change notifications](basic-changenotifications.html) -- filtering concepts
* [Enrichment](basic-enrichment.html) -- adding extra fields to events
* [Basic Search](basic-search.html) -- search concepts
* [WebSocket binding](httpapi-protocol-bindings-websocket.html) -- duplex alternative to SSE
