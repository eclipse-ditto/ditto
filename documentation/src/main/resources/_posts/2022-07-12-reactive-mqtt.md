---
title: "Reactive MQTT Connectivity"
published: true
permalink: 2022-07-12-reactive-mqtt.html
layout: post
author: juergen_fickel
tags: [blog]
hide_sidebar: true
sidebar: false
toc: true
---

The upcoming Eclipse Ditto version 3.0.0 will ship with a major refactoring of the MQTT
connectivity module.  
In this post we want to highlight what's new and why this could be interesting for you.

## Backpressure via throttling
The most noteworthy innovation is that Ditto now consumes incoming publish messages
by using the
[reactive API flavour of the HiveMQ client](https://hivemq.github.io/hivemq-mqtt-client/docs/api-flavours/).  
This, together with throttling, effectively enables *backpressure* for inbound
MQTT publishes for protocol version 3.x and 5:

```config
connectivity.conf

…
throttling {
  enabled = true 
  enabled = ${?MQTT_CONSUMER_THROTTLING_ENABLED}

  # Interval at which the consumer is throttled. Must be > 0s.
  interval = 1s
  interval = ${?MQTT_CONSUMER_THROTTLING_INTERVAL}

  # The maximum number of messages the consumer is allowed to receive
  # within the configured throttling interval e.g. 100 msgs/s.
  # Must be > 0.
  limit = 100
  limit = ${?MQTT_CONSUMER_THROTTLING_LIMIT}
}
…
```
This kind of throttling applies to all in-flight messages – no matter what
their QoS is set to.  
Backpressure protects Ditto from congestion caused by a too high amount of
incoming publishes.  
Of course the broker has to deal with backpressure as well when throttling in
Ditto is enabled because the amount of unprocessed messages would pile up
at the broker.

## Flow Control with Receive Maximum (MQTT 5)
With MQTT 5 it is possible to specify a *Receive Maximum* when the client
connects to the broker.  
For QoS 1 or 2 this value determines how many unacknowledged incoming messages
the client accepts from the broker.  
Apart from throttling this is an additional approach how Ditto can be protected
from excessive load – at least for MQTT 5 connections.  
The (client) Receive Maximum can be set for Ditto either in configuration or
via environment variable:

```config
connectivity.conf

…
receive-maximum-client = 65535
receive-maximum-client = ${?CONNECTIVITY_MQTT_CLIENT_RECEIVE_MAXIMUM}
…
```

For more details, please have a look at
[HiveMQ's MQTT 5 Essentials, Part 12]( https://www.hivemq.com/blog/mqtt5-essentials-part12-flow-control/)
that covers just this topic.

## Unified implementation
Under the hood, almost anything related to MQTT changed.
With the previous implementation most of the logic was divided into a common
base implementation and a concrete implementation for protocol version 3.1.1
and 5.  
Obviously this worked.  
However, the algorithms were scattered over multiple classes which made it
difficult to understand what is going on (hello, maintainability &#x1F609;).

Now, the distinction between protocol version 3.x and 5 is made on the level
of data structures.  
Luckily the evolution of the MQTT protocol from version 3.1.1 to version 5
as well as the design of HiveMQ's client library made this very easy.  
For example, instead of dealing with a `Mqtt3Publish` and a `Mqtt5Publish` in
parallel all the time we introduced  a `GenericMqttPublish` which can be
converted from and to the specific types at the time when they come in
contact with the client.  
The rest of the time Ditto can work with the generic representation.

{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
