---
title: Gateway service
keywords: architecture, service, gateway
tags: [architecture]
permalink: architecture-services-gateway.html
---

The "gateway" service is responsible for providing Ditto's [HTTP](httpapi-overview.html) + 
[WebSocket](httpapi-protocol-bindings-websocket.html) API.

## Model

The gateway service has no model by its own, but uses the model of all the services it provides the HTTP + WebSocket API for.

## Signals

The gateway service has no signals by its own, but uses the signals of all the services it provides the HTTP + WebSocket API for.

## Persistence

The gateway service has no persistence by its own.

## Tasks

* translate HTTP request to [commands](basic-signals-command.html) and translates [command responses](basic-signals-commandresponse.html)
  back to HTTP responses
* translate [Ditto Protocol](protocol-overview.html) messages incoming via the [WebSocket](httpapi-protocol-bindings-websocket.html)
  to [commands](basic-signals-command.html) and translates [command responses](basic-signals-commandresponse.html) back
  to [Ditto Protocol](protocol-overview.html) response messages
* accepts [Ditto Protocol](protocol-overview.html) messages incoming via the [Cloud Events HTTP Binding](httpapi-protocol-bindings-cloudevents.html)
* subscribe for [events](basic-signals-event.html) in Ditto cluster and emits [change notifications](basic-changenotifications.html)
  via connected [WebSocket](httpapi-protocol-bindings-websocket.html) clients or via [SSEs](httpapi-sse.html)
