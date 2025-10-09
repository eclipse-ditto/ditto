---
title: "Response diversion - Multi-protocol workflows made easy"
published: true
permalink: 2025-10-09-response-diversion.html
layout: post
author: aleksandar_stanchev
tags: [blog, connectivity]
hide_sidebar: true
sidebar: false
toc: true
---

Today we're excited to announce a powerful new connectivity feature in Eclipse Ditto: **Response Diversion**. 
This feature enables sophisticated multiprotocol workflows by allowing responses from one connection to be redirected to another connection instead of being sent to the originally configured reply target.

With response diversion, Eclipse Ditto becomes even more versatile in bridging different IoT protocols and systems, 
enabling complex routing scenarios that were previously challenging or impossible to achieve.

## The challenge: Multi-protocol IoT landscapes

Modern IoT deployments often involve multiple protocols and systems working together. Consider these common scenarios:

- **Cloud integration**: Your devices use MQTT to communicate with AWS IoT Core, but your analytics pipeline consumes data via Kafka
- **Protocol translation**: Legacy systems expect HTTP webhooks, but your devices communicate via AMQP
- **Response aggregation**: You want to collect all device responses in a central monitoring system regardless of the original protocol

Until now, implementing such multiprotocol workflows required complex external routing logic or multiple intermediate systems. 
Response diversion brings this capability directly into Ditto's connectivity layer.

## How response diversion works

Response diversion is configured at the connection source level using a key in the specific config and special header mapping keys:

```json
{
  "headerMapping": {
    "divert-response-to-connection": "target-connection-id", 
    "divert-expected-response-types": "response,error,nack"
   }, 
    "specificConfig": {
        "is-diversion-source": "true"
    }
}
``` 

And in the target connection, by defining a target.
In the case of multiple sources one or exactly the same number of sources targets are required.
If multiple targets are configured they are mapped to the sources by order.
Only diverted responses will be accepted by source connections which ids are defined in the specific config under the key
'authorized-connections-as-sources' in a comma separate format.

```json
{
    "id": "target-connection-id-1",
    "targets": [
        {
            "address": "command/redirected/response",
            "topics": [],
            "qos": 1,
            "authorizationContext": [
                "pre:ditto"
            ],
            "headerMapping": {}
        }
    ],
    "specificConfig": {
        "is-diversion-target": "true"
    }
}
```
```json
  {
    "targets": [
        {
            "address": "command/redirected/response",
            "topics": [],
            "qos": 1,
            "authorizationContext": [
                "pre:ditto"
            ],
            "headerMapping": {}
        }
    ],
    "specificConfig": {
        "is-diversion-target": "true",
        "authorized-connections-as-sources": "target-connection-id-1,..."
    }
}


```

When a command is received through a source with response diversion configured, Ditto intercepts the response and routes it through the specified target connection instead of the original reply target.

## Real-world use case: AWS IoT Core with Kafka 

Let's explore a practical scenario that demonstrates the power of response diversion. In this setup:

- Devices communicate with **AWS IoT Core** via MQTT (bidirectional)
- **Apache Kafka** IoT Core pushes device commands to a Kafka topic
- Device commands are consumed from Kafka topics
- **Responses must go back to AWS IoT Core via MQTT** (since IoT Core doesn't support Kafka consumers)

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  AWS IoT Core   │    │   Kafka Bridge  │    │  Apache Kafka   │    │  Eclipse Ditto  │
│    (MQTT)       │    │   /Analytics    │    │                 │    │                 │
│                 │    │                 │    │                 │    │                 │
│ ┌─────────────┐ │    │ ┌─────────────┐ │    │ ┌─────────────┐ │    │ ┌─────────────┐ │
│ │Device       │ │───▶│ │MQTT→Kafka   │ │───▶│ │device-      │ │───▶│ │Kafka Source │ │
│ │Commands     │ │    │ │Bridge       │ │    │ │commands     │ │    │ │Connection   │ │
│ │(MQTT topics)│ │    │ │             │ │    │ │topic        │ │    │ │             │ │
│ └─────────────┘ │    │ └─────────────┘ │    │ └─────────────┘ │    │ └─────────────┘ │
│        ▲        │    │                 │    │                 │    │        │        │
│        │        │    │                 │    │                 │    │        ▼        │
│        │        │    │                 │    │                 │    │ ┌─────────────┐ │
│        │        │    │                 │    │                 │    │ │Command      │ │
│        │        │    │                 │    │                 │    │ │Processing   │ │
│        │        │    │                 │    │                 │    │ │             │ │
│        │        │    │                 │    │                 │    │ └─────────────┘ │
│        │        │    │                 │    │                 │    │        │        │
│        │        │    │                 │    │                 │    │        ▼        │
│        │        │    │                 │    │                 │    │ ┌─────────────┐ │
│        │        │    │                 │    │                 │    │ │Response     │ │
│        │        │    │                 │    │                 │    │ │Diversion    │ │
│        │        │    │                 │    │                 │    │ │Interceptor  │ │
│        │        │    │                 │    │                 │    │ └─────────────┘ │
│        │        │    │                 │    │                 │    │        │        │
│        │        │    │                 │    │                 │    │        ▼        │
│ ┌─────────────┐ │    │                 │    │                 │    │ ┌─────────────┐ │
│ │Device       │ │◀───┼─────────────────┼────┼─────────────────┼────│ │MQTT Target  │ │
│ │Responses    │ │    │                 │    │                 │    │ │Connection   │ │
│ │(MQTT topics)│ │    │                 │    │                 │    │ │(AWS IoT)    │ │
│ └─────────────┘ │    │                 │    │                 │    │ └─────────────┘ │
└─────────────────┘    └─────────────────┘    └─────────────────┘    └─────────────────┘

Legend:
───▶ Command Flow (MQTT → Kafka → Ditto)
◀─── Response Flow (Ditto → MQTT, bypassing Kafka)

```

### Example Configuration

First, create the Kafka connection that consumes device commands:

```json
{
  "id": "kafka-commands-connection",
  "connectionType": "kafka",
  "connectionStatus": "open",
  "uri": "tcp://kafka-broker:9092",
  "specificConfig": {
    "bootstrapServers": "kafka-broker:9092",
    "saslMechanism": "plain"
  },
  "sources": [{
    "addresses": ["device-commands"],
    "authorizationContext": ["ditto:kafka-consumer"],
    "headerMapping": {
      "device-id": "{%raw%}{{ header:device-id }}{%endraw%}",
      "divert-response-to-connection": "aws-iot-mqtt-connection",
      "divert-expected-response-types": "response,error"
    }
  }]
}
```

Next, create the MQTT connection that will handle diverted responses:

```json
{
  "id": "aws-iot-mqtt-connection", 
  "connectionType": "mqtt",
  "connectionStatus": "open",
  "uri": "ssl://your-iot-endpoint.amazonaws.com:8883",
  "sources": [],
  "targets": [
      {
    "address": "device/{%raw%}{{ header:device-id }}{%endraw%}/response",
    "topics": [],
    "headerMapping": {
      "device-id": "{%raw%}{{ header:device-id }}{%endraw%}",
      "correlation-id": "{%raw%}{{ header:correlation-id }}{%endraw%}"
    }
  }
  ],
    "specificConfig": {
        "is-diversion-target": "true"
    }
}
```

### Flow explanation

1. **Command ingestion**: Kafka connection consumes device commands from the `device-commands` topic
2. **Response diversion**: Commands are configured to divert responses to the `aws-iot-mqtt-connection`
3. **Response routing**: Responses are automatically published to AWS IoT Core via MQTT on the device-specific response topic
4. **Device notification**: Devices receive responses via their subscribed MQTT topics in AWS IoT Core

This setup enables a seamless flow from Kafka-based systems back to MQTT-based device communication without requiring external routing logic.

## Try it out

Response diversion is available starting with Eclipse Ditto version 3.8.0. Update your deployment and start experimenting with multi-protocol workflows!

The feature documentation provides comprehensive configuration examples and troubleshooting guidance. We'd love to hear about your use cases and feedback.

Get started with response diversion today and unlock new possibilities for your IoT connectivity architecture.

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team