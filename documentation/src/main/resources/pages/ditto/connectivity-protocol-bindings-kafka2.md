---
title: Apache Kafka 2.x protocol binding
keywords: binding, protocol, kafka, kafka2
tags: [protocol, connectivity, rql]
permalink: connectivity-protocol-bindings-kafka2.html
---

Send messages to Apache Kafka via [targets](#target-format).

## Content-type

When Kafka messages are sent in [Ditto Protocol](protocol-overview.html), the payload should be `UTF-8` encoded strings.

If messages, which are not in Ditto Protocol, should be processed, a [payload mapping](connectivity-mapping.html) must
be configured for the connection in order to transform the messages.

## Specific connection configuration

The common configuration for connections in [Connections > Targets](basic-connections.html#targets) applies here 
as well. Following are some specifics for Apache Kafka 2.x connections:

### Source format
For a Kafka connection source "addresses" are Kafka topics to subscribe to. Legal characters are `[a-z]`, `[A-Z]`, `[0-9]`, `.`, `_` and `-`.

All messages are consumed in an "At-Most-Once" manner. This means that the offset will be committed after ditto consumed the message from kafka, no matter if the message can be processed correctly or not. Ditto's acknowledgement feature is right now not supported for Kafka consumers.

The following example shows a valid kafka source:
```json
{
  "addresses": ["theAddress"],
  "consumerCount": 1,
  "authorizationContext": ["ditto:inbound-auth-subject"],
  "enforcement": {
    "input": "{%raw%}{{ header:device_id }}{%endraw%}",
    "filters": ["{%raw%}{{ entity:id }}{%endraw%}"]
  },
  "headerMapping": {},
  "payloadMapping": ["Ditto"],
  "replyTarget": {
    "enabled": true,
    "address": "theReplyAddress",
    "headerMapping": {},
    "expectedResponseTypes": ["response", "error", "nack"]
  },
  "acknowledgementRequests": {
    "includes": [],
    "filter": "fn:filter(header:qos,\"ne\",\"0\")"
  },
  "declaredAcks": []
}
```

### Target format

A Kafka 2.x connection requires the protocol configuration target object to have an `address` property.
This property may have different formats:

* `topic`: Contains a Kafka topic - a partition will be assigned in a round-robin fashion.
* `topic/key`: Contains a Kafka topic and a key - Kafka ensures that messages with the same key end up in the same partition.
* `topic#partitionNumber`: Contains a Kafka topic and a specific partition number - that partition will be used when sending records. 

The target address may contain placeholders; see
[placeholders](basic-connections.html#placeholder-for-target-addresses) section for more information.

Further, `"topics"` is a list of strings, each list entry representing a subscription of
[Ditto protocol topics](protocol-specification-topic.html), see 
[target topics and filtering](basic-connections.html#target-topics-and-filtering) for more information on that.

Outbound messages are published to the configured target address if one of the subjects in `"authorizationContext"`
has READ permission on the thing, which is associated with a message.

```json
{
  "address": "<kafka_topic>/<kafka_key>",
  "topics": [
    "_/_/things/twin/events",
    "_/_/things/live/messages"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject"]
}
```

#### Target acknowledgement handling

For Kafka targets, when configuring 
[automatically issued acknowledgement labels](basic-connections.html#target-issued-acknowledgement-label), requested 
acknowledgements are produced in the following way:

Once the Kafka client signals that the message was acknowledged by the Kafka broker, the following information is mapped
to the automatically created [acknowledement](protocol-specification-acks.html#acknowledgement):
* Acknowledgement.status: 
   * will be `204`, if Kafka debug mode was disabled and the message was successfully consumed by Kafka
   * will be `200`, if Kafka debug mode was enabled (see [specific config](#specific-configuration-properties) `"debugEnabled"`) and the message was successfully consumed by Kafka
   * will be `4xx`, if Kafka failed to consume the message but retrying sending the message does not make sense
   * will be `5xx`, if Kafka failed to consume the message but retrying sending the message is feasible
* Acknowledgement.value: 
   * will be missing, if Kafka debug mode (see [specific config](#specific-configuration-properties) `"debugEnabled"`) was disabled
   * will include the Kafka `RecordMetadata` as JsonObject:
      * `timestamp` (if present)
      * `serializedKeySize`
      * `serializedValueSize`
      * `topic`
      * `partition`
      * `offset` (if present)

### Specific configuration properties

The specific configuration properties contain the following keys:
* `bootstrapServers` (required): contains a comma separated list of Kafka bootstrap servers to use for connecting to
(in addition to the still required connection uri)
* `saslMechanism` (required if connection uri contains username\/password): contains one of the following SASL mechanisms to use for authentication at Kafka:
    * `plain`
    * `scram-sha-256`
    * `scram-sha-512`
* `debugEnabled`: determines whether for acknowledgements 
  [automatically issued by Kafka targets](#target-acknowledgement-handling) additional debug information should be 
  included as payload or not - default: `false`


## Establishing connecting to an Apache Kafka endpoint

Ditto's [Connectivity service](architecture-services-connectivity.html) is responsible for creating new and managing 
existing connections.

This can be done dynamically at runtime without the need to restart any microservice using a
[Ditto DevOps command](installation-operating.html#devops-commands).

Example connection configuration to create a new Kafka 2.x connection in order to connect to a running Apache Kafka server:

```json
{
  "connection": {
    "id": "kafka-example-connection-123",
    "connectionType": "kafka",
    "connectionStatus": "open",
    "failoverEnabled": true,
    "uri": "tcp://user:password@localhost:9092",
    "specificConfig": {
      "bootstrapServers": "localhost:9092,other.host:9092",
      "saslMechanism": "plain"
    },
    "sources": [],
    "targets": [
      {
        "address": "topic/key",
        "topics": [
          "_/_/things/twin/events",
          "_/_/things/live/messages"
        ],
        "authorizationContext": ["ditto:outbound-auth-subject", "..."]
      }
    ]
  }
}
```
