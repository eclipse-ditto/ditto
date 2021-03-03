---
title: Protocol overview
keywords: channel, command, event, json, live, protocol, response, twin
tags: [protocol]
permalink: protocol-overview.html
---

The Ditto Protocol defines a JSON based text protocol for communicating with **digital twins** and the actual physical
devices they mirror.

It defines several **commands** both the actual device and the **digital twin** are able to understand.

The communication pattern is defined by the Ditto protocol and shown in the next section.


## Communication pattern

The typical communication pattern when interacting with a **digital twin** or the actual device using the Ditto Protocol 
is composed of multiple correlated Protocol messages.
Therefore, each Protocol message contains a `correlation-id` which can be used to associate related Protocol messages.

The [Signals](basic-signals.html#communication-pattern) chapter already describes the basic communication pattern of
**commands**, **responses**, **events** and **announcements**.
