---
title: Apache Kafka 2.x protocol binding
keywords: binding, protocol, kafka, kafka2
tags: [protocol, connectivity, rql]
permalink: connectivity-protocol-bindings-kafka2.html
---

Send messages to Apache Kafka via [targets](#target-format).

## Content-type

When Kafka messages are sent in [Ditto Protocol](protocol-overview.html), the payload should be `UTF-8` encoded strings.

If messages which are not in Ditto Protocol should be processed, a [payload mapping](connectivity-mapping.html) must
be configured for the connection in order to transform the messages.

## Specific connection configuration

The common configuration for connections in [Connections > Targets](basic-connections.html#targets) applies here 
as well. Following are some specifics for Apache Kafka 2.x connections:

### Source format

{% include warning.html content="Connecting to Kafka and consuming from topics via sources are not yet supported by Ditto." %}

### Target format

A Kafka 2.x connection requires the protocol configuration target object to have an `address` property.
This property may have different formats:

* `topic`: Contains a Kafka topic - a partition will be assigned in a round-robin fashion.
* `topic/key`: Contains a Kafka topic and a key - a partition will be chosen using a hash of the key.
* `topic#partitionNumber`: Contains a Kafka topic and a specific partition number - that partition will be used when sending records. 

The target address may contain placeholders; see
[placeholders](basic-connections.html#placeholder-for-target-addresses) section for more information.

Further, `"topics"` is a list of strings, each list entry representing a subscription of
[Ditto protocol topics](protocol-specification-topic.html), see 
[target topics and filtering](basic-connections.html#target-topics-and-filtering) for more information on that.

Outbound messages are published to the configured target address if one of the subjects in `"authorizationContext"`
have READ permission on the Thing that is associated with a message.

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

### Specific configuration properties

The specific configuration properties contain the following keys:
* `bootstrapServers` (required): contains a comma separated list of Kafka bootstrap servers to use for connecting to


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
      "bootstrapServers": "localhost:9092,other.host:9092"
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
