---
title: AMQP 1.0 protocol binding
keywords: binding, protocol, amqp, amqp10
tags: [protocol, connectivity, rql]
permalink: connectivity-protocol-bindings-amqp10.html
---

You use the AMQP 1.0 binding to connect Ditto with AMQP 1.0 endpoints such as Eclipse Hono or Azure Service Bus.

{% include callout.html content="**TL;DR**: Configure an AMQP 1.0 connection with `connectionType: \"amqp-10\"`. Source addresses are AMQP link names, and target addresses can be queues (`queue://`) or topics (`topic://`)." type="primary" %}

## Overview

The AMQP 1.0 protocol binding lets you consume messages from AMQP 1.0 endpoints via
[sources](#source-configuration) and publish messages via [targets](#target-configuration).

When you send messages in [Ditto Protocol](protocol-overview.html) format (`UTF-8` encoded strings),
set the `content-type` to:

```
application/vnd.eclipse.ditto+json
```

For other payload formats, configure a [payload mapping](connectivity-mapping.html).

### AMQP 1.0 properties and headers

Ditto maps these standard AMQP 1.0 properties:

`message-id`, `user-id`, `to`, `subject`, `reply-to`, `correlation-id`, `content-type`,
`absolute-expiry-time`, `creation-time`, `group-id`, `group-sequence`, `reply-to-group-id`

Headers not in this list are mapped as AMQP application properties. To set an application property
whose name collides with a standard property, prefix it with `amqp.application.property:`.

**Target** -- set the application property `to` to the Ditto protocol header `reply-to`:

```json
{
  "headerMapping": {
    "amqp.application.property:to": "{%raw%}{{ header:reply-to }}{%endraw%}"
  }
}
```

**Source** -- read the application property `to` into the Ditto protocol header `reply-to`:

```json
{
  "headerMapping": {
    "reply-to": "{%raw%}{{ header:amqp.application.property:to }}{%endraw%}"
  }
}
```

To set or read a message annotation, prefix with `amqp.message.annotation:`.

**Target** -- set the message annotation `to`:

```json
{
  "headerMapping": {
    "amqp.message.annotation:to": "{%raw%}{{ header:reply-to }}{%endraw%}"
  }
}
```

**Source** -- read the message annotation `to`:

```json
{
  "headerMapping": {
    "reply-to": "{%raw%}{{ header:amqp.message.annotation:to }}{%endraw%}"
  }
}
```

{% include note.html content="For now, setting or reading the AMQP 1.0 property 'content-encoding' is impossible." %}

## Connection URI format

```
amqps://user:password@hostname:5671
```

Use `amqp://` for unencrypted connections (port 5672 typically).

## Source configuration

The common [source configuration](basic-connections.html#sources) applies. Source `addresses` are
AMQP 1.0 link names:

```json
{
  "addresses": ["telemetry/FOO"],
  "authorizationContext": ["ditto:inbound-auth-subject"]
}
```

### Source acknowledgement handling

When you configure [acknowledgement requests](basic-connections.html#source-acknowledgement-requests):

* **Successful** -- message acknowledged with `accepted` outcome
* **Failed with redelivery needed** -- message rejected with `modified[delivery-failed]` outcome
* **Failed without redelivery** -- message rejected with `rejected` outcome

## Target configuration

The common [target configuration](basic-connections.html#targets) applies. Target addresses support
these formats:

* `the-queue-name` -- handled as a queue (default)
* `queue://the-queue-name` -- explicit queue
* `topic://the-topic-name` -- AMQP 1.0 topic

```json
{
  "address": "events/twin",
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
| `200` | Message successfully consumed by the endpoint |
| `5xx` | Endpoint failed to consume the message (retry feasible) |

## Specific configuration options

AMQP 1.0 specific configuration properties are interpreted as
[JMS Configuration options](https://qpid.apache.org/releases/qpid-jms-0.40.0/docs/index.html#jms-configuration-options).

### HMAC request signing

Ditto supports HMAC request signing for AMQP 1.0 connections. See
[HMAC request signing](connectivity-hmac-signing.html) for details.

## Example connection JSON

```json
{
  "id": "hono-example-connection-123",
  "connectionType": "amqp-10",
  "connectionStatus": "open",
  "failoverEnabled": true,
  "uri": "amqps://user:password@hono.eclipse.org:5671",
  "sources": [{
    "addresses": ["telemetry/FOO"],
    "authorizationContext": ["ditto:inbound-auth-subject"]
  }],
  "targets": [{
    "address": "events/twin",
    "topics": ["_/_/things/twin/events"],
    "authorizationContext": ["ditto:outbound-auth-subject"]
  }]
}
```

## Further reading

* [Connections overview](basic-connections.html) -- connection model and configuration
* [Payload mapping](connectivity-mapping.html) -- transform message payloads
* [Header mapping](connectivity-header-mapping.html) -- map external headers
* [HMAC signing](connectivity-hmac-signing.html) -- HMAC-based authentication
* [TLS certificates](connectivity-tls-certificates.html) -- secure connections with TLS
