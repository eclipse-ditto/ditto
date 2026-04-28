---
title: AMQP 0.9.1 protocol binding
keywords: binding, protocol, amqp, amqp091, rabbitmq
tags: [protocol, connectivity, rql]
permalink: connectivity-protocol-bindings-amqp091.html
---

You use the AMQP 0.9.1 binding to connect Ditto with message brokers like RabbitMQ for consuming and publishing messages.

{% include callout.html content="**TL;DR**: Configure an AMQP 0.9.1 connection with `connectionType: \"amqp-091\"`. Source addresses are queue names, and target addresses use the format `exchange_name/routing_key`." type="primary" %}

## Overview

The AMQP 0.9.1 protocol binding lets you consume messages from AMQP 0.9.1 brokers via
[sources](#source-configuration) and publish messages via [targets](#target-configuration).

When you send messages in [Ditto Protocol](protocol-overview.html) format (`UTF-8` encoded strings),
set the `content-type` to:

```
application/vnd.eclipse.ditto+json
```

For other payload formats, configure a [payload mapping](connectivity-mapping.html).

### AMQP 0.9.1 properties

Ditto interprets these AMQP 0.9.1 properties:

* `content-type` -- defines the Ditto Protocol content type
* `correlation-id` -- correlates request messages to responses

## Connection URI format

```
amqp://user:password@hostname:5672/vhost
```

Use `amqps://` for TLS-secured connections.

## Source configuration

The common [source configuration](basic-connections.html#sources) applies. Source `addresses` are
AMQP 0.9.1 queue names:

```json
{
  "addresses": ["queueName"],
  "authorizationContext": ["ditto:inbound-auth-subject"]
}
```

### Source acknowledgement handling

When you configure [acknowledgement requests](basic-connections.html#source-acknowledgement-requests):

* **Successful acknowledgements** -- Ditto sends an `Ack` for the received `deliveryTag`
* **Failed with redelivery needed** -- Ditto sends a `Nack` with `requeue: true`
* **Failed without redelivery** -- Ditto sends a `Nack` with `requeue: false`

## Target configuration

The common [target configuration](basic-connections.html#targets) applies. The target `address`
combines the exchange name and routing key:

```json
{
  "address": "exchangeName/routingKey",
  "topics": [
    "_/_/things/twin/events",
    "_/_/things/live/messages"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject"]
}
```

The target address supports [placeholders](basic-connections.html#placeholder-for-target-addresses).

### Target acknowledgement handling

When you configure [issued acknowledgement labels](basic-connections.html#target-issued-acknowledgement-label):

| Status | Condition |
|--------|-----------|
| `200` | Message successfully ACKed by the broker |
| `400` | Broker does not support publisher confirms |
| `503` | Broker negatively confirmed the message |

## Specific configuration options

There are no AMQP 0.9.1-specific configuration properties.

## Example connection JSON

```json
{
  "id": "rabbit-example-connection-123",
  "connectionType": "amqp-091",
  "connectionStatus": "open",
  "failoverEnabled": true,
  "uri": "amqp://user:password@localhost:5672/vhost",
  "sources": [{
    "addresses": ["queueName"],
    "authorizationContext": ["ditto:inbound-auth-subject"]
  }],
  "targets": [{
    "address": "exchangeName/routingKey",
    "topics": [
      "_/_/things/twin/events",
      "_/_/things/live/messages"
    ],
    "authorizationContext": ["ditto:outbound-auth-subject"]
  }]
}
```

## Further reading

* [Connections overview](basic-connections.html) -- connection model and configuration
* [Payload mapping](connectivity-mapping.html) -- transform message payloads
* [Header mapping](connectivity-header-mapping.html) -- map external headers
* [TLS certificates](connectivity-tls-certificates.html) -- secure connections with TLS
