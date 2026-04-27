---
title: MQTT 5 protocol binding
keywords: binding, protocol, mqtt, mqtt5
tags: [protocol, connectivity]
permalink: connectivity-protocol-bindings-mqtt5.html
---

You use the MQTT 5 binding to connect Ditto with MQTT 5 brokers, gaining access to user-defined properties and enhanced message metadata.

{% include callout.html content="**TL;DR**: Configure an MQTT 5 connection with `connectionType: \"mqtt-5\"`. Source addresses are MQTT topics (wildcards `+` and `#` allowed). MQTT 5 supports user-defined properties for header mapping." type="primary" %}

## Overview

The MQTT 5 protocol binding lets you consume messages from MQTT 5 brokers via
[sources](#source-configuration) and publish messages via [targets](#target-configuration).

MQTT payloads should be `UTF-8` encoded strings when sent in [Ditto Protocol](protocol-overview.html)
format. For other formats, configure a [payload mapping](connectivity-mapping.html).

### MQTT 5 properties

Ditto interprets these MQTT 5 properties:

| Property | Description |
|----------|-------------|
| `9 (0x09) Correlation Data` | Correlation ID, stored in the `correlation-id` header |
| `8 (0x08) Response Topic` | MQTT topic for responses, mapped to the `reply-to` header |
| `3 (0x03) Content Type` | MIME type of the payload |
| `2 (0x02) Message Expiry Interval` | Message lifetime in seconds |

## Connection URI format

```
tcp://hostname:1883
```

Use `ssl://` for TLS-secured connections.

## Source configuration

The common [source configuration](basic-connections.html#sources) applies, with these specifics:

* `addresses` are MQTT topics to subscribe to (wildcards `+` and `#` allowed)
* `authorizationContext` may contain `{%raw%}{{ header:<name> }}{%endraw%}` placeholders (via user-defined properties)
* `qos` (required) sets the maximum QoS: `0` (at-most-once), `1` (at-least-once), or `2` (exactly-once)

```json
{
  "addresses": ["device/telemetry/#"],
  "authorizationContext": ["ditto:inbound-auth-subject"],
  "qos": 2
}
```

### Source header mapping

MQTT 5 supports user-defined properties. Ditto also extracts these headers from each consumed message:

| Header | Description |
|--------|-------------|
| `mqtt.topic` | MQTT topic the message was received on |
| `mqtt.qos` | QoS value of the received message |
| `mqtt.retain` | Retain flag of the received message |
| `mqtt.message-expiry-interval` | Message expiry interval |
| `correlation-id` | MQTT 5 correlation data |
| `reply-to` | MQTT 5 response topic |
| `content-type` | MQTT 5 content type |

```json
{
  "headerMapping": {
    "topic": "{%raw%}{{ header:mqtt.topic }}{%endraw%}",
    "correlation-id": "{%raw%}{{ header:correlation-id }}{%endraw%}",
    "device-id": "{%raw%}{{ header:device-id-user-defined-property }}{%endraw%}"
  }
}
```

### Source acknowledgement handling

When you configure [acknowledgement requests](basic-connections.html#source-acknowledgement-requests):

* **Successful** -- Ditto acknowledges the MQTT 5 message
* **Failed with redelivery needed** -- depends on [reconnectForRedelivery](#reconnectforredelivery): either reconnects or withholds the ACK
* **Failed without redelivery** -- Ditto acknowledges the message

To enable acknowledgement processing only for QoS 1/2 messages:

```json
{
  "addresses": ["device/#"],
  "authorizationContext": ["ditto:inbound-auth-subject"],
  "qos": 1,
  "acknowledgementRequests": {
    "includes": [],
    "filter": "fn:filter(header:mqtt.qos,'ne','0')"
  }
}
```

## Target configuration

The common [target configuration](basic-connections.html#targets) applies. The target `address` is
the MQTT topic to publish to. The `qos` field sets the publishing QoS level:

```json
{
  "address": "mqtt/topic/of/my/device/{%raw%}{{ thing:id }}{%endraw%}",
  "topics": [
    "_/_/things/twin/events",
    "_/_/things/live/messages"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject"],
  "qos": 0
}
```

### Target header mapping

MQTT 5 supports user-defined properties in the [header mapping](connectivity-header-mapping.html).
These special headers are applied directly to the published message:

| Header | Effect |
|--------|--------|
| `mqtt.topic` | Overwrites the configured target topic |
| `mqtt.qos` | Overwrites the configured QoS level |
| `mqtt.retain` | Sets the MQTT retain flag |
| `mqtt.message-expiry-interval` | Sets the message expiry interval |

### Target acknowledgement handling

When you configure [issued acknowledgement labels](basic-connections.html#target-issued-acknowledgement-label):

| Status | Condition |
|--------|-----------|
| `200` | Message ACKed by the broker |
| `503` | Broker error before acknowledgement was received |

## Specific configuration options

```json
{
  "specificConfig": {
    "clientId": "my-mqtt5-client-id",
    "reconnectForRedelivery": false,
    "cleanSession": false,
    "separatePublisherClient": false,
    "publisherId": "my-mqtt5-publisher-id",
    "reconnectForRedeliveryDelay": "5s",
    "keepAlive": "60s",
    "lastWillTopic": "my/last/will/topic",
    "lastWillQos": 1,
    "lastWillRetain": false,
    "lastWillMessage": "connection lost"
  }
}
```

### clientId

Overwrites the default MQTT client ID. Default: the Ditto connection ID.

If the connection's `clientCount` is 2 or more, the ID of each connectivity service instance is
appended to the client ID to prevent clients from having the same ID. Otherwise the broker will
disconnect the already-connected client every time another client with the same ID connects.

### reconnectForRedelivery

When `true`, the MQTT connection reconnects whenever a consumed QoS 1 ("at least once") or 2
("exactly once") message cannot be [acknowledged](#source-acknowledgement-handling) successfully.
The MQTT broker will then re-publish the message after reconnection. When `false`, the MQTT
message is simply acknowledged (`PUBACK` or `PUBREC`, `PUBREL`). Default: `false`.

Handle with care:
* When `true`, incoming QoS 0 messages are lost during the reconnection phase
* When `true` and an MQTT target is also configured, outbound messages are lost during reconnection
  -- to fix this, set `separatePublisherClient` to `true` to publish via a separate MQTT connection
* When `false`, MQTT messages with QoS 1 and 2 are redelivered based on the MQTT broker's strategy,
  but may not be redelivered at all as the MQTT specification does not require unacknowledged
  messages to be redelivered without reconnection of the client

### cleanSession

Sets the MQTT 5 `cleanStart` flag. (The flag is called `cleanStart` in MQTT 5 but the option is
named `cleanSession` for consistency with MQTT 3.1.1.) Default: `false`.

### separatePublisherClient

When `true`, Ditto opens two MQTT connections: one for subscribing and one for publishing.
Default: `false`.

### publisherId

MQTT client ID for the publisher when `separatePublisherClient` is enabled.
Default: `clientId` + `"p"` (or `connectionId` + `"p"`).

### reconnectForRedeliveryDelay

Wait time before reconnecting for redelivery (minimum `1s`). Default: `2s`.

### keepAlive

Ping interval to check if the connection is still up. Default: `60s`.

### Last Will configuration

Configure a [Last Will](https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901033)
message to notify other clients of an ungraceful disconnect:

| Property | Description | Default |
|----------|-------------|---------|
| `lastWillTopic` | Topic for the Last Will message (required to enable) | -- |
| `lastWillQos` | QoS level (`0`, `1`, or `2`) | `0` |
| `lastWillRetain` | Whether new subscribers receive the message immediately | `false` |
| `lastWillMessage` | UTF-8 text payload | empty string |

{% include note.html content="This feature is enabled if the _last will topic_ is set." %}

## Example connection JSON

```json
{
  "id": "mqtt5-example-connection-12",
  "connectionType": "mqtt-5",
  "connectionStatus": "open",
  "failoverEnabled": true,
  "uri": "tcp://test.mosquitto.org:1883",
  "sources": [{
    "addresses": ["eclipse-ditto-sandbox/#"],
    "authorizationContext": ["ditto:inbound-auth-subject"],
    "qos": 0
  }],
  "targets": [{
    "address": "eclipse-ditto-sandbox/{%raw%}{{ thing:id }}{%endraw%}",
    "topics": ["_/_/things/twin/events"],
    "authorizationContext": ["ditto:outbound-auth-subject"],
    "qos": 0
  }]
}
```

### Client-certificate authentication

Ditto supports certificate-based authentication for MQTT 5 connections. See
[TLS certificates](connectivity-tls-certificates.html) for setup instructions.

```json
{
  "id": "mqtt5-example-connection-124",
  "connectionType": "mqtt-5",
  "connectionStatus": "open",
  "failoverEnabled": true,
  "uri": "ssl://test.mosquitto.org:8884",
  "validateCertificates": true,
  "ca": "-----BEGIN CERTIFICATE-----\n<broker certificate>\n-----END CERTIFICATE-----",
  "credentials": {
    "type": "client-cert",
    "cert": "-----BEGIN CERTIFICATE-----\n<client certificate>\n-----END CERTIFICATE-----",
    "key": "-----BEGIN PRIVATE KEY-----\n<client private key>\n-----END PRIVATE KEY-----"
  },
  "sources": [{
    "addresses": ["eclipse-ditto-sandbox/#"],
    "authorizationContext": ["ditto:inbound-auth-subject"],
    "qos": 0
  }],
  "targets": [{
    "address": "eclipse-ditto-sandbox/{%raw%}{{ thing:id }}{%endraw%}",
    "topics": ["_/_/things/twin/events"],
    "authorizationContext": ["ditto:outbound-auth-subject"],
    "qos": 0
  }]
}
```

## Further reading

* [Connections overview](basic-connections.html) -- connection model and configuration
* [Payload mapping](connectivity-mapping.html) -- transform message payloads
* [Header mapping](connectivity-header-mapping.html) -- map external headers
* [TLS certificates](connectivity-tls-certificates.html) -- secure connections with TLS
