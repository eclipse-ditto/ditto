---
title: Apache Kafka 2.x protocol binding
keywords: binding, protocol, kafka, kafka2
tags: [protocol, connectivity, rql]
permalink: connectivity-protocol-bindings-kafka2.html
---

Consume messages from Apache Kafka brokers via [sources](#source-format) and send messages to Apache Kafka brokers via
[targets](#target-format).

## Content-type

When messages are sent in [Ditto Protocol](protocol-overview.html) (as `UTF-8` encoded String payload),
the `content-type` of Apache Kafka messages must be set to:

```
application/vnd.eclipse.ditto+json
```

If messages, which are not in Ditto Protocol, should be processed, a [payload mapping](connectivity-mapping.html) must
be configured for the connection in order to transform the messages.

## Global Kafka client configuration

The behavior of the used Kafka client can be configured in the [connectivity.conf](https://github.com/eclipse/ditto/blob/master/connectivity/service/src/main/resources/connectivity.conf)
under key `ditto.connectivity.connection.kafka`:
* `consumer`: The Kafka consumer configuration applied when configuring [sources](#source-format) in order to consume messages from Kafka
* `committer`: The Kafka committer configuration to apply when consuming messages, e.g. the `max-batch` size and `max-interval` duration
* `producer`: The Kafka producer configuration applied when configuring [targets](#target-format) in order to publish messages to Kafka

## Specific connection configuration

The common configuration for connections in [Connections > Targets](basic-connections.html#targets) applies here 
as well. Following are some specifics for Apache Kafka 2.x connections:

### Source format
For a Kafka connection source "addresses" are Kafka topics to subscribe to. Legal characters are `[a-z]`, `[A-Z]`, `[0-9]`, `.`, `_` and `-`.

Messages are either consumed in an "at-most-once" or "at-least-once" manner depending on the 
configured `"qos"` (Quality of Service) value of the source:
* `"qos": 0` (at-most-once): This means that the offset will be committed after Ditto consumed the message from Kafka, 
  no matter if the message could be processed or not.
* `"qos": 1` (at-least-once): This means that the offset will only be committed after 
  [requested acknowledgements](basic-acknowledgements.html#requesting-acks) were successfully issued.

The following example shows a valid Kafka source:
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
#### Quality of Service

The shown example with the configured `"qos": 1` has the following behavior: 
* Kafka messages from the topic `"theAddress"` are consumed in an "at-least-once" fashion, e.g.
  [twin modify commands](basic-signals-command.html#modify-commands) will implicitly request the 
  [built-in acknowledgement label](basic-acknowledgements.html#built-in-acknowledgement-labels) `"twin-persisted"` meaning
  that the consumed message will only be committed to Kafka after it was successfully persisted by Ditto
* When a consumed Kafka message could not be acknowledged by Ditto (e.g. because persisting a consumed command failed), 
  consuming from the Kafka source will be restarted which means that message consumption will restart from the last 
  committed offset of the Kafka topic, already successfully processed messages could be processed again as a result 
  (which is the "at-least-once" semantic).  

For Kafka sources, it is not possible to have different Quality of Service on a per message basis.
Either all messages from a source are consumed in an "at-most-once" or in an "at-least-once" semantic, depending on the 
configured `"qos"` value.


#### Source header mapping

The Kafka protocol binding supports to map arbitrary headers from a consumed record to the message that is further 
processed by Ditto (see [Header Mapping](connectivity-header-mapping.html)). 

In addition, there are three special headers extracted from every received record that can be used in a payload or 
header mapping:
* `kafka.topic`: contains the Kafka topic the record was received from 
* `kafka.key`: contains the key of the received record (only set if key is available)
* `kafka.timestamp`: contains the timestamp of the received record 

These headers may be used in a source header mapping:
```json
{
  "headerMapping": {
    "the-topic": "{%raw%}{{ header:kafka.topic }}{%endraw%}",
    "the-key": "{%raw%}{{ header:kafka.key }}{%endraw%}"
  }
}
```

#### Message expiry

In the Ditto implementation for consuming messages from Kafka we also added a feature for message expiration. This way a device can express for how long a message is valid to be processed.
To use this feature, two headers are relevant:
* `creation-time`: Epoch millis value when the message was created.
* `ttl`: Number milliseconds the message should be considered as valid.

When Ditto consumes such a message it checks whether the amount of milliseconds since `creation-time` is larger than specified by `ttl`.
If so, the message will be ignored.
If this is not the case or the headers are not specified at all, the message will be processed normally.

#### Backpressure by using acknowledgements

For Kafka Sources one can use [acknowledements](basic-acknowledgements.html) to achieve backpressure from the event/message consuming application down to the Kafka consumer in Ditto.
So if for example [live messages](basic-messages.html) should be consumed via the Kafka connection, you could want that the consume rate adapts to the performance of the message consuming and responding application.

For this scenario there is nothing that needs to be configured explicitly. Since the `live-response` is a built in acknowledgement, it is requested by default.
The same applies for [twin modify commands](basic-signals-command.html#modify-commands). 
For those type of commands the `twin-persisted` acknowledgement is requested automatically which would cause backpressure from the persistence to the Kafka consumer.

If for some reason you don't want to have this backpressure, because losing some messages due to for example overflowing buffers is not a problem for you, you can disable requesting acknowledgements for the Kafka source.
This can be done by configuring the following for your source:

```json
"acknowledgementRequests": {
  "includes": [],
  "filter": "fn:delete()"
}
```

If you however want to achieve backpressure from an event consuming application to the Kafka consumer, you could use custom [acknowledgement requests](basic-acknowledgements.html#requesting-acks).

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
* `groupId`: The consumer group ID to be used by the kafka consumer. If not defined the group ID will be equal to the connection ID.


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
    "sources": [
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
    ],
    "targets": [
      {
        "address": "topic/key",
        "topics": [
          "_/_/things/twin/events",
          "_/_/things/live/messages"
        ],
        "authorizationContext": ["ditto:outbound-auth-subject"]
      }
    ]
  }
}
```
