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

## Special header mapping keys

In addition to general header mapping capabilities, Ditto recognizes several special header mapping keys that control connectivity behavior:

### Response diversion headers

These headers control [response diversion](connectivity-response-diversion.html) functionality:

| Header Key | Description | Example Values                                                 |
|------------|-------------|----------------------------------------------------------------|
| `divert-response-to` | Target connection ID for response diversion | `"target-connection-id"`, `"target-connection-id"` |
| `divert-expected-response-types` | Response types to divert (comma-separated) | `"response"`, `"error"`, `"nack"`                |
| `diverted-response-from` | Source connection of diverted response (automatically set) | `"source-connection-id"`                                       |

Example source configuration with response diversion:
```json
{
  "headerMapping": {
    "divert-response-to": "webhook-connection",
    "divert-expected-response-types": "response,error",
    "device-id": "{{ header:device_id }}"
  }
}
```

### Protocol-specific headers

Different protocols support different sets of headers. Some protocols also have special header behavior:

{% include note.html content="Response diversion headers (`ditto-divert-*`)
are processed by Ditto internally and are not sent as external protocol headers.
They control internal routing behavior only."
%}
