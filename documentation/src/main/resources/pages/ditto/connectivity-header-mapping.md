---
title: Header Mapping
keywords: header, mapping, placeholder
tags: [connectivity]
permalink: connectivity-header-mapping.html
---

Header mapping lets you translate between external message headers and Ditto protocol headers, enabling correlation IDs, content types, and custom metadata to flow through Ditto.

{% include callout.html content="**TL;DR**: Define a `headerMapping` object on any source or target to map external headers to/from Ditto protocol headers using placeholders." type="primary" %}

## Overview

When Ditto receives messages from or sends messages to external systems, you can map headers between
the external protocol and the Ditto protocol. This allows you to:

* Pass headers through Ditto (for example, correlation IDs)
* Map protocol-specific headers to Ditto headers
* Set custom external headers on outgoing messages

You define header mappings individually for each source and target. For configuration examples, see
[source header mapping](basic-connections.html#source-header-mapping) and
[target header mapping](basic-connections.html#target-header-mapping).

{% include note.html content="Do not map headers prefixed by 'ditto-'. Ditto uses them internally. Setting them in header mapping has no effect." %}

## How it works

A header mapping is a JSON object where:

* **Keys** are the target header names
* **Values** are strings that can include [placeholders](basic-placeholders.html#scope-connections)

For **inbound** messages (sources), the mapping translates external headers to Ditto protocol headers.
For **outbound** messages (targets), the mapping translates Ditto protocol headers to external headers.

If a placeholder fails to resolve, that header is skipped. Other headers and the message itself
are still processed.

## Configuration

### Source header mapping example

```json
{
  "addresses": ["telemetry"],
  "authorizationContext": ["ditto:inbound-auth-subject"],
  "headerMapping": {
    "correlation-id": "{%raw%}{{ header:message-id }}{%endraw%}",
    "content-type": "{%raw%}{{ header:content-type }}{%endraw%}",
    "device-id": "{%raw%}{{ header:device_id }}{%endraw%}"
  }
}
```

### Target header mapping example

```json
{
  "address": "events/twin",
  "topics": ["_/_/things/twin/events"],
  "authorizationContext": ["ditto:outbound-auth-subject"],
  "headerMapping": {
    "message-id": "{%raw%}{{ header:correlation-id }}{%endraw%}",
    "content-type": "{%raw%}{{ header:content-type }}{%endraw%}",
    "subject": "{%raw%}{{ topic:subject }}{%endraw%}",
    "reply-to": "all-replies"
  }
}
```

## Supported placeholders

The supported placeholders for header mapping are defined in the
[Placeholders - Scope: Connections](basic-placeholders.html#scope-connections) section.

## Special header mapping keys

Ditto recognizes several special header keys that control connectivity behavior.

### Response diversion headers

These headers control [response diversion](connectivity-response-diversion.html):

| Header Key | Description |
|------------|-------------|
| `divert-response-to-connection` | Target connection ID for response diversion |
| `divert-expected-response-types` | Response types to divert (comma-separated: `response`, `error`, `nack`) |
| `diverted-response-from-connection` | Source connection of the diverted response (set automatically) |

```json
{
  "headerMapping": {
    "divert-response-to-connection": "webhook-connection",
    "divert-expected-response-types": "response,error",
    "device-id": "{%raw%}{{ header:device_id }}{%endraw%}"
  }
}
```

### Protocol-specific headers

Different protocols support different header capabilities:

* **AMQP 1.0** -- full application properties and message annotations
* **MQTT 5** -- user-defined properties
* **MQTT 3.1.1** -- no application headers (only special `mqtt.*` headers)
* **HTTP** -- standard HTTP headers plus `http.query` and `http.path`
* **Kafka** -- Kafka record headers

{% include note.html content="Response diversion headers are processed by Ditto internally and are not sent as external protocol headers. They control internal routing behavior only." %}

## Further reading

* [Connections overview](basic-connections.html) -- source and target header mapping configuration
* [Placeholders](basic-placeholders.html) -- placeholder syntax and available placeholders
* [Response diversion](connectivity-response-diversion.html) -- redirect responses to other connections
