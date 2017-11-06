---
title: Protocol Overview
keywords: protocol
tags: [protocol]
permalink: protocol-overview.html
---

## Motivation

The Ditto protocol covers two different communication channels to address different aspects 
of devices and their digital representation.

The first aspect handles the digital representation of an IoT asset, like a device.
This asset, or Thing, is managed with Ditto and its state and properties can be read and updated.
The channel to work with the digital representation is called **twin**.

This channel is available both at the Ditto HTTP API and the WebSocket interface which talks the Ditto Protocol.
The REST-like API is not scope of this specification but mentioned here to outline the context.

This protocol is specified and documented in the following chapter. 

The first part of the specification describes the **twin** aspect of the protocol.


## Semantics of commands, events, messages, and responses

### Twin

A **command** can be sent to Ditto to request a modification of a thing.
When Ditto handled the **command** successfully, i.e. the updated thing is persisted, it publishes an **event**.
An **event** is the unit that describes a modification of a thing, e.g. a property change, or an attribute change.

When sending a **command**, a **response** can be requested.
Ditto (asynchronously) replies to such **commands** as soon as the change has been applied.

### Live

**Commands**, **events** and **messages** are directly exchanged when using the _live_ channel.

**Commands** are defined to be used to change properties of e.g. connected device.
In case a **response** to a **command** is requested, the receiver must fulfill this request.
It is also always required that a _live_ thing **event** is published after a **command** has been applied (to the device) successfully.

A **message** carries a custom payload and can be answered by another, correlated **message**.
