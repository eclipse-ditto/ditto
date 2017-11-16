---
title: Protocol twin/live channel
keywords: protocol, command, response, event, channel, twin, live
tags: [protocol]
permalink: protocol-twinlive.html
---

The Ditto Protocol furthermore covers two different communication channels to address different aspects of devices and 
their **Digital Twins**.

## Twin

The first _channel_, **twin**, handles the digital representation of a `Thing`.<br/>
This `Thing` is managed with Ditto and its state and properties can be read and updated.

{% include image.html file="pages/protocol/ditto-twin-channel.png" alt="Ditto twin channel" caption="Ditto twin channel pattern" max-width=800 %}

## Live

The second _channel_, **live**, routes a command/message towards an actual device.<br/>
The execution of what to do when a device (or a gateway which connects the device) does with a received command/message
is out of scope of Ditto and solution specific implementation.

{% include image.html file="pages/protocol/ditto-live-channel.png" alt="Ditto live channel" caption="Ditto live channel pattern" max-width=800 %}

What Ditto however does when routing **live** commands/messages is an [authorization check](basic-auth.html). So Ditto ensures
that only authorized parties are able to send a commands or messages.
