---
title: Change notifications
keywords: change, event, feature, notification, thing
tags: [model]
permalink: basic-changenotifications.html
---

[Signals](basic-signals.html) already described what an [Event](basic-signals-event.html) in Ditto is.
Events are emitted after an entity (either a **Digital Twin** or an actual device) was changed.

At the Ditto API there are two ways for getting notified of such events:
* Via the [WebSocket API](httpapi-protocol-bindings-websocket.html) a WebSocket client gets all Events the authenticated subject
  (e.g. a user) is [authorized](basic-auth.html) to receive as [Ditto Protocol](protocol-overview.html) messages.
* Via [HTTP SSEs](httpapi-sse.html) a consumer of the SSE `EventSource` gets all Events the authenticated subject
  (e.g. a user) is [authorized](basic-auth.html) to receive directly in the format of the changed entity 
  (e.g. as [Thing JSON](basic-thing.html#model-specification) format).
