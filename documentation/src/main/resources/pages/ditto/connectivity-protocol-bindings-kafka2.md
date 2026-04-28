---
title: Apache Kafka 2.x protocol binding
keywords: binding, protocol, kafka, kafka2
tags: [protocol, connectivity, rql]
permalink: connectivity-protocol-bindings-kafka2.html
---

You use the Kafka 2.x binding to consume messages from and publish messages to Apache Kafka brokers.

{% include callout.html content="**TL;DR**: Configure a Kafka connection with `connectionType: \"kafka\"`. You must set `bootstrapServers` in `specificConfig`. Source addresses are Kafka topics, and target addresses support `topic`, `topic/key`, and `topic#partition` formats." type="primary" %}

## Overview

The Kafka 2.x protocol binding lets you consume messages from Kafka via
[sources](#source-configuration) and publish messages via [targets](#target-configuration).

When you send messages in [Ditto Protocol](protocol-overview.html) format (`UTF-8` encoded strings),
set the `content-type` to:

```
application/vnd.eclipse.ditto+json
```

For other formats, configure a [payload mapping](connectivity-mapping.html).

### Global Kafka client configuration

You can configure the Kafka client behavior in
[connectivity.conf](https://github.com/eclipse-ditto/ditto/blob/master/connectivity/service/src/main/resources/connectivity.conf)
under `ditto.connectivity.connection.kafka`:

* `consumer` -- consumer settings for [sources](#source-configuration)
* `committer` -- commit batch size and interval
* `producer` -- producer settings for [targets](#target-configuration)

## Connection URI format

```
tcp://user:password@hostname:9092
```

## Source configuration

The common [source configuration](basic-connections.html#sources) applies. Source `addresses` are
Kafka topics. Legal characters: `[a-z]`, `[A-Z]`, `[0-9]`, `.`, `_`, `-`.

### Quality of Service

The `qos` field controls message delivery semantics:

* `qos: 0` (at-most-once) -- offsets are committed after consumption, regardless of processing success
* `qos: 1` (at-least-once) -- offsets are committed only after [requested acknowledgements](basic-acknowledgements.html#requesting-acks) succeed

```json
{
  "addresses": ["theTopic"],
  "consumerCount": 1,
  "qos": 1,
  "authorizationContext": ["ditto:inbound-auth-subject"],
  "enforcement": {
    "input": "{%raw%}{{ header:device_id }}{%endraw%}",
    "filters": ["{%raw%}{{ entity:id }}{%endraw%}"]
  },
  "headerMapping": {},
  "payloadMapping": ["Ditto"],
  "replyTarget": {
    "enabled": true,
    "address": "theReplyTopic",
    "headerMapping": {},
    "expectedResponseTypes": ["response", "error", "nack"]
  },
  "acknowledgementRequests": {
    "includes": []
  },
  "declaredAcks": []
}
```

When `qos: 1` is set, twin modify commands automatically request the `twin-persisted`
acknowledgement. If processing fails, consumption restarts from the last committed offset
(at-least-once semantics).

### Source header mapping

Ditto extracts these headers from each consumed Kafka record:

| Header | Description |
|--------|-------------|
| `kafka.topic` | Kafka topic the record was received from |
| `kafka.key` | Record key (if available) |
| `kafka.timestamp` | Record timestamp |

These headers can be used in a source header mapping:

```json
{
  "headerMapping": {
    "the-topic": "{%raw%}{{ header:kafka.topic }}{%endraw%}",
    "the-key": "{%raw%}{{ header:kafka.key }}{%endraw%}"
  }
}
```

All other Kafka record headers are also available for [header mapping](connectivity-header-mapping.html).

### Message expiry

Devices can set message expiry using two headers:

* `creation-time` -- epoch milliseconds when the message was created
* `ttl` -- milliseconds the message remains valid

Ditto drops expired messages (where elapsed time since `creation-time` exceeds `ttl`).

### Backpressure via acknowledgements

For Kafka sources, you can use [acknowledgements](basic-acknowledgements.html) for backpressure.
Built-in acknowledgements like `live-response` and `twin-persisted` are requested by default
for their respective message types.

To disable backpressure:

```json
{
  "acknowledgementRequests": {
    "includes": [],
    "filter": "fn:delete()"
  }
}
```

## Target configuration

The common [target configuration](basic-connections.html#targets) applies. Target `address` supports
these formats:

| Format | Description |
|--------|-------------|
| `topic` | Round-robin partition assignment |
| `topic/key` | Key-based partitioning (same key = same partition) |
| `topic#partition` | Specific partition number |

```json
{
  "address": "myTopic/myKey",
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
| `204` | Message consumed successfully (debug mode disabled) |
| `200` | Message consumed successfully (debug mode enabled, includes `RecordMetadata`) |
| `4xx` | Kafka failed to consume (no retry) |
| `5xx` | Kafka failed to consume (retry feasible) |

When debug mode is enabled (`"debugEnabled": "true"`), the `200` response includes the Kafka
`RecordMetadata` as a JSON object with these fields:

* `timestamp` (if present)
* `serializedKeySize`
* `serializedValueSize`
* `topic`
* `partition`
* `offset` (if present)

## Specific configuration options

| Property | Description | Default |
|----------|-------------|---------|
| `bootstrapServers` (required) | Comma-separated list of Kafka bootstrap servers | -- |
| `saslMechanism` (required with auth) | SASL mechanism: `plain`, `scram-sha-256`, or `scram-sha-512` | -- |
| `debugEnabled` | Include Kafka `RecordMetadata` in acknowledgement payloads | `false` |
| `groupId` | Consumer group ID | connection ID |

## Example connection JSON

```json
{
  "id": "kafka-example-connection-123",
  "connectionType": "kafka",
  "connectionStatus": "open",
  "failoverEnabled": true,
  "uri": "tcp://user:password@localhost:9092",
  "specificConfig": {
    "bootstrapServers": "localhost:9092,other.host:9092",
    "saslMechanism": "plain"
  },
  "sources": [{
    "addresses": ["theTopic"],
    "consumerCount": 1,
    "qos": 1,
    "authorizationContext": ["ditto:inbound-auth-subject"],
    "enforcement": {
      "input": "{%raw%}{{ header:device_id }}{%endraw%}",
      "filters": ["{%raw%}{{ entity:id }}{%endraw%}"]
    },
    "headerMapping": {},
    "payloadMapping": ["Ditto"],
    "replyTarget": {
      "enabled": true,
      "address": "theReplyTopic",
      "headerMapping": {},
      "expectedResponseTypes": ["response", "error", "nack"]
    },
    "acknowledgementRequests": {
      "includes": []
    },
    "declaredAcks": []
  }],
  "targets": [{
    "address": "topic/key",
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
