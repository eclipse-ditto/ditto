---
title: Header mapping for connections
keywords: header, mapping, placeholder
tags: [connectivity]
permalink: connectivity-header-mapping.html
---

When receiving messages from external systems or sending messages to external systems, the protocol headers of the 
messages can be mapped to and from internal DittoHeaders.

That way the headers can be passed through Ditto or defined DittoHeaders like for example `correlation-id` may be 
mapped to a header used for message correlation in the external system.

A header mapping can be defined individually for every source and target of a connection. For examples of a definition 
see [source header mapping](connectivity-protocol-bindings-amqp091.html#source-header-mapping) 
and [target header mapping](connectivity-protocol-bindings-amqp091.html#target-header-mapping) for AMQP 0.9.1 connections
or [source header mapping](connectivity-protocol-bindings-amqp10.html#source-header-mapping) 
and [target header mapping](connectivity-protocol-bindings-amqp10.html#target-header-mapping) for AMQP 1.0 connections.

## Supported placeholders

The supported placeholders for header mapping are defined in the 
[Placeholders - Scope: Connections](basic-placeholders.html#scope-connections) section.
