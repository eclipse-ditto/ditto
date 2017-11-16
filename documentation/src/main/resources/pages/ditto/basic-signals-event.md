---
title: Event
keywords: signal, event
tags: [signal]
permalink: basic-signals-event.html
---

Events report that something took place in a **Digital Twin** in Ditto. Important is the "past tense" here, it took
already place (it was for example persisted into the datastore) and cannot be reversed or stopped.

Events are one of the centerpieces of Ditto:
* they are persisted/appended into the datastore
* they are published in the Ditto cluster, so other Ditto backend services can react on them (e.g. in order to update the search index)
* they are published to interested parties (if they are authorized) via the [WebSocket API](protocol-bindings-websocket.html) 
  and via [HTTP Server Sent Events](httpapi-sse.html) as [change notifications](basic-changenotifications.html)
