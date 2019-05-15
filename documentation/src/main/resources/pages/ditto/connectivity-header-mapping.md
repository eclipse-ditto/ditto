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
see [source header mapping](basic-connections.html#source-header-mapping) 
and [target header mapping](basic-connections.html#target-header-mapping).

## Supported placeholders

The supported placeholders for header mapping are defined in the 
[Placeholders - Scope: Connections](basic-placeholders.html#scope-connections) section.
