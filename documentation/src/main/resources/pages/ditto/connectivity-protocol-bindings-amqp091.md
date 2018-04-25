---
title: AMQP 0.9.1 protocol binding
keywords: binding, protocol, amqp, amqp091, rabbitmq
tags: [protocol, connectivity]
permalink: connectivity-protocol-bindings-amqp091.html
---

When messages are sent in [Ditto Protocol](protocol-overview.html) (as `UTF-8` encoded String payload), 
the `content-type` of AMQP 0.9.1 messages must be set to:

```
application/vnd.eclipse.ditto+json
```

If messages which are not in Ditto Protocol should be processed, a [payload mapping](connectivity-mapping.html) must
be configured for the AMQP 0.9.1 connection in order to transform the messages. 

## AMQP 0.9.1 properties

Supported AMQP 0.9.1 properties which are interpreted in a specific way are:

* `content-type`: for defining the Ditto Protocol content-type
* `correlation-id`: for correlating request messages to responses

## Specific connection configuration

### Source format

An AMQP 0.9.1 connection requires the protocol configuration source object to have an `addresses` property with a list
of queue names.

```json
{
  "addresses": [
    "<queue_name>",
    "..."
  ]
}
```

### Target format

An AMQP 0.9.1 connection requires the protocol configuration target object to have an `address` property with a combined
 value of the `exchange_name` and `routing_key`. It is continued with a list of topic strings, each representing a
 subscription of a Ditto [protocol topic](protocol-specification-topic.html).


```json
{
  "address": "<exchange_name>/<routing_key>",
  "topics": [
    "_/_/things/twin/events",
    "_/_/things/live/messages"
  ]
}
```

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
    "authorizationSubject": "<<<my-subject-id-included-in-policy-or-acl>>>",
    "failoverEnabled": true,
    "uri": "amqp://user:password@localhost:5672",
    "sources": [
      {
        "addresses": [
          "queueName"
        ]
      }
    ],
    "targets": [
      {
        "address": "exchangeName/routingKey",
        "topics": [
          "_/_/things/twin/events",
          "_/_/things/live/messages"
        ]
      }
    ]
  }
}
```

## Messages

Messages consumed via the AMQP 0.9.1 binding are treated similar to the [WebSocket binding](httpapi-protocol-bindings-websocket.html)
meaning that the messages are expected to be [Ditto Protocol](protocol-overview.html) messages serialized as JSON (as 
shown for example in the [protocol examples](protocol-examples.html)). If your payload is not conform to the [Ditto
Protocol](protocol-overview.html), you can configure a custom [payload mapping](connectivity-mapping.html).
 
