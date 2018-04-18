---
title: HTTP API server sent events (SSE)
keywords: http, api, sse, EventSource
tags: [http]
permalink: httpapi-sse.html
---

HTML5 <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.sse}}">SSEs</a> may be used in order 
to get notified when the state of **Digital Twins** change.

This is the second mechanism of Ditto in order to get [change notifications](basic-changenotifications.html).
The benefit of this mechanism in contrary to the [WebSocket](httpapi-protocol-bindings-websocket.html) channel is that it is
even easier to open a SSE connection from the client than a WebSocket and that in Ditto's interpretation of SSEs the
events sent back from the backend have the same JSON structure as the HTTP API on which they are invoked. 

## Server-Sent Events

Different as WebSockets, Server-Sent Events are unidirectional originating from the back-end towards the client. Via SSEs
the client can only be notified, it cannot send data back (it can use plain HTTP therefore).

For a detailed introduction into SSEs, please visit 
the [HTML5 specification](https://html.spec.whatwg.org/multipage/server-sent-events.html).

{% include warning.html content="Although SSEs are a HTML5 standard, they still cannot be used in every browser: 
    see [caniuse.com](https://caniuse.com/#search=Server-sent%20events) - Microsoft IE11 and Edge browser still doesn't support them." %}

### SSEs in JavaScript

Using the `EventSource` object in JavaScript is also well covered in the [HTML5 specification](https://html.spec.whatwg.org/multipage/server-sent-events.html#server-sent-events-intro).

## SSE API `/things`

Currently the only supported HTTP API for receiving <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.sse}}">SSEs</a>
is the `/things` endpoint:
```
http://localhost:8080/api/<1|2>/things
```

When the endpoint is invoked with an HTTP header `Accept` with value `text/event-stream`, an Server-Sent Event stream of
[change notifications](basic-changenotifications.html) is created by Ditto and for each notification for which the caller
has READ permissions (see [authorization](basic-auth.html#authorization)), a message is sent to the client.

The format of the message at the `/things` endpoint is always in the form of a [Thing JSON](basic-thing.html#model-specification)
(in API 1 format or API 2 format depending on which edpoint the SSE was created).

For partial updates to a `Thing` however, only the changed part is sent back via the SSE, not the complete `Thing`.

### Only get notified about changes of specific Things

When the `/things` endpoint is used for connecting to the SSE stream, all for the authenticated user visible `Things` are
included in the stream. If only specific `Things` should be watched, the query parameter `ids` can be added:
```
http://localhost:8080/api/<1|2>/things?ids=<thingId1>,<thingId2>
```

### Only get notified about certain changes

Additionally using the `fields` paramter of the [partial request](httpapi-concepts.html#partial-requests) feature, only
specific parts can be watched for changes:
```
http://localhost:8080/api/<1|2>/things?fields=thingId,attributes
```

{% include tip.html content="The `thingId` should always be included in the `fields` query, otherwise it is no longer
    reproducible, for which Thing the change was made." %}


### Example for SSE on Things

Assuming we have a Thing (in API version 1) with the following JSON content:
```json
{
  "thingId": "org.eclipse.ditto:fancy-thing",
  "acl": {
    "{userId}": {
      "READ": true,
      "WRITE": true,
      "ADMINISTRATE": true
    }
  },
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

From within JavaScript we can now create an `EventSource` in order to open up a SSE stream in Ditto and simply print each
event to the console. This one tracks only changes to the Thing with ID `org.eclipse.ditto:fancy-thing` and only watches
for changes on the feature `lamp`:
```javascript
// the javascript must be served from the same domain as Ditto is running in order to avoid CORS problems
let source = new EventSource('/api/1/things?ids=org.eclipse.ditto:fancy-thing&fields=thingId,features/lamp');
source.onmessage = function (event) {
    console.log(event.data);
};
```

This would print for each `Thing` the authenticated user is allowed to `READ` (see [acl](basic-acl.html)) the changed content of the Thing.

So when the `on` property of the `lamp` feature is changed to `true` via such an HTTP API call:
```
PUT /api/1/things/org.eclipse.ditto:fancy-thing/features/lamp/properties/on
payload: true
```

The JavaScript snippet would log to console:
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
