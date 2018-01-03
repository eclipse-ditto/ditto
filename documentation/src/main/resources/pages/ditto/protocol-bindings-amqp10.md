---
title: AMQP 1.0 protocol binding
keywords: binding, protocol, amqp, amqp10
tags: [protocol]
permalink: protocol-bindings-amqp10.html
---

The Ditto Protocol message can be sent *as is* as **AMQP 1.0** message.
The Ditto Protocol JSON must be sent as `UTF-8` encoded String payload.

The `content-type` of AMQP 1.0 messages must be set to:

```
application/vnd.eclipse.ditto+json
```

## AMQP 1.0 properties

Supported AMQP 1.0 properties which are interpreted in a specific way are:
* `content-type`: for defining the Ditto Protocol content-type
* `reply-to`: for defining the address of the node to send replies to
* `correlation-id`: for correlating request messages to responses

