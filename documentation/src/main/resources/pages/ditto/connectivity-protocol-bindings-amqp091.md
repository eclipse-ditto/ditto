---
title: AMQP 0.9.1 protocol binding
keywords: binding, protocol, amqp, amqp091, rabbitmq
tags: [protocol, connectivity, rql]
permalink: connectivity-protocol-bindings-amqp091.html
---

Consume messages from AMQP 0.9.1 brokers via [sources](#source-format) and send messages to AMQP 0.9.1 brokers via 
[targets](#target-format).

## Content-type

When messages are sent in [Ditto Protocol](protocol-overview.html) (as `UTF-8` encoded String payload), 
the `content-type` of AMQP 0.9.1 messages must be set to:

```
application/vnd.eclipse.ditto+json
```

If messages, which are not in Ditto Protocol, should be processed, a [payload mapping](connectivity-mapping.html) must
be configured for the AMQP 0.9.1 connection in order to transform the messages. 

## AMQP 0.9.1 properties

Supported AMQP 0.9.1 properties which are interpreted in a specific way are:

* `content-type`: for defining the Ditto Protocol content-type
* `correlation-id`: for correlating request messages to responses

## Specific connection configuration

The common configuration for connections in [Connections > Sources](basic-connections.html#sources) and 
[Connections > Targets](basic-connections.html#targets) applies here as well. 
Following are some specifics for AMQP 0.9.1 connections:

### Source format

An AMQP 0.9.1 connection requires the protocol configuration source object to have an `addresses` property with a list
of queue names, and `authorizationContext` array that contains the authorization subjects, in whose context 
incoming messages are processed. These subjects may contain placeholders, see 
[placeholders](basic-connections.html#placeholder-for-source-authorization-subjects) section for more information.


```json
{
  "addresses": [
    "<queue_name>",
    "..."
  ],
  "authorizationContext": ["ditto:inbound-auth-subject", "..."]
}
```

#### Source acknowledgement handling

For AMQP 0.9.1 sources, when configuring 
[acknowledgement requests](basic-connections.html#source-acknowledgement-requests), consumed messages from the AMQP 0.9.1
broker are treated in the following way:

For Ditto acknowledgements with successful [status](protocol-specification-acks.html#combined-status-code):
* Acknowledges a single AMQP 0.9.1 message with an `Ack` message for the received `deliveryTag`

For Ditto acknowledgements with mixed successful/failed [status](protocol-specification-acks.html#combined-status-code):
* If some of the aggregated [acknowledgements](basic-acknowledgements.html#acknowledgements-acks) require redelivery (e.g. based on a timeout):
   * Negatively acknowledges the AMQP 0.9.1 message with a `Nack` message for the received `deliveryTag` and setting `requeue` to `true`
* If none of the aggregated [acknowledgements](basic-acknowledgements.html#acknowledgements-acks) require redelivery:
   * Negatively acknowledges the AMQP 0.9.1 message with a `Nack` message for the received `deliveryTag` and setting `requeue` to `false` preventing redelivery by the AMQP 0.9.1 broker


### Target format

An AMQP 0.9.1 connection requires the protocol configuration target object to have an `address` property with a combined
value of the `exchange_name` and `routing_key`. The target address may contain placeholders; see
[placeholders](basic-connections.html#placeholder-for-target-addresses) section for more information.

Further, `"topics"` is a list of strings, each list entry representing a subscription of
[Ditto protocol topics](protocol-specification-topic.html).

Outbound messages are published to the configured target address if one of the subjects in `"authorizationContext"`
has READ permission on the thing, which is associated with a message.


```json
{
  "address": "<exchange_name>/<routing_key>",
  "topics": [
    "_/_/things/twin/events",
    "_/_/things/live/messages"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject"]
}
```

#### Target acknowledgement handling

For AMQP 0.9.1 targets, when configuring 
[automatically issued acknowledgement labels](basic-connections.html#target-issued-acknowledgement-label), requested 
acknowledgements are produced in the following way:

Once the AMQP 0.9.1 client signals that the message was acknowledged by the AMQP 0.9.1 broker, the following information 
is mapped to the automatically created [acknowledgement](protocol-specification-acks.html#acknowledgement):
* Acknowledgement.status: 
   * will be `200`, if the message was successfully ACKed by the AMQP 0.9.1 broker
   * will be `400`, if the AMQP 0.9.1 broker does not support publisher confirms
   * will be `503`, if the AMQP 0.9.1 broker negatively confirmed receiving a message
* Acknowledgement.value: 
   * will be missing, for status `200`
   * will contain more information, in case that an error `status` was set

### Specific configuration properties

There are no specific configuration properties available for this type of connection.

## Establishing connecting to an AMQP 0.9.1 endpoint

Ditto's [Connectivity service](architecture-services-connectivity.html) is responsible for creating new and managing 
existing connections.

This can be done dynamically at runtime without the need to restart any microservice using a
[Ditto DevOps command](installation-operating.html#devops-commands).

Example connection configuration to create a new AMQP 0.9.1 connection (e.g. in order to connect to a RabbitMQ):

```json
{
  "connection": {
    "id": "rabbit-example-connection-123",
    "connectionType": "amqp-091",
    "connectionStatus": "open",
    "failoverEnabled": true,
    "uri": "amqp://user:password@localhost:5672/vhost",
    "sources": [
      {
        "addresses": [
          "queueName"
        ],
        "authorizationContext": ["ditto:inbound-auth-subject", "..."]
      }
    ],
    "targets": [
      {
        "address": "exchangeName/routingKey",
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
