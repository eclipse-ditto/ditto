---
title: Protocol bindings
keywords: bindings, protocol
tags: [protocol]
permalink: protocol-bindings.html
---

A protocol binding defines how the Ditto protocol messages are transported using a specific network protocol e.g.
"Ditto Protocol over WebSocket".
The binding defines a set of rules how Ditto protocol messages are mapped to network protocol messages and back.

Currently the following protocol bindings are supported:

* [WebSocket](httpapi-protocol-bindings-websocket.html)
* [AMQP 1.0](connectivity-protocol-bindings-amqp10.html)
* [AMQP 0.9.1](connectivity-protocol-bindings-amqp091.html)
* [MQTT 3.1.1](connectivity-protocol-bindings-mqtt.html)
* [MQTT 5](connectivity-protocol-bindings-mqtt5.html)
* [HTTP 1.1](connectivity-protocol-bindings-http.html)
* [Kafka 2.x](connectivity-protocol-bindings-kafka2.html)


## Content Type

When sending messages towards Ditto, the following content type has to be specified in a protocol-specific way given 
that the messages are already in [Ditto Protocol](protocol-overview.html) format.

All [change notifications](basic-changenotifications.html) emitted by Ditto will also contain the `content-type`:

```
application/vnd.eclipse.ditto+json
```

For messages which are not yet in that format, the [payload mapping](connectivity-mapping.html) of Ditto's 
[connectivity](connectivity-overview.html) may be used to bring the messages in that format.
