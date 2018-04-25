---
title: Connectivity API overview
keywords: 
tags: [connectivity]
permalink: connectivity-overview.html
---

The Connectivity API is a bare management API for Ditto's [Connectivity Service](architecture-services-connectivity.html). 
It completely depends on [DevOps Commands](installation-operating.html#devops-commands) for 
[connection](basic-connections.html) management.

Use it to manage client connections to remote systems and to exchange 
[Ditto Protocol](protocol-specification.html) messages with those. 
If a remote system is unable to send messages in the necessary format, there is the option
to configure custom [payload mapping logic](connectivity-mapping.html) to adapt to almost any message format and 
encoding.

The following connection types are supported:


* [AMQP 0.9.1](connectivity-protocol-bindings-amqp091.html)
* [AMQP 1.0](connectivity-protocol-bindings-amqp10.html)
