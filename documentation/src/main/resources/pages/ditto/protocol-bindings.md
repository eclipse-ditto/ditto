---
title: Protocol bindings
keywords: protocol, bindings
tags: [protocol]
permalink: protocol-bindings.html
---

A protocol binding defines how the Ditto protocol messages are transported using a specific network protocol e.g.
 “Ditto Protocol over WebSocket”. The binding defines a set of rules how Ditto protocol messages are mapped to network protocol messages and back.

Currently the following protocol bindings are supported:

* [WebSocket Binding](protocol-bindings-websocket.html)


## Content Type

Whenever sending messages towards Ditto, you need to specify the following preliminary _Content-Type_ in a protocol 
specific way. All event messages emitted by Ditto will also contain this _Content-Type_:

```
application/vnd.eclipse.ditto+json
```
