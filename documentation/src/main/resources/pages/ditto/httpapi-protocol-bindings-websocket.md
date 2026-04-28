---
title: WebSocket Binding
keywords: binding, protocol, websocket, http
tags: [protocol, http, rql]
permalink: httpapi-protocol-bindings-websocket.html
---

You use the WebSocket binding to send [Ditto Protocol](protocol-overview.html) messages over a persistent, duplex connection -- enabling real-time change notifications, bidirectional messaging, and live device interaction.

{% include callout.html content="**TL;DR**: Connect to `ws://localhost:8080/ws/2`, send Ditto Protocol JSON messages, and subscribe to events, messages, or live commands using plain-text control messages like `START-SEND-EVENTS`." type="primary" %}

## Overview

The WebSocket binding provides an alternative to the [HTTP API](httpapi-overview.html) for managing digital twins. Compared to HTTP, the WebSocket offers:

* **Lower overhead** -- a single persistent connection eliminates per-request HTTP handshake costs
* **Duplex communication** -- you receive [change notifications](basic-changenotifications.html), [messages](basic-messages.html), and [live commands/events](protocol-twinlive.html) on the same connection
* **Higher throughput** -- no HTTP header overhead means more commands per second

Every connected WebSocket session receives all events and messages it is authorized to see. There is no round-robin dispatching across sessions with the same authentication.

{% include warning.html content="This means that WebSockets are not meant to be used for scenarios where horizontal
    scaling should be applied.
    For those scenarios we suggest using the [Connectivity API](connectivity-overview.html)."
%}

## How it works

### Send commands and receive responses

Send a Ditto Protocol command as a JSON message and receive a corresponding response. Match responses to requests using the `correlation-id` header. See [Protocol examples](protocol-examples.html) for command/response patterns.

### Subscribe to change notifications

After subscribing, you receive [events](basic-changenotifications.html) whenever other clients modify Things, Features, or other entities you have read access to.

### Subscribe to messages

[Messages](basic-messages.html) can be sent via both the HTTP API and WebSocket, but you can only **receive and reply** to messages through the WebSocket.

### Subscribe to live commands and events

Use the WebSocket to receive [live commands and events](protocol-twinlive.html). The Ditto Protocol messages are the same as for the twin channel, but with `live` as the channel in the [topic](protocol-specification-topic.html).

## Setup

### Endpoint

```
ws://localhost:8080/ws/2
```

### Authentication

Authenticate using:
* **HTTP Basic Authentication** with a username and password managed by your reverse proxy (e.g., nginx)
* **JSON Web Token (JWT)** issued by an OpenID Connect provider

See [Authentication](basic-auth.html) for details.

## WebSocket protocol format

A Ditto Protocol message over WebSocket combines all protocol fields into a single JSON object:

```json
{
  "topic": "<the topic>",
  "headers": {
    "correlation-id": "<a correlation-id>",
    "a-header": "<header value>"
  },
  "path": "<the path>",
  "value": {}
}
```

See the [Protocol specification](protocol-specification.html) for details on [topic](protocol-specification.html#topic), [headers](protocol-specification.html#headers), [path](protocol-specification.html#path), [value](protocol-specification.html#value), and [status](protocol-specification.html#status).

## Subscription control messages

The WebSocket binding defines plain-text control messages (not JSON) for subscribing to different event streams.

### Control message reference

| Description | Subscribe | Unsubscribe | Acknowledgment |
|-------------|-----------|-------------|----------------|
| [Thing change notifications](basic-changenotifications.html) | `START-SEND-EVENTS` | `STOP-SEND-EVENTS` | `START-SEND-EVENTS:ACK` / `STOP-SEND-EVENTS:ACK` |
| [Messages](basic-messages.html) | `START-SEND-MESSAGES` | `STOP-SEND-MESSAGES` | `START-SEND-MESSAGES:ACK` / `STOP-SEND-MESSAGES:ACK` |
| [Live commands](protocol-twinlive.html) | `START-SEND-LIVE-COMMANDS` | `STOP-SEND-LIVE-COMMANDS` | `START-SEND-LIVE-COMMANDS:ACK` / `STOP-SEND-LIVE-COMMANDS:ACK` |
| [Live events](protocol-twinlive.html) | `START-SEND-LIVE-EVENTS` | `STOP-SEND-LIVE-EVENTS` | `START-SEND-LIVE-EVENTS:ACK` / `STOP-SEND-LIVE-EVENTS:ACK` |
| [Policy announcements](protocol-specification-policies-announcement.html) | `START-SEND-POLICY-ANNOUNCEMENTS` | `STOP-SEND-POLICY-ANNOUNCEMENTS` | `START-SEND-POLICY-ANNOUNCEMENTS:ACK` / `STOP-SEND-POLICY-ANNOUNCEMENTS:ACK` |
| Refresh JWT authentication | `JWT-TOKEN` | -- | -- |

### JWT token refresh

Ditto closes WebSocket connections when the JWT expires. To keep the connection open, send a new valid JWT using the `JWT-TOKEN` message. The `sub` claim of the new token must match the original token.

```
JWT-TOKEN?jwtToken=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Enrichment

Add extra fields to outgoing messages using the `extraFields` parameter on subscription messages:

```
START-SEND-EVENTS?extraFields=attributes/counter,features/ConnectionStatus
START-SEND-MESSAGES?extraFields=attributes
```

Enrichment is supported for all subscription types except Policy announcements. See [enrichment](basic-enrichment.html) for details.

| Subscription type | Enrichment supported |
|-------------------|---------------------|
| Thing change notifications | Yes |
| Messages | Yes |
| Live commands | Yes |
| Live events | Yes |
| Policy announcements | No |

### Filtering

You can filter which events you receive by namespace or RQL expression. Specify parameters like HTTP query parameters, with `?` for the first and `&` for subsequent ones. URL-encode filter values before use.

```
START-SEND-EVENTS?namespaces=org.eclipse.ditto&filter=gt(attributes/counter,42)
```

| Subscription type | Filter by namespace | Filter by RQL |
|-------------------|---------------------|---------------|
| Thing change notifications | Yes | Yes |
| Messages | Yes | No |
| Live commands | Yes | No |
| Live events | Yes | Yes |
| Policy announcements | Yes | No |

You can combine filtering with enrichment:

```
START-SEND-EVENTS?extraFields=attributes&filter=gt(attributes/counter,42)
```

## Further reading

* [Ditto Protocol overview](protocol-overview.html) -- message format specification
* [Protocol examples](protocol-examples.html) -- command and response patterns
* [Change notifications](basic-changenotifications.html) -- event filtering concepts
* [Connectivity API](connectivity-overview.html) -- for horizontally scaled event consumption
