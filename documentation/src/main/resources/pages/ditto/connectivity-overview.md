---
title: Connectivity API overview
keywords: 
tags: [connectivity]
permalink: connectivity-overview.html
---

The Connectivity API is a bare management API for Ditto's [Connectivity Service](architecture-services-connectivity.html). 
It is available:
* via [DevOps Commands](installation-operating.html#devops-commands)
* via HTTP API with a specific authentication - for details see [Manage connections](connectivity-manage-connections.html) 

Use it to manage client [connections](basic-connections.html) to remote systems and to exchange 
[Ditto Protocol](protocol-specification.html) messages with those. 
If a remote system is unable to send messages in the necessary format, there is the option
to configure custom [payload mapping logic](connectivity-mapping.html) to adapt to almost any message format and 
encoding.

The following connection types are supported:


* [AMQP 0.9.1](connectivity-protocol-bindings-amqp091.html)
* [AMQP 1.0](connectivity-protocol-bindings-amqp10.html)
* [MQTT 3.1.1](connectivity-protocol-bindings-mqtt.html)
* [MQTT 5](connectivity-protocol-bindings-mqtt5.html)
* [HTTP 1.1](connectivity-protocol-bindings-http.html)
* [Kafka 2.x](connectivity-protocol-bindings-kafka2.html)

