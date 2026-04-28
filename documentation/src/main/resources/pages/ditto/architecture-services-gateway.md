---
title: Gateway service
keywords: architecture, service, gateway
tags: [architecture]
permalink: architecture-services-gateway.html
---

The Gateway service provides Ditto's external-facing [HTTP](httpapi-overview.html) and [WebSocket](httpapi-protocol-bindings-websocket.html) APIs, translating between HTTP/WebSocket requests and internal Ditto signals.

{% include callout.html content="**TL;DR**: The Gateway service is the entry point for all HTTP and WebSocket traffic. It translates HTTP requests into internal commands, routes Ditto Protocol messages from WebSocket and Cloud Events, and streams change notifications back to connected clients." type="primary" %}

## Overview

The Gateway service acts as Ditto's API layer. It does not own any entities or persistence -- instead, it translates external requests into internal [signals](basic-signals.html) and forwards them to the appropriate services within the cluster.

## How it works

### Model and signals

The Gateway service does not define its own entity model or signal types. It uses the models and signals from all other Ditto services to provide a unified API surface.

### Persistence

The Gateway service does not maintain any persistence of its own.

### Tasks

The Gateway service performs these core tasks:

* **HTTP API**: Translate incoming HTTP requests to [commands](basic-signals-command.html) and translate [command responses](basic-signals-commandresponse.html) back to HTTP responses
* **WebSocket API**: Translate [Ditto Protocol](protocol-overview.html) messages arriving via [WebSocket](httpapi-protocol-bindings-websocket.html) to commands, and translate responses back to Ditto Protocol messages
* **Cloud Events**: Accept [Ditto Protocol](protocol-overview.html) messages via the [Cloud Events HTTP Binding](httpapi-protocol-bindings-cloudevents.html)
* **Change notifications**: Subscribe to [events](basic-signals-event.html) in the Ditto cluster and stream [change notifications](basic-changenotifications.html) to connected [WebSocket](httpapi-protocol-bindings-websocket.html) clients and [SSE](httpapi-sse.html) consumers

## Further reading

* [HTTP API Overview](httpapi-overview.html)
* [WebSocket Protocol Binding](httpapi-protocol-bindings-websocket.html)
* [Cloud Events Binding](httpapi-protocol-bindings-cloudevents.html)
* [SSE (Server-Sent Events)](httpapi-sse.html)
* [Architecture Overview](architecture-overview.html)
