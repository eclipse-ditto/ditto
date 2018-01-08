---
title: Protocol bindings
keywords: bindings, protocol
tags: [protocol]
permalink: protocol-bindings.html
---

A protocol binding defines how the Ditto protocol messages are transported using a specific network protocol e.g.
“Ditto Protocol over WebSocket”.
The binding defines a set of rules how Ditto protocol messages are mapped to network protocol messages and back.

Currently the following protocol bindings are supported:
* [WebSocket Binding](protocol-bindings-websocket.html)
* [AMQP 1.0 Binding](protocol-bindings-amqp10.html)


## Content Type

Whenever sending messages towards Ditto, the following content type has to be specified in a protocol-specific way.

All [change notifications](basic-changenotifications.html) emitted by Ditto will also contain the `content-type`:

```
application/vnd.eclipse.ditto+json
```
