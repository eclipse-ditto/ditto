---
title: Protocol examples
keywords: protocol, examples
tags: [protocol]
permalink: protocol-examples.html
---

This page explains the structure of Ditto Protocol examples and provides reference samples for commands, responses, events, and acknowledgements.

{% include callout.html content="**TL;DR**: Every protocol interaction follows the pattern: send a **command**, receive a **response** (success or error), and optionally observe an **event**. Use `correlation-id` to link related messages." type="primary" %}

## Overview

Each example in this section follows the same structure: a command that initiates an operation, a response indicating the outcome, and (for modifying commands) an event that subscribers receive.

## Command

Every interaction starts with a command message that tells Ditto what to do (e.g., create a Thing, modify an attribute). Commands include a `correlation-id` header to link the command with its response.

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/commands/modify",
  "headers": {
    "correlation-id": "a780b7b5-fdd2-4864-91fc-80df6bb0a636",
    "requested-acks": [ "twin-persisted", "custom-ack" ]
  },
  "path": "/"
}
```

## Response

Every command produces a response indicating success or failure. The response carries the same `correlation-id` as the command. See [Things error responses](protocol-examples-errorresponses.html) for error examples.

```json
{
  "headers": {
    "correlation-id": "a780b7b5-fdd2-4864-91fc-80df6bb0a636"
  },
  "path": "/",
  "value": {},
  "status": 204
}
```

## Event

When a command modifies state (e.g., Thing created, attribute modified), Ditto emits an event. Subscribers who registered for change notifications receive these events.

```json
{
  "topic": "org.eclipse.ditto/thing_name_3141/things/twin/events/modified",
  "headers": {
    "correlation-id": "a780b7b5-fdd2-4864-91fc-80df6bb0a636",
    "requested-acks": [ "custom-ack" ]
  },
  "path": "/",
  "value": {},
  "revision": 1
}
```

## Acknowledgements (ACKs)

When you specify `requested-acks` in your command headers, Ditto collects acknowledgements from the processing chain. Subscribers that handle requested labels send acknowledgements back with matching `correlation-id` headers.

**Successful ACK** (status `202`):

```json
{
  "topic": "org.eclipse.ditto/thing_name_3141/things/twin/acks/custom-ack",
  "headers": {
    "correlation-id": "a780b7b5-fdd2-4864-91fc-80df6bb0a636"
  },
  "path": "/",
  "status": 202
}
```

**Failed ACK / NACK** (status `400`):

```json
{
  "topic": "org.eclipse.ditto/thing_name_3141/things/twin/acks/custom-ack",
  "headers": {
    "correlation-id": "a780b7b5-fdd2-4864-91fc-80df6bb0a636"
  },
  "path": "/",
  "value": "You better try harder",
  "status": 400
}
```

**Timeout ACK** (status `408`):

```json
{
  "topic": "org.eclipse.ditto/thing_name_3141/things/twin/acks/custom-ack",
  "headers": {
    "correlation-id": "a780b7b5-fdd2-4864-91fc-80df6bb0a636"
  },
  "path": "/",
  "value": {
    "status": 408,
    "error": "acknowledgement:request.timeout",
    "message": "The acknowledgement request reached the specified timeout of 1,337ms.",
    "description": "Try increasing the timeout and make sure that the requested acknowledgement is sent back in time."
  },
  "status": 408
}
```

## Further reading

- [Things specification](protocol-specification-things.html) -- all Thing commands with example links
- [Policies specification](protocol-specification-policies.html) -- all Policy commands with example links
- [Acknowledgements](basic-acknowledgements.html) -- ack concepts and configuration
- [Protocol specification](protocol-specification.html) -- the full message format reference
