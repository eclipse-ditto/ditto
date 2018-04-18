---
title: AMQP 0.9.1 protocol binding
keywords: binding, protocol, amqp, amqp091, rabbitmq
tags: [protocol, connectivity]
permalink: connectivity-protocol-bindings-amqp091.html
---

The Ditto Protocol message can be sent *as is* as **AMQP 0.9.1** message.
The Ditto Protocol JSON must be sent as `UTF-8` encoded String payload.

The `content-type` of AMQP 0.9.1 messages must be set to:

```
application/vnd.eclipse.ditto+json
```

## AMQP 0.9.1 properties

Supported AMQP 0.9.1 properties which are interpreted in a specific way are:

* `content-type`: for defining the Ditto Protocol content-type
* `correlation-id`: for correlating request messages to responses


## Establishing connecting to an AMQP 0.9.1 endpoint

Ditto's [Connectivity service](architecture-services-connectivity.html) is responsible for creating new and managing 
existing connections.

This can be done dynamically at runtime without the need to restart any microservice using a
[Ditto operations command](installation-operating.html#connectivity-service-commands).


## Messages

Messages consumed via the AMQP 1.0 binding are treated similar to the [WebSocket binding](httpapi-protocol-bindings-websocket.html)
meaning that the messages are expected to be [Ditto Protocol](protocol-overview.html) messages serialized as JSON (as 
shown for example in the [protocol examples](protocol-examples.html)).
 
