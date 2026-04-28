---
title: API Overview
keywords: API, HTTP, HTTPS, JWT, REST, websocket, WSS
tags: [http]
permalink: httpapi-overview.html
---

Ditto provides multiple interfaces for interacting with digital twins, ranging from a REST-like HTTP API to persistent WebSocket connections, Server-Sent Events, and a connectivity layer for integrating external messaging systems.

{% include callout.html content="**TL;DR**: You interact with Ditto through its **HTTP API** for request/response operations, the **WebSocket API** for real-time bidirectional communication, **SSE** for streaming change notifications, and the **Connectivity API** for integrating external message brokers." type="primary" %}

## Overview

Ditto offers four primary interfaces, each suited to different use cases:

| Interface | Best for | Protocol |
|-----------|----------|----------|
| [HTTP API](http-api-doc.html) | CRUD operations, search, messages | REST-like HTTP/HTTPS |
| [WebSocket API](httpapi-protocol-bindings-websocket.html) | Real-time events, bidirectional messaging | WebSocket (WSS) |
| [Server-Sent Events](httpapi-sse.html) | Streaming change notifications | SSE over HTTP |
| [Connectivity API](connectivity-overview.html) | Integrating message brokers (MQTT, Kafka, AMQP, etc.) | Various |

## How it works

### HTTP API

The [HTTP API](http-api-doc.html) follows REST conventions and provides a resource-based interface for managing Things, Policies, and Connections. You send standard HTTP requests (`GET`, `PUT`, `POST`, `PATCH`, `DELETE`) and receive JSON responses.

Use the HTTP API when you need:
* Simple request/response interactions
* CRUD operations on Things, Features, Policies, and Connections
* [Searching](httpapi-search.html) across Things
* [Sending messages](httpapi-messages.html) to or from devices
* Lightweight integration from any programming language

### WebSocket API

The [WebSocket API](httpapi-protocol-bindings-websocket.html) provides a persistent, duplex connection using the [Ditto Protocol](protocol-overview.html). You establish a single connection and exchange JSON messages in both directions.

Use the WebSocket API when you need:
* Real-time [change notifications](basic-changenotifications.html) for digital twins
* High-throughput command streams with minimal overhead
* Bidirectional [message](basic-messages.html) exchange
* [Live channel](protocol-twinlive.html) interactions with devices

### Server-Sent Events (SSE)

[SSE](httpapi-sse.html) provides a unidirectional stream from Ditto to your client. You open a connection and receive events whenever digital twins change or when streaming search results.

Use SSE when you need:
* Simple, one-way change notifications without WebSocket complexity
* Streaming search result sets
* Browser-based event consumption via the standard `EventSource` API

### Connectivity API

The [Connectivity API](connectivity-overview.html) connects Ditto to external messaging systems like MQTT, Apache Kafka, AMQP 1.0, and AMQP 0.9.1. This enables integration with devices and backend systems that communicate through message brokers.

Use the Connectivity API when you need:
* Integration with existing messaging infrastructure
* Horizontal scaling of event consumers
* Round-robin dispatching of messages across multiple consumers

## Channel: twin vs. live

Ditto supports two communication channels across all interfaces:

| Channel | Description |
|---------|-------------|
| `twin` (default) | Communicates with the persisted digital twin representation |
| `live` | Communicates directly with the actual device |

When you use the `live` channel, the device itself handles the command. If the device does not respond within the configured `timeout` (default: 10 seconds), Ditto returns `408 Request Timeout`. Ditto performs [authorization checks](basic-auth.html) and filters responses based on the Thing's Policy.

For details, see [Ditto Protocol twin/live channel](protocol-twinlive.html).

## Content type

The HTTP API supports `application/json` for all resources except `PATCH` operations, which require `application/merge-patch+json`.

## Feature comparison

| Feature | HTTP API | WebSocket | SSE | Connectivity |
|---------|----------|-----------|-----|-------------|
| Things management (CRUD) | Yes | Yes | -- | Yes |
| Features management | Yes | Yes | -- | Yes |
| Search | Yes | Yes | Yes (streaming) | -- |
| Count | Yes | -- | -- | -- |
| Messages | Yes (send) | Yes (send + receive) | Yes (receive) | Yes |
| Change notifications | Yes (SSE) | Yes | Yes | Yes |
| Policy-based access control | Yes | Yes | Yes | Yes |
| Horizontal scaling | -- | -- | -- | Yes |

## Authentication

All interfaces support:
* **HTTP Basic Authentication** with a user managed by an upstream reverse proxy (e.g., nginx)
* **JSON Web Token (JWT)** issued by an OpenID Connect provider

See [Authentication](basic-auth.html) for details.

## Further reading

* [HTTP API concepts](httpapi-concepts.html) -- versioning, partial updates, conditional requests
* [HTTP API reference](http-api-doc.html) -- full interactive API documentation
* [WebSocket binding](httpapi-protocol-bindings-websocket.html) -- WebSocket-specific protocol details
* [Server-Sent Events](httpapi-sse.html) -- SSE streaming details
* [Connectivity overview](connectivity-overview.html) -- external broker integrations
* [Ditto Protocol](protocol-overview.html) -- the underlying message format
