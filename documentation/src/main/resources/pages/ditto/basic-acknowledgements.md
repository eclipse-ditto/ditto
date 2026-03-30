---
title: Acknowledgements / Quality of Service
keywords: acks, acknowledgement, acknowledgements, qos, at least once, delivery, guarantee
tags: [model]
permalink: basic-acknowledgements.html
---

Acknowledgements let you confirm that a signal was successfully received or processed, either by Ditto internally or by an external subscriber.

{% include callout.html content="**TL;DR**: Request acknowledgements (via the `requested-acks` header) to get confirmation that a command was persisted, forwarded, or processed by subscribers. Use them to achieve 'at least once' delivery guarantees (QoS 1)." type="primary" %}

## Overview

Acknowledgements act as (potentially multiple) responses to a single signal such as a twin command. Ditto collects all [requested acknowledgements](#requesting-acks) until the signal is successfully processed or a timeout expires.

## Usage scenarios

Acknowledgements help you accomplish tasks like:

* **Block until persisted**: Postpone an API response until a modification is persisted in Ditto.
* **Block until consumed**: Postpone a response until an external subscriber confirms it processed the resulting event.
* **At-least-once delivery**: Technically acknowledge a message from a broker (AMQP, MQTT) only after Ditto has processed it and optionally forwarded it to third parties.

## Supported signal types

You can request acknowledgements for:

* [Commands](basic-signals-command.html) that modify twin state (twin commands)
* Live commands
* Live messages

## Acknowledgement labels

A label links an acknowledgement request to its corresponding acknowledgement. Ditto uses some labels internally; you cannot use those labels for custom acknowledgements.

### Built-in acknowledgement labels

Ditto automatically fulfills these labels:

* **twin-persisted**: Fulfilled when a modifying command successfully updates the digital twin in Ditto's persistence. Ignored for live channel commands.
* **live-response**: Fulfilled when a subscriber of a live command or message sends a corresponding response. Ignored for twin channel commands.
* **search-persisted**: Fulfilled when a modifying command successfully updates the search index. Ignored for live channel commands.

### Custom acknowledgement labels

In addition to built-in labels, you can include custom acknowledgement requests in supported signals. A subscriber detects requested labels via the `"requested-acks"` header and [issues an acknowledgement](#issuing-acknowledgements) for the labels it handles.

## Acknowledgement structure

A single acknowledgement contains:

* **Label**: one of the requested labels
* **Headers**: must include `correlation-id` matching the original signal; may include additional headers
* **Status code**: HTTP-semantics status code indicating success or failure
* **Payload** (optional): JSON data

The [Ditto Protocol specification](protocol-specification-acks.html) describes the format in detail. See the [acknowledgement examples](protocol-examples.html#acknowledgements-acks) for sample messages.

## Requesting ACKs

You request acknowledgements via headers on supported signals.

[Events](basic-signals-event.html) emitted by Ditto include the custom acknowledgement requests in the `"requested-acks"` header.

### Requesting ACKs via HTTP

Set these HTTP headers:

* **requested-acks**: comma-separated list of [acknowledgement labels](#acknowledgement-labels). Example: `requested-acks: twin-persisted,some-connection-id:my-custom-ack`
* **timeout**: how long to block (default and max: `60s`). Examples: `timeout: 42s`, `timeout: 250ms`, `timeout: 1m`

Or use query parameters:

```
PUT /api/2/things/org.eclipse.ditto:thing-1?requested-acks=twin-persisted,my-custom-ack&timeout=42s
```

**Example response** -- all acknowledgements successful (overall status `200`):

```json
{
  "twin-persisted": {
    "status": 201,
    "payload": {
      "thingId": "org.eclipse.ditto:thing-1",
      "policyId": "org.eclipse.ditto:thing-1"
    },
    "headers": {
      "correlation-id": "db878735-4957-4fd9-92dc-6f09bb12a093",
      "version": 2,
      "etag": "\"rev:1\"",
      "location": "http://127.0.0.1:8080/api/2/things/org.eclipse.ditto:thing-1"
    }
  },
  "my-custom-ack": {
    "status": 200,
    "payload": { "outcome": "green" },
    "headers": {
      "version": 2,
      "correlation-id": "db878735-4957-4fd9-92dc-6f09bb12a093"
    }
  }
}
```

**Example response** -- one acknowledgement timed out (overall status `424`):

```json
{
  "twin-persisted": {
    "status": 201,
    "payload": {
      "thingId": "org.eclipse.ditto:thing-1",
      "policyId": "org.eclipse.ditto:thing-1"
    },
    "headers": { "correlation-id": "db878735-4957-4fd9-92dc-6f09bb12a093" }
  },
  "my-custom-ack": {
    "status": 408,
    "payload": {
      "status": 408,
      "error": "acknowledgement:request.timeout",
      "message": "The acknowledgement request reached the specified timeout of 42,000ms.",
      "description": "Try increasing the timeout and make sure that the requested acknowledgement is sent back in time."
    },
    "headers": { "correlation-id": "db878735-4957-4fd9-92dc-6f09bb12a093" }
  }
}
```

### Requesting ACKs via WebSocket

Include `"requested-acks"` (JSON array of strings) and `"timeout"` in the [Ditto Protocol headers](protocol-specification.html#headers). The response is an [aggregated acknowledgements](protocol-specification-acks.html#acknowledgements-aggregated) message.

### Requesting ACKs via connections

You can request acknowledgements in two ways:

1. **Per signal**: Set `"requested-acks"` in the [Ditto Protocol headers](protocol-specification.html#headers) of each consumed signal.
2. **Per source**: [Configure the connection source](basic-connections.html#source-acknowledgement-requests) to add acknowledgement requests to all consumed signals.

{% include note.html content="These requested acknowledgements will be appended after payload mapping is applied.<br/>
                              This means, that in case you decided to split your message into multiple messages, all of these messages will request the same acknowledgements.<br/>
                              If this is not what you want to achieve, have a look at [how to add acknowledgement requests during payload mapping](#requesting-acks-via-ditto-protocol-message-in-payload-mapping)." %}

#### Requesting ACKs via Ditto Protocol message in payload mapping

During inbound payload mapping, you can set `"requested-acks"` headers on individual messages rather than applying the source-level configuration uniformly.

## Issuing acknowledgements

Subscribers of twin events, live commands, or live messages issue acknowledgements by sending a [Ditto Protocol acknowledgement](protocol-specification-acks.html#acknowledgement) message with the same `"correlation-id"` as the received signal.

Each subscriber must declare the labels of acknowledgements it sends. Any label already declared by another subscriber causes an error.

### Status code semantics

| Status code | Description | Should retry | Reasoning |
|---|---|---|---|
| `2xx` | Successfully processed | No | Received and processed. |
| `4xx` | Request error | No | Bad request or auth failure -- retrying yields the same result. |
| `408` | Request timeout | Yes | Timeout -- retrying may succeed. |
| `424` | Mixed status codes | Yes | Contains a timeout or server error. |
| `5xx` | Server error | Yes | Temporary backend error. |

### Issuing ACKs via WebSocket

Send the [acknowledgement message](protocol-specification-acks.html#acknowledgement) over the WebSocket. Declare labels via the `declared-acks` query parameter:

```
GET /ws/2?declared-acks=some-connection-id:ack-label-1,my:ack-label-2
```

The WebSocket closes immediately if another subscriber already declared the same label.

{% include warning.html content="Therefore, it is not recommended relying on the websocket API for high
    availability scenarios."
%}

### Issuing ACKs via connections

You can issue acknowledgements in two ways:

1. **Via source**: Send a [Ditto Protocol acknowledgement](protocol-specification-acks.html#acknowledgement) message through a source of the same connection. Labels must be [declared](basic-connections.html#source-declared-acknowledgement-labels) in the `declaredAcks` field.
2. **Via target configuration**: [Configure the target](basic-connections.html#target-issued-acknowledgement-label) to automatically issue acknowledgements for all published signals.

Acknowledgement labels for connections must be prefixed by the connection ID or the `{%raw%}{{connection:id}}{%endraw%}` placeholder followed by a colon, e.g., `{%raw%}{{connection:id}}:my-custom-ack{%endraw%}`.

## Quality of Service

### QoS 0 -- at most once

By default, Ditto processes signals with "at most once" semantics. For many IoT use cases (e.g., telemetry data), this is sufficient -- if one sensor reading is lost, the next one updates the twin.

### QoS 1 -- at least once

For critical signals, use acknowledgements to achieve "at least once" delivery:

* **twin-persisted**: Ensures the command was persisted in the digital twin's database.
* **live-response**: Ensures a live command or message was processed by a subscriber.
* **Custom label**: Ensures an event was consumed by a specific integration.

If an acknowledgement fails or times out, the signal is negatively acknowledged on the transport layer (e.g., AMQP NACK, MQTT no-PUBACK), triggering redelivery by the broker.

## Weak Acknowledgements (WACKs)

When a subscriber has an RQL filter that excludes an event, or when a policy prevents delivery, Ditto cannot obtain a real acknowledgement. To prevent commands from failing due to missing acks in these cases, Ditto issues **weak acknowledgements** automatically.

Identify weak acknowledgements by checking the `ditto-weak-ack` header (set to `true`). Weak acknowledgements do not cause redelivery of messages consumed by a connection.

## Interaction between headers

Three headers control how Ditto responds: `response-required`, `requested-acks`, and `timeout`.

* **response-required**: `true` or `false`. Controls whether the caller gets a detailed reply. For live messages/commands, it also affects `requested-acks`:
  * `true`: adds `live-response` to `requested-acks` if not present (unless `requested-acks` was explicitly set to empty).
  * `false`: removes `live-response` from `requested-acks`.
* **requested-acks**: JSON array of acknowledgement labels. Determines response content and transport-layer settlement.
* **timeout**: Duration. How long Ditto waits for responses and acknowledgements.

It is a client error to set `timeout` to `0s` while `response-required` is `true` or `requested-acks` is nonempty.

### Default header values

Ditto sets defaults for each header based on the other headers:

| Header | Default value | Default (all unset) |
|---|---|---|
| response-required | `false` if timeout is zero or requested-acks is empty; `true` otherwise | `true` |
| requested-acks | empty if timeout is zero or response-required is false; channel default otherwise | `["twin-persisted"]` (TWIN) / `["live-response"]` (LIVE) |
| timeout | `60s` | `60s` |

### HTTP

| response-required | requested-acks | timeout | Outcome |
|---|---|---|---|
| false | empty | zero | 202 Accepted immediately |
| false | empty | non-zero | 202 Accepted immediately |
| false | non-empty | zero | 400 Bad Request |
| false | non-empty | non-zero | 202 Accepted after receiving acks |
| true | empty | zero | 400 Bad Request |
| true | empty | non-zero | Response |
| true | non-empty | zero | 400 Bad Request |
| true | non-empty | non-zero | Aggregated response and acks |

### WebSocket

| response-required | requested-acks | timeout | Outcome |
|---|---|---|---|
| false | empty | zero | No response |
| false | empty | non-zero | No response |
| false | non-empty | zero | Error |
| false | non-empty | non-zero | Error: cannot send acks without response |
| true | empty | zero | Error |
| true | empty | non-zero | Response |
| true | non-empty | zero | Error |
| true | non-empty | non-zero | Aggregated response and acks |

### Connectivity

| response-required | requested-acks | timeout | Outcome |
|---|---|---|---|
| false | empty | zero | Nothing published; settled immediately |
| false | empty | non-zero | Nothing published; settled immediately |
| false | non-empty | zero | Error; settled negatively |
| false | non-empty | non-zero | Nothing published; settled after acks |
| true | empty | zero | Error; settled negatively |
| true | empty | non-zero | Response published; settled immediately |
| true | non-empty | zero | Error; settled negatively |
| true | non-empty | non-zero | Aggregated response and acks published; settled after acks |

## Further reading

- [Acknowledgement protocol specification](protocol-specification-acks.html) -- Ditto Protocol format for acks
- [Protocol examples](protocol-examples.html#acknowledgements-acks) -- sample ack messages
- [Connections](basic-connections.html) -- configuring ack requests and issuance on connections
