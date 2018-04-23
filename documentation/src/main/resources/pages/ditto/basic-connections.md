---
title: Connections
keywords: connection
tags: [connection]
permalink: basic-connections.html
---


You can integrate your Ditto instance with external messaging services such as 
[Eclipse Hono](https://eclipse.org/hono/) or a [RabbitMQ](https://www.rabbitmq.com/) broker via custom "connections". 

A connection represents a communication channel for the exchange of messages between any service and Ditto. It 
requires a transport protocol which is used to transmit [Ditto Protocol] messages. Ditto supports one-way and two-way
 communication over connections. This enables fully fledged command and response use cases as well as consumer/producer 
 scenarios. Nevertheless those options can be limited by the used transport protocol and/or the other endpoint's 
 capabilities.
 
All connections are configured and supervised via Ditto's [Connectivity service](architecture-services-connectivity
.html). The connection itself is defined by the following model:

{% include docson.html schema="jsonschema/connection.json" %}

The top design priority of this model is to be generic as possible while still allowing protocol specific 
customizations and tweaks. This enables the implementations of different customizable connection types and support 
for custom payload formats. Currently the following connection types are supported:


* [AMQP 0.9.1](connectivity-protocol-bindings-amqp091.html)
* [AMQP 1.0](connectivity-protocol-bindings-amqp10.html)
 
 
The `sources` and `targets` identifier format depends on the `connectionType` and has therefore `connectionType` 
specific limitations. Those are documented with the corresponding protocol bindings.

A connection handles authorization for all incoming messages.  
To grant or revoke access to a specific resource you 
have to

* auth subject see /basic-acl.html


[Connectivity API]: connectivity-overview.html
[Ditto Protocol]: protocol-overview.html