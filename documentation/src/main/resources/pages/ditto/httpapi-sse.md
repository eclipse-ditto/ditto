---
title: HTTP API server sent events (SSE)
keywords: http, api, sse, EventSource, fields, projection, extra, enrich
tags: [http, rql]
permalink: httpapi-sse.html
---

Server-Sent Events (<a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.sse}}">SSEs</a>)
can be used to get notified when the state of **digital twins** change, and to receive a
[search results](basic-search.html) stream.

## Server-Sent Events

Server-Sent Events are unidirectional originating from the back-end towards the client. Via SSEs
the client can only be notified, it cannot send data back (it can use plain HTTP for that).

For a detailed introduction into SSEs, please visit 
the [HTML5 specification](https://html.spec.whatwg.org/multipage/server-sent-events.html).

### SSEs in JavaScript

Using the `EventSource` object in JavaScript is also covered in the [HTML5 specification](https://html.spec.whatwg.org/multipage/server-sent-events.html#server-sent-events-intro).

## SSE API `/things`

The SSE API for receiving [change notifications](basic-changenotifications.html) is the `/things` endpoint:
```
http://localhost:8080/api/2/things
```

This is a mechanism to get [change notifications](basic-changenotifications.html).
The benefit of this mechanism in comparison to the [WebSocket](httpapi-protocol-bindings-websocket.html) channel is, 
that it is even easier to open a SSE connection from the client than a WebSocket, 
and that in Ditto's interpretation of SSEs the events sent back from the backend have the same JSON structure as 
the HTTP API on which they are invoked. 

When the endpoint is invoked with an HTTP header `Accept` with value `text/event-stream`, a Server-Sent Event stream of
[change notifications](basic-changenotifications.html) is created by Ditto and for each notification for which the 
caller has READ permissions (see [authorization](basic-auth.html#authorization)), an event is sent to the client.

The format of the event at the `/things` endpoint is always in the form of a [Thing JSON](basic-thing.html#model-specification).

For partial updates to a `Thing` however, only the changed part is sent back via the SSE, not the complete `Thing`.


### Only get notified about specific changes

In order to apply a server side filtering of which Server-Sent Events should be emitted to a consumer, Ditto provides
several possibilities listed in the sections below. 

All of the query parameters below can be combined, so that you can for example express that only events from
a certain namespace with a specific RQL expression should be emitted, which could look like:
```
http://localhost:8080/api/2/things?namespaces=org.eclipse.ditto.one,org.eclipse.test&filter=gt
(attributes/counter,42)
```

#### Specify the IDs of the Things

When the `/things` endpoint is used for connecting to the SSE stream, all
things visible for the authenticated user are
included in the stream. If only specific things should be watched, the query parameter `ids` can be added:
```
http://localhost:8080/api/2/things?ids=<thingId1>,<thingId2>
```

#### Fields projection

Additionally, using the `fields` parameter of the [partial request](httpapi-concepts.html#partial-requests) feature, 
only specific parts can be watched for changes, e.g.:
```
http://localhost:8080/api/2/things?fields=thingId,attributes
```

{% include tip.html content="The `thingId` should always be included in the `fields` query, otherwise it is no longer visible for which thing the change was made." %}

#### Field enrichment

In addition to the fields projection, one can also choose to select [extra fields](basic-enrichment.html) 
to return in addition to the actually changed fields, e.g.:
```
http://localhost:8080/api/2/things?extraFields=attributes
```

The result is, that the server-sent events are merged, i.e. the SSE contains the actually changed data + the extra fields.

This can be used in combination with the below mentioned [RQL filter](#filtering-by-rql-expression), e.g.:
```
http://localhost:8080/api/2/things?extraFields=attributes/location&filter=eq(attributes/location,"kitchen")
```

For combined usage of `fields` and `extraFields` one needs to specify all fields, selected as extra fields, for the 
field projection, too. This is required to allow filtering based on extra fields but still omit them in the payload.
An example without filtering would look like this:
```
http://localhost:8080/api/2/things?fields=thingId,attributes&extraFields=attributes
```

#### Filtering by namespaces

As described in [change notifications](basic-changenotifications.html#by-namespaces), it is possible to subscribe only
for changes done in specific namespaces. At the SSE API, simply specify the `namespaces` parameter and provide a comma
separated list of which namespaces to select, e.g.:
```
http://localhost:8080/api/2/things?namespaces=org.eclipse.ditto.one,org.eclipse.test
```

#### Filtering by RQL expression

As also described in [change notifications](basic-changenotifications.html#by-rql-expression), it is additionally 
possible to specify an RQL expression expressing on which occasions to emit an event via the SSE API. 
Simply specify the `filter` parameter with an [RQL expression](basic-rql.html), e.g.:
```
http://localhost:8080/api/2/things?filter=gt(attributes/counter,42)
```


### Example for SSE on Things

Assuming we have a thing with the following JSON content:
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

From within JavaScript we can now create an `EventSource` in order to open up a SSE stream in Ditto and simply print 
each event to the console. This one tracks only changes to the thing with ID `org.eclipse.ditto:fancy-thing` and 
only watches for changes on the feature `lamp`:
```javascript
// the javascript must be served from the same domain as Ditto is running in order to avoid CORS problems
let source = new EventSource('/api/2/things?ids=org.eclipse.ditto:fancy-thing&fields=thingId,features/lamp', { withCredentials: true });
source.onmessage = function (event) {
    console.log(event.data);
};
```

By defining `{ withCredentials: true }` at the `new EventSource()`, the browser credentials (`Authorization` header) of 
the already authenticated browser against that domain are sent along, this works for Basic Auth as well as for 
JWT based authentication using a `Bearer` token.

This would log the changed content of each thing the authenticated subject is allowed to `READ`.

So when the `on` property of the `lamp` feature is changed to `true` via such an HTTP API call:
```
PUT /api/2/things/org.eclipse.ditto:fancy-thing/features/lamp/properties/on
payload: true
```

the JavaScript snippet would log to console:
```json
{
  "thingId": "org.eclipse.ditto:fancy-thing",
  "features": {
    "lamp": {
      "properties": {
        "on": false
      }
    }
  }
}
```

## SSE API `/things/<thingId>`

### Subscribe for changes of a single Thing

Opening the SSE on the `/things/<thingId>` endpoint is used for connecting to the SSE stream in order to watch changes
for only one specific thing:
```
http://localhost:8080/api/2/things/<thingId>
```

Example - only get the changes of the thing `org.eclipse.ditto:thing-1`:
```
http://localhost:8080/api/2/things/org.eclipse.ditto:thing-1
```

### Subscribe for changes of a specific path inside a Thing

Opening the SSE on the `/things/<thingId>/<jsonPointer>` endpoint is used for connecting to the SSE stream in
order to watch changes for only one specific thing on only a specific path in that thing:
```
http://localhost:8080/api/2/things/<thingId>/<jsonPointer>
```
The data contained in the event will be the same as when the endpoint would be queried via a normal HTTP `GET`.

Example - only get the value of the attribute `location` whenever that changes:
```
http://localhost:8080/api/2/things/org.eclipse.ditto:thing-1/attributes/location
```

### Subscribe for messages for a specific Thing

Opening the SSE on the `/things/<thingId>/inbox/messages` or `/things/<thingId>/outbox/messages` endpoint is used for
connecting to the SSE stream in order to receive [messages](basic-messages.html) sent to or sent by the thing:
```
http://localhost:8080/api/2/things/<thingId>/inbox/messages
http://localhost:8080/api/2/things/<thingId>/outbox/messages
```

In order to subscribe just for a specific [message subject](basic-messages.html#elements), just use the same path as
you would for e.g. sending the message via HTTP `POST`:
```
http://localhost:8080/api/2/things/<thingId>/inbox/messages/<subject>
http://localhost:8080/api/2/things/<thingId>/outbox/messages/<subject>
```

Example - receive whenever a device sends a "smoke-alarm" message to its outbox:
```
http://localhost:8080/api/2/things/org.eclipse.ditto:thing-1/outbox/messages/smoke-alarm
```

### Subscribe for messages of a specific Feature of a specific Thing

Opening the SSE on the `/things/<thingId>/features/<featureId>/inbox/messages` or
`/things/<thingId>/features/<featureId>//outbox/messages` endpoint is used for
connecting to the SSE stream in order to receive [messages](basic-messages.html) sent to or sent by the thing feature:
```
http://localhost:8080/api/2/things/<thingId>/features/<featureId>/inbox/messages
http://localhost:8080/api/2/things/<thingId>/features/<featureId>/outbox/messages
```

In order to subscribe just for a specific [message subject](basic-messages.html#elements), just use the same path as
you would for e.g. sending the message via HTTP `POST`:
```
http://localhost:8080/api/2/things/<thingId>/features/<featureId>/inbox/messages/<subject>
http://localhost:8080/api/2/things/<thingId>/features/<featureId>/outbox/messages/<subject>
```

Example - receive whenever a device sends a "ping" message to the outbox of its "Pinger" feature:
```
http://localhost:8080/api/2/things/org.eclipse.ditto:thing-1/features/Pinger/outbox/messages/ping
```


## SSE API `/search/things`

The SSE API to stream search results is the `/search/things` endpoint:
```
http://localhost:8080/api/2/search/things
```

This is the second mechanism of Ditto in order to get [search results](basic-search.html).
The benefits of this mechanism over the [search protocol](protocol-specification-things-search.html) are:
- The client side is easy to implement; it needs not abide by the reactive-streams rules.
- SSE permits [resuming a stream from the last received ID](#resuming-by-last-event-id) after connection interruptions.

The drawback is, that SSE has no application-layer flow control and must rely on the transport layer (TCP) for
back-pressure. In contrast, the [search protocol](protocol-specification-things-search.html) supports back-pressure
and cancellation over any transport layer by reactive-streams means.

When the endpoint is invoked with an HTTP header `Accept` with value `text/event-stream`, a Server-Sent Event stream of
things is created by Ditto and for each thing matching the search filter for which the caller has READ permissions
(see [authorization](basic-auth.html#authorization)), an event is sent to the client.

The format of the event at the `/search/things` endpoint is always in the form of a [Thing JSON](basic-thing.html#model-specification)
(in API 1 format or API 2 format depending on which endpoint the SSE was created).

### Filtering by RQL expression

Specify the `filter` parameter with an [RQL expression](basic-rql.html) to restrict the search results to things
matching the RQL expression. For example, the SSE stream below emits only things which have a `counter` attribute
with value `42`:
```
http://localhost:8080/api/2/search/things?filter=eq(attributes/counter,42)
```

### Filtering by namespaces

Specify the `namespaces` parameter to restrict search to the namespaces given as a comma separated list. For example:
```
http://localhost:8080/api/2/search/things?namespaces=org.eclipse.ditto.one,org.eclipse.test
```

### Sorting by RQL sort option

Specify the `option` parameter with an [RQL sort option](basic-rql.html#rql-sorting) to stream things in a certain
order. For example, the SSE stream below emits things according to the timestamp. The timestamp of their last updates
is stored in the `_modified` field, and `-` describes the descending order, thus the thing with the newest
changes appears first:
```
http://localhost:8080/api/2/search/things?option=sort(-_modified)
```

**Fields projection**

Use the `fields` parameter to retrieve only specific parts of things in search results, e.g.:
```
http://localhost:8080/api/2/search/things?fields=thingId,attributes
```

### Resuming by `Last-Event-ID`

The [HTML5 SSE specification](https://html.spec.whatwg.org/multipage/server-sent-events.html)
permits clients to resume from interrupted streams by sending a header `Last-Event-ID`.
Each thing in the search result has its thing ID set as the event ID.
To resume the stream from the point of its interruption,
start another SSE stream with _identical_ query parameters and the `Last-Event-ID` header set to the last received
event ID.
Specification-conform SSE clients perform resumption automatically, making SSE a simple way to export large numbers
of things over a slow connection for long periods of time.


**Example:**

Request
```
GET http://localhost:8080/api/2/search/things?fields=thingId&option=sort(+thingId) HTTP/1.1
Accept:        text/event-stream
Last-Event-ID: ditto:device7152
```

Response
```
HTTTP/1.1 200 OK
Content-Type: text/event-stream

data:{"thingId":"ditto:device7153"}
id:ditto:device7153

data:{"thingId":"ditto:device7154"}
id:ditto:device7154

data:{"thingId":"ditto:device7155"}
id:ditto:device7155
```
