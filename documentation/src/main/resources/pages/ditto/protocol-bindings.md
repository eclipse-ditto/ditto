---
title: Protocol bindings
keywords: bindings, protocol
tags: [protocol]
permalink: protocol-bindings.html
---

A protocol binding defines how you transport Ditto Protocol messages over a specific network protocol (e.g., "Ditto Protocol over WebSocket").

{% include callout.html content="**TL;DR**: Ditto supports seven protocol bindings -- WebSocket, AMQP 1.0, AMQP 0.9.1, MQTT 3.1.1, MQTT 5, HTTP 1.1, and Kafka 2.x. Each binding maps Ditto Protocol JSON messages to its transport format." type="primary" %}

## Overview

Each binding specifies rules for mapping Ditto Protocol messages to transport-specific messages and back. The Ditto Protocol message format stays the same; only the transport wrapper changes.

## Supported bindings

| Binding | Documentation |
|---|---|
| WebSocket | [WebSocket binding](httpapi-protocol-bindings-websocket.html) |
| AMQP 1.0 | [AMQP 1.0 binding](connectivity-protocol-bindings-amqp10.html) |
| AMQP 0.9.1 | [AMQP 0.9.1 binding](connectivity-protocol-bindings-amqp091.html) |
| MQTT 3.1.1 | [MQTT 3.1.1 binding](connectivity-protocol-bindings-mqtt.html) |
| MQTT 5 | [MQTT 5 binding](connectivity-protocol-bindings-mqtt5.html) |
| HTTP 1.1 | [HTTP 1.1 binding](connectivity-protocol-bindings-http.html) |
| Kafka 2.x | [Kafka 2.x binding](connectivity-protocol-bindings-kafka2.html) |

## Content type

When you send messages that are already in [Ditto Protocol](protocol-overview.html) format, specify this content type in the transport-specific way:

```text
application/vnd.eclipse.ditto+json
```

All [change notifications](basic-changenotifications.html) emitted by Ditto also carry this content type.

For messages that are not yet in Ditto Protocol format, use [payload mapping](connectivity-mapping.html) in Ditto's [connectivity](connectivity-overview.html) service to transform them.

## Further reading

- [Protocol overview](protocol-overview.html) -- introduction to the Ditto Protocol
- [Protocol specification](protocol-specification.html) -- the full message format reference
- [Payload mapping](connectivity-mapping.html) -- transforming non-Ditto messages
