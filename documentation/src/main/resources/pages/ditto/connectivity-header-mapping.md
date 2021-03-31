---
title: Header mapping for connections
keywords: header, mapping, placeholder
tags: [connectivity]
permalink: connectivity-header-mapping.html
---

When receiving messages from external systems or sending messages to external systems, the external headers of the 
messages can be mapped to and from Ditto protocol headers.

That way the headers can be passed through Ditto, or defined Ditto protocol headers like for example `correlation-id` 
may be mapped to a header used for message correlation in the external system.

A header mapping can be defined individually for every source and target of a connection. For examples of a definition 
see [source header mapping](basic-connections.html#source-header-mapping) 
and [target header mapping](basic-connections.html#target-header-mapping).

{% include note.html content="Do not map headers prefixed by 'ditto-'. Ditto uses them internally. Setting them in header mapping has no effect." %}

## Supported placeholders

The supported placeholders for header mapping are defined in the 
[Placeholders - Scope: Connections](basic-placeholders.html#scope-connections) section.
If a placeholder fails to resolve for a header value, then that header is not set. Placeholder resolution failure
does not prevent sending of the message or setting other headers with resolved values.
