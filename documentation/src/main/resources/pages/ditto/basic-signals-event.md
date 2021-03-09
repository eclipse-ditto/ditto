---
title: Event
keywords: event, signal
tags: [signal]
permalink: basic-signals-event.html
---

Events report that something took place in a **digital twin** in Ditto. Important is the "past tense" here; it took
already place (it was for example persisted into the data store) and cannot be reversed or stopped.

Events are one of the centerpieces of Ditto:
* they are persisted/appended into the data store,
* they are published in the Ditto cluster, so other Ditto back end services can react on them (e.g. in order to update
  the search index) and
* they are published to interested and authorized parties via the [WebSocket API](httpapi-protocol-bindings-websocket.html) as
  well as via [HTTP Server Sent Events](httpapi-sse.html) as well as [connection targets](basic-connections.html#targets) 
  via [change notifications](basic-changenotifications.html).
