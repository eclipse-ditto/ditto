---
title: Connections
keywords: connection, connectivity, mapping, connection, integration
tags: [connectivity]
permalink: basic-connections.html
---

  {%
    include note.html content="To get started with connections right away, consolidate the [Manage connections](/connectivity-manage-connections.html) 
                               page. "
  %}

You can integrate your Ditto instance with external messaging services such as 
[Eclipse Hono](https://eclipse.org/hono/) or a [RabbitMQ](https://www.rabbitmq.com/) broker via custom "connections". 

A connection represents a communication channel for the exchange of messages between any service and Ditto. It 
requires a transport protocol, which is used to transmit [Ditto Protocol] messages. Ditto supports one-way and two-way
 communication over connections. This enables consumer/producer scenarios as well as fully-fledged command and response use cases. Nevertheless, those options might be limited by the used transport protocol and/or the other endpoint's 
 capabilities.
 
All connections are configured and supervised via Ditto's 
[Connectivity service](architecture-services-connectivity.html). The following model defines the connection itself:

{% include docson.html schema="jsonschema/connection.json" %}

The top design priority of this model is to be as generic as possible, while still allowing protocol specific 
customizations and tweaks. This enables the implementations of different customizable connection types, and support 
for custom payload formats. Currently the following connection types are supported:


* [AMQP 0.9.1](connectivity-protocol-bindings-amqp091.html)
* [AMQP 1.0](connectivity-protocol-bindings-amqp10.html)
 
 
The `sources` and `targets` identifier format depends on the `connectionType` and has therefore `connectionType` 
specific limitations. Those are documented with the corresponding protocol bindings.

A connection is initiated by the connectivity service. This obsoletes the need for client authorization, because 
Ditto becomes the client in this case. Nevertheless, to access resources within Ditto, the connection must know on 
which instance’s behalf it is acting. This is controlled via the configured `authorisationContext`, which holds a list of 
self-assigned authorization subjects. Before a connection can access a Ditto resource, one of its 
`authorizationSubject`s must be referenced in the used authorization mechanism, having the needed access rights. You 
can achieve this via [ACLs](basic-acl.html) or [Policies](basic-policy.html).

For more information on the `mappingContext` see the corresponding [Payload Mapping Documentation](/connectivity-mapping.html)


[Connectivity API]: connectivity-overview.html
[Ditto Protocol]: protocol-overview.html
