---
title: Connections
keywords: 
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
 
All connections are configured via Ditto's [Connectivity service](architecture-services-connectivity.html).


## Connection management

There are

## Supported transport protocols

## Message retention

Not guaranteed

## Handling custom payload


[Ditto Protocol]: protocol-overview.html