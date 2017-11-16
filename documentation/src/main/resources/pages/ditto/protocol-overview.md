---
title: Protocol overview
keywords: protocol, command, response, event, channel, twin, live
tags: [protocol]
permalink: protocol-overview.html
---

The Ditto Protocol defines a JSON based text protocol for communicating with **Digital Twins** and the actual physical
devices they mirror.

It defines several **commands** both the actual device and the **Digital Twin** are able to understand 

The Ditto Protocol furthermore covers two different communication [channels](protocol-specification-topic.html#channel) 
to address different aspects of devices and their digital representation.

The first _channel_, **twin**, handles the digital representation of an IoT asset.<br/>
This asset, or `Thing`, is managed with Ditto and its state and properties can be read and updated.

{% include image.html file="pages/protocol/ditto-twin-channel.png" alt="Ditto twin channel" caption="Ditto twin channel pattern" max-width=800 %}

The second _channel_, **live**, routes a command/message towards an actual device.<br/>
The execution of what to do when a device (or a gateway which connects the device) does with a received command/message
is out of scope of Ditto and solution specific implementation.<br/>

{% include image.html file="pages/protocol/ditto-live-channel.png" alt="Ditto live channel" caption="Ditto live channel pattern" max-width=800 %}

The communication pattern is defined by the Ditto protocol and shown in the next section.


## Communication pattern

The typical communication pattern when interacting with a **Digital Twin** or the actual device using the Ditto Protocol 
is composed of multiple correlated Protocol messages.<br/>
Therefore, each Protocol message contains a `correlation-id` which can be used to associate related Protocol messages.

The [Signals](basic-signals.html#communication-pattern) chapter already describes the basic communication pattern of
**commands**, **responses** and **events**.
