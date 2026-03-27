---
title: Protocol overview
keywords: channel, command, event, json, live, protocol, response, twin
tags: [protocol]
permalink: protocol-overview.html
---

The Ditto Protocol is a JSON-based text protocol for communicating with digital twins and the physical devices they represent.

{% include callout.html content="**TL;DR**: The Ditto Protocol defines a structured JSON message format for sending commands, receiving responses, and subscribing to events on digital twins and live devices." type="primary" %}

## Overview

Every interaction with Eclipse Ditto -- whether you create a thing, update a sensor value, or query device state -- uses the Ditto Protocol. It provides a uniform message format regardless of the transport layer (WebSocket, AMQP, MQTT, HTTP, or Kafka).

The protocol defines several **command** types that both the digital twin and the actual physical device can understand.

## How it works

### Communication pattern

The typical communication pattern when you interact with a digital twin or a device uses correlated protocol messages. Each message contains a `correlation-id` header that links related messages together.

The [Signals](basic-signals.html#communication-pattern) chapter describes the basic communication pattern of **commands**, **responses**, **events**, and **announcements**.

Here is the typical flow:

1. You send a **command** (e.g., modify a thing attribute) with a `correlation-id`.
2. Ditto processes the command and returns a **response** with the same `correlation-id`, indicating success or failure.
3. If the command modified state, Ditto emits an **event** that subscribers receive.

### Why a custom protocol?

Ditto needs a protocol that:

- Works across multiple transport layers (WebSocket, AMQP, MQTT, HTTP, Kafka)
- Supports both twin (server-side) and live (device-side) channels
- Enables fine-grained authorization on every message
- Carries structured metadata (topic, headers, path, value) alongside payloads

Standard REST or pub/sub patterns alone do not cover all of these requirements. The Ditto Protocol bridges that gap with a single, consistent JSON envelope format.

## Further reading

- [Protocol specification](protocol-specification.html) -- the full message format reference
- [Twin and live channels](protocol-twinlive.html) -- how Ditto routes messages to twins vs. devices
- [Protocol bindings](protocol-bindings.html) -- how the protocol maps to specific transports
- [Protocol examples](protocol-examples.html) -- concrete message examples
