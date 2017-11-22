---
title: Protocol bindings
keywords: bindings, protocol
tags: [protocol]
permalink: protocol-bindings.html
---

A protocol binding defines how the Ditto protocol messages are transported using a specific network protocol e.g.
“Ditto Protocol over WebSocket”.
The binding defines a set of rules how Ditto protocol messages are mapped to network protocol messages and back.

Currently only the [WebSocket Binding](protocol-bindings-websocket.html) is supported.


## Content Type

Whenever sending messages towards Ditto, you need to specify the following preliminary content type in a
protocol-specific way.
All event messages emitted by Ditto will also contain the `content-type`:

```
application/vnd.eclipse.ditto+json
```
