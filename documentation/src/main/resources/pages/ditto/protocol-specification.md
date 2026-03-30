---
title: Protocol specification
keywords: action, channel, criterion, digital twin, envelope, payload, protocol, specification, twin
tags: [protocol]
permalink: protocol-specification.html
---

Every Ditto Protocol message consists of a JSON envelope and a JSON payload that together describe what action to perform on which entity.

{% include callout.html content="**TL;DR**: A Ditto Protocol message is a JSON object with `topic`, `headers`, `path`, and `value` fields. The topic identifies the entity and action; headers carry metadata; path and value specify what to change." type="primary" %}

## Overview

To comply with the Ditto Protocol, a message must contain:

* A **Ditto Protocol envelope** (JSON) -- metadata describing the message
* A **Ditto Protocol payload** (JSON) -- the application data

The transport layer (e.g., [WebSocket](httpapi-protocol-bindings-websocket.html)) handles serialization. See the relevant [protocol binding](protocol-bindings.html) for transport-specific details.

## Content type

Ditto Protocol messages use the [IANA-registered](https://www.iana.org/assignments/media-types/application/vnd.eclipse.ditto+json) content type:

```text
application/vnd.eclipse.ditto+json
```

## Ditto Protocol envelope {#dittoProtocolEnvelope}

The envelope describes the message content (the affected entity, message type, protocol version, etc.) and allows intermediary nodes to route the message without parsing the payload.

The envelope is a JSON object (`content-type=application/json`) conforming to this schema:

{% include docson.html schema="jsonschema/protocol-envelope.json" %}

## Ditto Protocol payload (JSON) {#dittoProtocolPayload}

The payload contains application data -- for example, an updated sensor value or a complete Thing in JSON form.

* [Things specification](protocol-specification-things.html)
* [Policies specification](protocol-specification-policies.html)
* [Connections specification](protocol-specification-connections.html)

## Ditto Protocol response {#dittoProtocolResponse}

When you send a **command**, you can request a **response** that indicates success or failure.

A successful response has this format:

{% include docson.html schema="jsonschema/protocol-response.json" %}

A failed response includes error details:

{% include docson.html schema="jsonschema/protocol-error_response.json" %}

## Topic

Every protocol message contains a [topic](protocol-specification-topic.html) that:

* Addresses a specific entity
* Defines the `channel` (*twin* vs. *live*)
* Specifies the intent of the message (create, retrieve, modify, delete, etc.)

## Headers

Protocol messages carry headers as a JSON object. The keys are header names and the values are header values.

Header names are **case-insensitive** and **case-preserving**:

- **Case-insensitive**: Setting `correlation-id` or `CORRELATION-ID` has the same effect. If you set two headers that differ only in capitalization, behavior is undefined.
- **Case-preserving**: The sender's capitalization is visible to the receiver, except for HTTP request/response headers which are inherently case-insensitive.

### Pre-defined headers

| Header Key | Description | Possible values |
|---|---|---|
| `content-type` | Describes the [value](#value) content type. | `String` |
| `correlation-id` | Correlates commands with their responses. | `String` |
| `ditto-originator` | The first authorization subject of the causing command. Set by Ditto. | `String` |
| `if-match` | Same semantics as [HTTP API conditional requests](httpapi-concepts.html#conditional-requests). | `String` |
| `if-none-match` | Same semantics as [HTTP API conditional requests](httpapi-concepts.html#conditional-requests). | `String` |
| `if-equal` | Same semantics as [HTTP API conditional requests](httpapi-concepts.html#conditional-requests). | `String` - currently: \['update','skip','skip-minimizing-merge'\] - default: `'update'` |
| `response-required` | Whether Ditto should send a response to this command. | `Boolean` - default: `true` |
| `requested-acks` | Which [acknowledgements](basic-acknowledgements.html) to request. | `JsonArray` of `String` - default: `["twin-persisted"]` |
| `ditto-weak-ack` | Marks [weak acknowledgements](basic-acknowledgements.html). | `Boolean` - default: `false` |
| `timeout` | How long Ditto waits (e.g., for acknowledgements). | `String` - e.g.: `42s`, `250ms`, `1m` - default: `60s` |
| `version` | Schema version for interpreting the payload. | `Number` - currently: \[2\] - default: `2` |
| `put-metadata` | Metadata to store with the thing. | `JsonArray` of `JsonObject`s containing [metadata](basic-metadata.html). |
| `condition` | RQL condition to evaluate before applying the request. | `String` containing a [condition](basic-conditional-requests.html). |
| `live-channel-condition` | RQL condition to evaluate before retrieving from the device. | `String` containing a [live channel condition](basic-conditional-requests.html#live-channel-condition). |
| `live-channel-timeout-strategy` | What to do when a [live](protocol-twinlive.html#live-channel) command times out. | `fail`: return 408 timeout. `use-twin`: fall back to persisted data. |
| `at-historical-revision` | Retrieve an entity at a historical revision using [history](basic-history.html). | `Number` - a long revision value. |
| `at-historical-timestamp` | Retrieve an entity at a historical timestamp using [history](basic-history.html). | `String` - ISO-8601 formatted timestamp. |
| `historical-headers` | Contains persisted historical headers from the `at-historical-*` request. | `JsonObject` of persisted historical headers. |

### Custom headers

Custom headers on [live channel](protocol-twinlive.html#live-channel) messages pass through verbatim. When naming custom headers, use a prefix specific to your application that does not conflict with Ditto or HTTP headers (e.g., `myapp-*`).

* Avoid [permanent HTTP headers](https://www.iana.org/assignments/message-headers/message-headers.xml).
* Ditto uses these headers internally -- if you set them, they are ignored and not delivered:
  ```text
  channel
  ditto-*
  raw-request-url
  read-subjects
  subject
  timeout-access
  ```

The interaction between `response-required`, `requested-acks`, and `timeout` is documented in [Acknowledgements](basic-acknowledgements.html#interaction-between-headers).

## Path

A JSON Pointer specifying where to apply the [value](#value) in the target entity. Use `/` when the value replaces the entire entity (e.g., a complete [Thing](basic-thing.html) JSON).

## Value

The JSON value to apply at the specified path.

## Status

Responses include an HTTP status code in this field.

## Extra

When you use [signal enrichment](basic-enrichment.html) to request `extraFields`, the protocol message includes an `extra` field containing a JSON object with the selected extra fields.

## Further reading

- [Protocol topic structure](protocol-specification-topic.html) -- topic path breakdown
- [Protocol errors](protocol-specification-errors.html) -- error response format
- [Things specification](protocol-specification-things.html) -- Thing-specific commands
- [Policies specification](protocol-specification-policies.html) -- Policy-specific commands
