---
title: Connectivity overview
keywords: connectivity, connections, overview, AMQP, MQTT, HTTP, Kafka, Hono
tags: [connectivity]
permalink: connectivity-overview.html
---

Ditto's Connectivity service lets you integrate with external messaging systems and backends using protocols like AMQP, MQTT, HTTP, and Kafka.

For the connection data model and core concepts, see [Connections](basic-connections.html).

This section covers the operational aspects of connectivity:

* **[Manage Connections](connectivity-manage-connections.html)** — Create, modify, retrieve, and delete connections via the HTTP API
* **[Piggyback Commands](connectivity-manage-connections-piggyback.html)** — Manage connections via DevOps commands
* **Protocol Bindings** — Protocol-specific configuration for [AMQP 0.9.1](connectivity-protocol-bindings-amqp091.html), [AMQP 1.0](connectivity-protocol-bindings-amqp10.html), [MQTT 3.1.1](connectivity-protocol-bindings-mqtt.html), [MQTT 5](connectivity-protocol-bindings-mqtt5.html), [HTTP 1.1](connectivity-protocol-bindings-http.html), [Kafka 2.x](connectivity-protocol-bindings-kafka2.html), and [Eclipse Hono](connectivity-protocol-bindings-hono.html)
* **Data Transformation** — [Payload mapping](connectivity-mapping.html) and [header mapping](connectivity-header-mapping.html)
* **Security** — [TLS certificates](connectivity-tls-certificates.html), [SSH tunneling](connectivity-ssh-tunneling.html), and [HMAC signing](connectivity-hmac-signing.html)
